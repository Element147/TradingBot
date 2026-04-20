package com.algotrader.bot.controller;

import com.algotrader.bot.entity.MarketDataProviderCredential;
import com.algotrader.bot.repository.MarketDataProviderCredentialRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "algotrading.market-data.credentials.master-key=test-market-data-master-key")
class MarketDataProviderCredentialControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MarketDataProviderCredentialRepository marketDataProviderCredentialRepository;

    private String adminToken;
    private String traderToken;

    @BeforeEach
    void setUp() {
        marketDataProviderCredentialRepository.deleteAll();
        adminToken = jwtTokenProvider.generateToken("admin", "ADMIN");
        traderToken = jwtTokenProvider.generateToken("trader", "TRADER");
    }

    @Test
    void listProviderCredentials_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/market-data/provider-credentials")
                .header("Authorization", "Bearer " + traderToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void saveProviderCredential_persistsEncryptedValueAndShowsDatabaseSource() throws Exception {
        mockMvc.perform(post("/api/market-data/provider-credentials/twelvedata")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "apiKey": "td-live-demo-key",
                      "note": "Free-tier key for stock and crypto imports"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerId").value("twelvedata"))
            .andExpect(jsonPath("$.credentialSource").value("DATABASE"))
            .andExpect(jsonPath("$.note").value("Free-tier key for stock and crypto imports"));

        MarketDataProviderCredential stored = marketDataProviderCredentialRepository.findByProviderId("twelvedata")
            .orElseThrow();
        assertThat(stored.getEncryptedApiKey()).isNotEqualTo("td-live-demo-key");
        assertThat(stored.getEncryptionIv()).isNotBlank();
        assertThat(stored.getNote()).isEqualTo("Free-tier key for stock and crypto imports");

        List<MarketDataProviderCredentialResponse> credentials = listCredentialSettings();
        MarketDataProviderCredentialResponse twelveData = credentials.stream()
            .filter(item -> "twelvedata".equals(item.providerId()))
            .findFirst()
            .orElseThrow();

        assertThat(twelveData.hasStoredCredential()).isTrue();
        assertThat(twelveData.effectiveCredentialConfigured()).isTrue();
        assertThat(twelveData.credentialSource()).isEqualTo("DATABASE");
    }

    @Test
    void providersEndpoint_reportsDatabaseCredentialAsConfigured() throws Exception {
        mockMvc.perform(post("/api/market-data/provider-credentials/twelvedata")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "apiKey": "td-live-demo-key",
                      "note": "Rotation A"
                    }
                    """))
            .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/market-data/providers")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

        List<MarketDataProviderResponse> providers = objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<>() {
            }
        );

        MarketDataProviderResponse twelveData = providers.stream()
            .filter(item -> "twelvedata".equals(item.id()))
            .findFirst()
            .orElseThrow();

        assertThat(twelveData.apiKeyConfigured()).isTrue();
        assertThat(twelveData.apiKeyConfiguredSource()).isEqualTo("DATABASE");
    }

    @Test
    void deleteProviderCredential_removesStoredSecret() throws Exception {
        mockMvc.perform(post("/api/market-data/provider-credentials/finnhub")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "apiKey": "fh-demo-key",
                      "note": "Used for candle validation"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/market-data/provider-credentials/finnhub")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        assertThat(marketDataProviderCredentialRepository.findByProviderId("finnhub")).isEmpty();
    }

    private List<MarketDataProviderCredentialResponse> listCredentialSettings() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/market-data/provider-credentials")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<>() {
            }
        );
    }
}
