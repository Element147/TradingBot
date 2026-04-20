package com.algotrader.bot.service.marketdata;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MarketDataHttpClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    public MarketDataHttpResponse get(URI uri, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
            headers.forEach(requestBuilder::header);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new MarketDataHttpResponse(response.statusCode(), response.body(), response.headers());
        } catch (IOException exception) {
            throw new MarketDataRetryableException(
                "Temporary network error while contacting provider: " + exception.getMessage(),
                LocalDateTime.now().plusMinutes(5),
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataRetryableException(
                "Provider request interrupted. The downloader will retry automatically.",
                LocalDateTime.now().plusMinutes(1),
                exception
            );
        }
    }
}
