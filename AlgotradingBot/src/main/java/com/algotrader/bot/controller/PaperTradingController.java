package com.algotrader.bot.controller;

import com.algotrader.bot.service.PaperTradingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {

    private final PaperTradingService paperTradingService;

    public PaperTradingController(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    @PostMapping("/orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaperOrderResponse> placeOrder(@Valid @RequestBody PaperOrderRequest request) {
        return ResponseEntity.ok(paperTradingService.placeOrder(request));
    }

    @PostMapping("/orders/{orderId}/fill")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaperOrderResponse> fillOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paperTradingService.fillOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaperOrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paperTradingService.cancelOrder(orderId));
    }

    @GetMapping("/orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PaperOrderResponse>> orders() {
        return ResponseEntity.ok(paperTradingService.listOrders());
    }

    @GetMapping("/state")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaperTradingStateResponse> state() {
        return ResponseEntity.ok(paperTradingService.getState());
    }
}
