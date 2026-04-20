package com.algotrader.bot.service;

import com.algotrader.bot.controller.PaperOrderRequest;
import com.algotrader.bot.controller.PaperOrderResponse;
import com.algotrader.bot.controller.PaperTradingAlertResponse;
import com.algotrader.bot.controller.PaperTradingStateResponse;
import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.PaperOrder;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PaperOrderRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaperTradingService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    private static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.0003");
    private static final long STALE_OPEN_ORDER_MINUTES = 15L;
    private static final long STALE_POSITION_HOURS = 6L;

    private final PaperOrderRepository paperOrderRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final OperatorAuditService operatorAuditService;

    public PaperTradingService(PaperOrderRepository paperOrderRepository,
                               AccountRepository accountRepository,
                               PortfolioRepository portfolioRepository,
                               TradeRepository tradeRepository,
                               OperatorAuditService operatorAuditService) {
        this.paperOrderRepository = paperOrderRepository;
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional
    public PaperOrderResponse placeOrder(PaperOrderRequest request) {
        Account account = resolveAccount();

        PaperOrder order = new PaperOrder(
            account.getId(),
            request.symbol(),
            PaperOrder.Side.valueOf(request.side().toUpperCase()),
            request.quantity(),
            request.price()
        );

        PaperOrder saved = paperOrderRepository.save(order);

        if (Boolean.TRUE.equals(request.executeNow())) {
            saved = fillOrderInternal(saved.getId());
        }

        operatorAuditService.recordSuccess(
            "PAPER_ORDER_PLACED",
            "paper",
            "PAPER_ORDER",
            String.valueOf(saved.getId()),
            "symbol=" + saved.getSymbol() + ", side=" + saved.getSide().name() + ", status=" + saved.getStatus().name()
        );

        return mapToResponse(saved);
    }

    @Transactional
    public PaperOrderResponse fillOrder(Long orderId) {
        PaperOrder filled = fillOrderInternal(orderId);
        operatorAuditService.recordSuccess(
            "PAPER_ORDER_FILLED",
            "paper",
            "PAPER_ORDER",
            String.valueOf(filled.getId()),
            "symbol=" + filled.getSymbol() + ", side=" + filled.getSide().name()
        );
        return mapToResponse(filled);
    }

    private PaperOrder fillOrderInternal(Long orderId) {
        PaperOrder order = paperOrderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Paper order not found: " + orderId));

        if (!PaperOrder.Status.NEW.equals(order.getStatus())) {
            throw new IllegalArgumentException("Only NEW orders can be filled");
        }

        Account account = accountRepository.findById(order.getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("Account not found for order: " + orderId));

        BigDecimal notional = order.getPrice().multiply(order.getQuantity());
        BigDecimal fees = notional.multiply(FEE_RATE).setScale(8, RoundingMode.HALF_UP);
        BigDecimal slippage = notional.multiply(SLIPPAGE_RATE).setScale(8, RoundingMode.HALF_UP);
        BigDecimal fillPrice = resolveFillPrice(order);

        switch (order.getSide()) {
            case BUY -> handleBuy(account, order, fillPrice, fees, slippage);
            case SELL -> handleSell(account, order, fillPrice, fees, slippage);
            case SHORT -> handleShort(account, order, fillPrice, fees, slippage);
            case COVER -> handleCover(account, order, fillPrice, fees, slippage);
        }

        order.setStatus(PaperOrder.Status.FILLED);
        order.setFillPrice(fillPrice);
        order.setFees(fees);
        order.setSlippage(slippage);
        order.setFilledAt(LocalDateTime.now());

        return paperOrderRepository.save(order);
    }

    private BigDecimal resolveFillPrice(PaperOrder order) {
        return switch (order.getSide()) {
            case BUY, COVER -> order.getPrice()
                .multiply(BigDecimal.ONE.add(SLIPPAGE_RATE))
                .setScale(8, RoundingMode.HALF_UP);
            case SELL, SHORT -> order.getPrice()
                .multiply(BigDecimal.ONE.subtract(SLIPPAGE_RATE))
                .setScale(8, RoundingMode.HALF_UP);
        };
    }

    private void handleBuy(Account account,
                           PaperOrder order,
                           BigDecimal fillPrice,
                           BigDecimal fees,
                           BigDecimal slippage) {
        Portfolio existingPosition = portfolioRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
            .orElse(null);
        assertCompatiblePosition(existingPosition, PositionSide.LONG, "Use COVER before opening a long position");

        BigDecimal totalCost = fillPrice.multiply(order.getQuantity()).add(fees);
        if (account.getCurrentBalance().compareTo(totalCost) < 0) {
            throw new IllegalArgumentException("Insufficient paper balance");
        }

        account.setCurrentBalance(account.getCurrentBalance().subtract(totalCost));
        upsertPosition(account.getId(), order.getSymbol(), order.getQuantity(), fillPrice, PositionSide.LONG);
        accountRepository.save(account);
        createTradeRecord(account.getId(), order, fillPrice, fees, slippage, BigDecimal.ZERO, PositionSide.LONG, Trade.SignalType.BUY);
    }

    private void handleSell(Account account,
                            PaperOrder order,
                            BigDecimal fillPrice,
                            BigDecimal fees,
                            BigDecimal slippage) {
        Portfolio position = loadPositionForSide(account.getId(), order.getSymbol(), PositionSide.LONG, "No open long position to sell");
        ensureQuantityAvailable(position, order.getQuantity(), "Sell quantity exceeds open long position size");

        BigDecimal proceeds = fillPrice.multiply(order.getQuantity()).subtract(fees);
        account.setCurrentBalance(account.getCurrentBalance().add(proceeds));

        BigDecimal pnl = fillPrice.subtract(position.getAverageEntryPrice())
            .multiply(order.getQuantity())
            .subtract(fees)
            .subtract(slippage)
            .setScale(8, RoundingMode.HALF_UP);
        account.setTotalPnl(account.getTotalPnl().add(pnl));

        reducePosition(position, order.getQuantity(), fillPrice);
        accountRepository.save(account);
        createTradeRecord(account.getId(), order, fillPrice, fees, slippage, pnl, PositionSide.LONG, Trade.SignalType.SELL);
    }

    private void handleShort(Account account,
                             PaperOrder order,
                             BigDecimal fillPrice,
                             BigDecimal fees,
                             BigDecimal slippage) {
        Portfolio existingPosition = portfolioRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
            .orElse(null);
        assertCompatiblePosition(existingPosition, PositionSide.SHORT, "Use SELL before opening a short position");

        BigDecimal collateral = fillPrice.multiply(order.getQuantity()).add(fees);
        if (account.getCurrentBalance().compareTo(collateral) < 0) {
            throw new IllegalArgumentException("Insufficient paper balance to reserve short collateral");
        }

        account.setCurrentBalance(account.getCurrentBalance().subtract(collateral));
        upsertPosition(account.getId(), order.getSymbol(), order.getQuantity(), fillPrice, PositionSide.SHORT);
        accountRepository.save(account);
        createTradeRecord(account.getId(), order, fillPrice, fees, slippage, BigDecimal.ZERO, PositionSide.SHORT, Trade.SignalType.SHORT);
    }

    private void handleCover(Account account,
                             PaperOrder order,
                             BigDecimal fillPrice,
                             BigDecimal fees,
                             BigDecimal slippage) {
        Portfolio position = loadPositionForSide(account.getId(), order.getSymbol(), PositionSide.SHORT, "No open short position to cover");
        ensureQuantityAvailable(position, order.getQuantity(), "Cover quantity exceeds open short position size");

        BigDecimal releasedCollateral = position.getAverageEntryPrice().multiply(order.getQuantity());
        BigDecimal pnl = position.getAverageEntryPrice().subtract(fillPrice)
            .multiply(order.getQuantity())
            .subtract(fees)
            .subtract(slippage)
            .setScale(8, RoundingMode.HALF_UP);
        account.setCurrentBalance(account.getCurrentBalance().add(releasedCollateral).add(pnl));
        account.setTotalPnl(account.getTotalPnl().add(pnl));

        reducePosition(position, order.getQuantity(), fillPrice);
        accountRepository.save(account);
        createTradeRecord(account.getId(), order, fillPrice, fees, slippage, pnl, PositionSide.SHORT, Trade.SignalType.COVER);
    }

    @Transactional
    public PaperOrderResponse cancelOrder(Long orderId) {
        PaperOrder order = paperOrderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Paper order not found: " + orderId));

        if (!PaperOrder.Status.NEW.equals(order.getStatus())) {
            throw new IllegalArgumentException("Only NEW orders can be cancelled");
        }

        order.setStatus(PaperOrder.Status.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        PaperOrder saved = paperOrderRepository.save(order);
        operatorAuditService.recordSuccess(
            "PAPER_ORDER_CANCELLED",
            "paper",
            "PAPER_ORDER",
            String.valueOf(saved.getId()),
            "symbol=" + saved.getSymbol() + ", side=" + saved.getSide().name()
        );
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaperOrderResponse> listOrders() {
        Account account = resolveAccount();
        return paperOrderRepository.findByAccountIdOrderByCreatedAtDesc(account.getId()).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PaperTradingStateResponse getState() {
        Account account = resolveAccount();
        List<PaperOrder> orders = paperOrderRepository.findByAccountIdOrderByCreatedAtDesc(account.getId());
        List<Portfolio> positions = portfolioRepository.findByAccountId(account.getId());

        Long total = (long) orders.size();
        Long open = paperOrderRepository.countByAccountIdAndStatus(account.getId(), PaperOrder.Status.NEW);
        Long filled = paperOrderRepository.countByAccountIdAndStatus(account.getId(), PaperOrder.Status.FILLED);
        Long cancelled = paperOrderRepository.countByAccountIdAndStatus(account.getId(), PaperOrder.Status.CANCELLED);
        LocalDateTime lastOrder = orders.isEmpty() ? null : orders.get(0).getCreatedAt();
        LocalDateTime lastPositionUpdate = positions.stream()
            .map(Portfolio::getLastUpdated)
            .max(LocalDateTime::compareTo)
            .orElse(null);
        long staleOpenOrderCount = orders.stream()
            .filter(order -> PaperOrder.Status.NEW.equals(order.getStatus()))
            .filter(order -> order.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(STALE_OPEN_ORDER_MINUTES)))
            .count();
        long stalePositionCount = positions.stream()
            .filter(position -> position.getLastUpdated().isBefore(LocalDateTime.now().minusHours(STALE_POSITION_HOURS)))
            .count();
        RecoveryState recoveryState = resolveRecoveryState(staleOpenOrderCount, stalePositionCount, total, positions.size());
        List<PaperTradingAlertResponse> alerts = buildAlerts(staleOpenOrderCount, stalePositionCount, total, positions.size());

        return new PaperTradingStateResponse(
            true,
            account.getCurrentBalance(),
            positions.size(),
            total,
            open,
            filled,
            cancelled,
            lastOrder,
            lastPositionUpdate,
            staleOpenOrderCount,
            stalePositionCount,
            recoveryState.status(),
            recoveryState.message(),
            buildIncidentSummary(staleOpenOrderCount, stalePositionCount, total, positions.size()),
            alerts
        );
    }

    private void upsertPosition(Long accountId,
                                String symbol,
                                BigDecimal quantity,
                                BigDecimal fillPrice,
                                PositionSide positionSide) {
        Portfolio position = portfolioRepository.findByAccountIdAndSymbol(accountId, symbol)
            .orElseGet(() -> new Portfolio(accountId, symbol, BigDecimal.ZERO, fillPrice, fillPrice, positionSide));

        if (position.getPositionSize().compareTo(BigDecimal.ZERO) > 0
            && position.getPositionSide() != null
            && position.getPositionSide() != positionSide) {
            throw new IllegalArgumentException("Opposing position already exists for " + symbol);
        }

        BigDecimal newSize = position.getPositionSize().add(quantity);
        BigDecimal newAverage = BigDecimal.ZERO;
        if (newSize.compareTo(BigDecimal.ZERO) > 0) {
            newAverage = position.getAverageEntryPrice().multiply(position.getPositionSize())
                .add(fillPrice.multiply(quantity))
                .divide(newSize, 8, RoundingMode.HALF_UP);
        }

        position.setPositionSide(positionSide);
        position.setPositionSize(newSize);
        position.setAverageEntryPrice(newAverage);
        position.setCurrentPrice(fillPrice);
        portfolioRepository.save(position);
    }

    private void createTradeRecord(Long accountId,
                                   PaperOrder order,
                                   BigDecimal fillPrice,
                                   BigDecimal fees,
                                   BigDecimal slippage,
                                   BigDecimal pnl,
                                   PositionSide positionSide,
                                   Trade.SignalType signalType) {
        BigDecimal stopLoss = positionSide.isLong()
            ? fillPrice.multiply(new BigDecimal("0.98"))
            : fillPrice.multiply(new BigDecimal("1.02"));
        BigDecimal takeProfit = positionSide.isLong()
            ? fillPrice.multiply(new BigDecimal("1.02"))
            : fillPrice.multiply(new BigDecimal("0.98"));
        Trade trade = new Trade(
            accountId,
            order.getSymbol(),
            signalType,
            positionSide,
            LocalDateTime.now(),
            fillPrice,
            order.getQuantity(),
            fillPrice.multiply(order.getQuantity()).multiply(new BigDecimal("0.02")),
            stopLoss,
            takeProfit,
            fees,
            slippage
        );
        trade.setExitTime(LocalDateTime.now());
        trade.setExitPrice(fillPrice);
        trade.setPnl(pnl);
        tradeRepository.save(trade);
    }

    private Portfolio loadPositionForSide(Long accountId,
                                          String symbol,
                                          PositionSide expectedSide,
                                          String missingMessage) {
        Portfolio position = portfolioRepository.findByAccountIdAndSymbol(accountId, symbol)
            .orElseThrow(() -> new IllegalArgumentException(missingMessage));
        if (position.getPositionSide() != expectedSide) {
            throw new IllegalArgumentException(missingMessage);
        }
        return position;
    }

    private void ensureQuantityAvailable(Portfolio position, BigDecimal quantity, String message) {
        if (position.getPositionSize().compareTo(quantity) < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void reducePosition(Portfolio position, BigDecimal quantity, BigDecimal fillPrice) {
        BigDecimal remaining = position.getPositionSize().subtract(quantity);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            portfolioRepository.delete(position);
            return;
        }

        position.setPositionSize(remaining);
        position.setCurrentPrice(fillPrice);
        portfolioRepository.save(position);
    }

    private void assertCompatiblePosition(Portfolio existingPosition,
                                          PositionSide expectedSide,
                                          String message) {
        if (existingPosition != null
            && existingPosition.getPositionSize().compareTo(BigDecimal.ZERO) > 0
            && existingPosition.getPositionSide() != expectedSide) {
            throw new IllegalArgumentException(message);
        }
    }

    private PaperOrderResponse mapToResponse(PaperOrder order) {
        return new PaperOrderResponse(
            order.getId(),
            order.getSymbol(),
            order.getSide().name(),
            order.getStatus().name(),
            order.getQuantity(),
            order.getPrice(),
            order.getFillPrice(),
            order.getFees(),
            order.getSlippage(),
            order.getCreatedAt()
        );
    }

    private Account resolveAccount() {
        return accountRepository.findTopByOrderByCreatedAtDesc()
            .orElseGet(() -> accountRepository.save(new Account(
                new BigDecimal("10000.00"),
                new BigDecimal("0.02"),
                new BigDecimal("0.25")
            )));
    }

    private RecoveryState resolveRecoveryState(long staleOpenOrderCount,
                                               long stalePositionCount,
                                               long totalOrders,
                                               int positionCount) {
        if (staleOpenOrderCount > 0 || stalePositionCount > 0) {
            return new RecoveryState(
                "ATTENTION",
                "Review paper-trading recovery state: " + staleOpenOrderCount + " stale open orders and "
                    + stalePositionCount + " stale positions detected."
            );
        }
        if (totalOrders == 0 && positionCount == 0) {
            return new RecoveryState("IDLE", "Paper trading is idle with no orders or open positions.");
        }
        return new RecoveryState("HEALTHY", "No stale paper-trading state detected after the latest activity.");
    }

    private String buildIncidentSummary(long staleOpenOrderCount,
                                        long stalePositionCount,
                                        long totalOrders,
                                        int positionCount) {
        if (staleOpenOrderCount > 0 || stalePositionCount > 0) {
            return "Operator attention required: stale paper-trading state is blocking a clean restart picture.";
        }
        if (totalOrders == 0 && positionCount == 0) {
            return "Paper trading is idle. No incident follow-up is currently required.";
        }
        return "Paper trading is active with no current incident signals.";
    }

    private List<PaperTradingAlertResponse> buildAlerts(long staleOpenOrderCount,
                                                        long stalePositionCount,
                                                        long totalOrders,
                                                        int positionCount) {
        List<PaperTradingAlertResponse> alerts = new ArrayList<>();
        if (staleOpenOrderCount > 0) {
            alerts.add(new PaperTradingAlertResponse(
                "WARNING",
                "STALE_OPEN_ORDERS",
                staleOpenOrderCount + " open paper order(s) are older than " + STALE_OPEN_ORDER_MINUTES + " minutes.",
                "Review each stale order and either fill or cancel it before relying on restart recovery."
            ));
        }
        if (stalePositionCount > 0) {
            alerts.add(new PaperTradingAlertResponse(
                "WARNING",
                "STALE_POSITIONS",
                stalePositionCount + " paper position(s) have not updated in over " + STALE_POSITION_HOURS + " hours.",
                "Reconcile stale positions, then verify that strategy recovery telemetry returns to HEALTHY."
            ));
        }
        if (alerts.isEmpty()) {
            if (totalOrders == 0 && positionCount == 0) {
                alerts.add(new PaperTradingAlertResponse(
                    "INFO",
                    "PAPER_IDLE",
                    "Paper trading is idle with no pending recovery actions.",
                    "No action required until the next paper-trading exercise."
                ));
            } else {
                alerts.add(new PaperTradingAlertResponse(
                    "INFO",
                    "PAPER_HEALTHY",
                    "Paper-trading recovery telemetry is currently healthy.",
                    "Continue monitoring for stale orders or stale positions during the next restart cycle."
                ));
            }
        }
        return List.copyOf(alerts);
    }

    private record RecoveryState(String status, String message) {
    }
}
