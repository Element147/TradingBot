package com.algotrader.bot.service.marketdata.provider;

import com.algotrader.bot.service.marketdata.MarketDataAssetType;
import com.algotrader.bot.service.marketdata.MarketDataHttpClient;
import com.algotrader.bot.service.marketdata.MarketDataHttpResponse;
import com.algotrader.bot.service.marketdata.MarketDataProviderCredentialService;
import com.algotrader.bot.service.marketdata.MarketDataProviderFetchRequest;
import com.algotrader.bot.service.marketdata.MarketDataRetryableException;
import com.algotrader.bot.service.marketdata.MarketDataTimeframe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KrakenMarketDataProviderTest {

    private MarketDataHttpClient httpClient;
    private KrakenMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        httpClient = Mockito.mock(MarketDataHttpClient.class);
        provider = new KrakenMarketDataProvider(
            httpClient,
            new ObjectMapper(),
            Mockito.mock(MarketDataProviderCredentialService.class)
        );
    }

    @Test
    void resolveChunkEnd_capsRequestsToKrakenRollingWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 30, 0, 0);
        MarketDataProviderFetchRequest request = new MarketDataProviderFetchRequest(
            MarketDataAssetType.CRYPTO,
            "BTC/USDT",
            MarketDataTimeframe.from("5m"),
            start,
            start.plusDays(3),
            false,
            false
        );

        assertThat(provider.resolveChunkEnd(request))
            .isEqualTo(start.plusMinutes(5L * 719L));
    }

    @Test
    void fetch_marksKrakenTooManyRequestsAsRetryable() {
        when(httpClient.get(any(), anyMap())).thenReturn(new MarketDataHttpResponse(
            200,
            "{\"error\":[\"EGeneral:Too many requests\"],\"result\":{}}",
            HttpHeaders.of(Map.of(), (left, right) -> true)
        ));

        LocalDateTime start = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).minusHours(1);
        MarketDataProviderFetchRequest request = new MarketDataProviderFetchRequest(
            MarketDataAssetType.CRYPTO,
            "BTC/USDT",
            MarketDataTimeframe.from("1m"),
            start,
            start.plusMinutes(30),
            false,
            false
        );

        assertThatThrownBy(() -> provider.fetch(request))
            .isInstanceOf(MarketDataRetryableException.class)
            .hasMessageContaining("Too many requests");
    }

    @Test
    void fetch_rejectsTooOldRequestsBeforeMakingHttpCalls() {
        LocalDateTime start = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).minusDays(3);
        MarketDataProviderFetchRequest request = new MarketDataProviderFetchRequest(
            MarketDataAssetType.CRYPTO,
            "BTC/USDT",
            MarketDataTimeframe.from("1m"),
            start,
            start.plusHours(1),
            false,
            false
        );

        assertThatThrownBy(() -> provider.fetch(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Kraken only exposes the most recent 720 candles");
        verifyNoInteractions(httpClient);
    }
}
