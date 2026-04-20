package com.algotrader.bot.controller;

import com.algotrader.bot.entity.User;
import com.algotrader.bot.repository.ExchangeConnectionProfileRepository;
import com.algotrader.bot.repository.UserRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExchangeSystemControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExchangeConnectionProfileRepository exchangeConnectionProfileRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String authToken;

    @BeforeEach
    void setUp() {
        exchangeConnectionProfileRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("testuser");
        user.setPasswordHash("hashed-password");
        user.setEmail("test@example.com");
        user.setRole(User.Role.TRADER);
        user.setEnabled(true);
        userRepository.save(user);

        authToken = jwtTokenProvider.generateToken("testuser", User.Role.TRADER.name());
    }

    @Test
    void getExchangeConnectionStatus_returnsStatusPayload() throws Exception {
        mockMvc.perform(get("/api/exchange/connection-status")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").isBoolean())
            .andExpect(jsonPath("$.exchange").value("binance"))
            .andExpect(jsonPath("$.lastSync").exists())
            .andExpect(jsonPath("$.rateLimitUsage").exists());
    }

    @Test
    void testConnection_withoutCredentials_returnsDisconnectedState() throws Exception {
        mockMvc.perform(post("/api/system/test-connection")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false))
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getSystemInfo_returnsRuntimeStatus() throws Exception {
        mockMvc.perform(get("/api/system/info")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicationVersion").exists())
            .andExpect(jsonPath("$.lastDeploymentDate").exists())
            .andExpect(jsonPath("$.databaseStatus").value(anyOf(is("UP"), is("DOWN"), is("unavailable"))));
    }

    @Test
    void triggerBackup_createsDatabaseBackupArtifact() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/system/backup")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").exists())
            .andExpect(jsonPath("$.size").exists())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String pathValue = responseBody.replaceAll(".*\"path\":\"([^\"]+)\".*", "$1").replace("\\\\", "\\");
        Path backupPath = Path.of(pathValue);

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(backupPath));
        String backupContent = Files.readString(backupPath);
        org.junit.jupiter.api.Assertions.assertFalse(backupContent.contains("Local metadata backup"));
        org.junit.jupiter.api.Assertions.assertTrue(
            backupContent.contains("CREATE")
                || backupContent.contains("INSERT")
                || backupContent.contains("SET DB_CLOSE_DELAY"),
            "Expected SQL backup content instead of placeholder metadata."
        );
    }

    @Test
    void listAuditEvents_returnsRecentEntries() throws Exception {
        mockMvc.perform(post("/api/system/backup")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/audit-events")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.visibleEventCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.events").isArray())
            .andExpect(jsonPath("$.events[0].action").exists())
            .andExpect(jsonPath("$.events[0].actor").exists());
    }

    @Test
    void listAuditEvents_supportsOutcomeFiltering() throws Exception {
        mockMvc.perform(post("/api/system/test-connection")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/audit-events")
                .header("Authorization", "Bearer " + authToken)
                .param("outcome", "FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events[0].outcome").value("FAILED"));
    }

    @Test
    void savedExchangeConnections_roundTripForCurrentUser() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/exchange/connections")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Binance Live",
                      "exchange": "binance",
                      "apiKey": "key123",
                      "apiSecret": "secret123",
                      "testnet": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Binance Live"))
            .andExpect(jsonPath("$.exchange").value("binance"))
            .andExpect(jsonPath("$.apiKey").value("key123"))
            .andExpect(jsonPath("$.apiSecret").value("secret123"))
            .andExpect(jsonPath("$.active").value(false))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String connectionId = created.get("id").asText();

        mockMvc.perform(post("/api/exchange/connections/{connectionId}/activate", connectionId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(connectionId))
            .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/exchange/connections")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeConnectionId").value(connectionId))
            .andExpect(jsonPath("$.connections[0].id").value(connectionId))
            .andExpect(jsonPath("$.connections[0].name").value("Binance Live"))
            .andExpect(jsonPath("$.connections[0].exchange").value("binance"))
            .andExpect(jsonPath("$.connections[0].apiKey").value("key123"))
            .andExpect(jsonPath("$.connections[0].apiSecret").value("secret123"))
            .andExpect(jsonPath("$.connections[0].testnet").value(false))
            .andExpect(jsonPath("$.connections[0].active").value(true));
    }

    @Test
    void savedExchangeConnections_areScopedToAuthenticatedUser() throws Exception {
        User otherUser = new User();
        otherUser.setUsername("otheruser");
        otherUser.setPasswordHash("hashed-password");
        otherUser.setEmail("other@example.com");
        otherUser.setRole(User.Role.TRADER);
        otherUser.setEnabled(true);
        userRepository.save(otherUser);

        String otherToken = jwtTokenProvider.generateToken("otheruser", User.Role.TRADER.name());

        mockMvc.perform(post("/api/exchange/connections")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Primary Trader Connection",
                      "exchange": "binance",
                      "apiKey": "user-key",
                      "apiSecret": "user-secret",
                      "testnet": true
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/exchange/connections")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Other Trader Connection",
                      "exchange": "kraken",
                      "apiKey": "other-key",
                      "apiSecret": "other-secret",
                      "testnet": true
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/exchange/connections")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connections.length()").value(1))
            .andExpect(jsonPath("$.connections[0].name").value("Primary Trader Connection"));
    }
}
