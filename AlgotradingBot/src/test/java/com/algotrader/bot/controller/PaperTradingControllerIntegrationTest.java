package com.algotrader.bot.controller;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PaperOrderRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaperTradingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private PaperOrderRepository paperOrderRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        paperOrderRepository.deleteAll();
        portfolioRepository.deleteAll();

        Account account = new Account(
            new BigDecimal("100000"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );
        accountRepository.save(account);
    }

    @Test
    void placeOrder_executesAndFillsByDefault() throws Exception {
        PaperOrderRequest request = new PaperOrderRequest(
            "BTC/USDT",
            "BUY",
            new BigDecimal("1"),
            new BigDecimal("100"),
            true
        );

        mockMvc.perform(post("/api/paper/orders")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FILLED"))
            .andExpect(jsonPath("$.fillPrice").exists());
    }

    @Test
    void placeOrder_canCreatePendingAndCancel() throws Exception {
        PaperOrderRequest request = new PaperOrderRequest(
            "BTC/USDT",
            "BUY",
            new BigDecimal("1"),
            new BigDecimal("100"),
            false
        );

        String response = mockMvc.perform(post("/api/paper/orders")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NEW"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long orderId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/paper/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void getPaperState_returnsSummary() throws Exception {
        mockMvc.perform(get("/api/paper/state")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paperMode").value(true))
            .andExpect(jsonPath("$.cashBalance").exists())
            .andExpect(jsonPath("$.totalOrders").exists())
            .andExpect(jsonPath("$.staleOpenOrderCount").value(0))
            .andExpect(jsonPath("$.stalePositionCount").value(0))
            .andExpect(jsonPath("$.recoveryStatus").value("IDLE"))
            .andExpect(jsonPath("$.incidentSummary").value("Paper trading is idle. No incident follow-up is currently required."))
            .andExpect(jsonPath("$.alerts[0].code").value("PAPER_IDLE"));
    }

    @Test
    void listOrders_returnsArray() throws Exception {
        mockMvc.perform(get("/api/paper/orders")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))));
    }

    @Test
    void shortOrder_andCover_updatesPaperState() throws Exception {
        PaperOrderRequest shortRequest = new PaperOrderRequest(
            "BTC/USDT",
            "SHORT",
            new BigDecimal("1"),
            new BigDecimal("100"),
            true
        );

        mockMvc.perform(post("/api/paper/orders")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FILLED"))
            .andExpect(jsonPath("$.side").value("SHORT"));

        mockMvc.perform(get("/api/paper/state")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionCount").value(1));

        PaperOrderRequest coverRequest = new PaperOrderRequest(
            "BTC/USDT",
            "COVER",
            new BigDecimal("1"),
            new BigDecimal("90"),
            true
        );

        mockMvc.perform(post("/api/paper/orders")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(coverRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FILLED"))
            .andExpect(jsonPath("$.side").value("COVER"));

        mockMvc.perform(get("/api/paper/state")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionCount").value(0))
            .andExpect(jsonPath("$.filledOrders").value(2));
    }
}
