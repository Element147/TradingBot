package com.algotrader.bot.validation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class ValidationHttpClient {

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration requestTimeout;

    ValidationHttpClient(String baseUrl, Duration requestTimeout) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(requestTimeout)
            .build();
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.requestTimeout = requestTimeout;
    }

    ValidationHttpResponse get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
            .timeout(requestTimeout)
            .GET()
            .build();
        return send(request);
    }

    ValidationHttpResponse post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return send(request);
    }

    ValidationHttpResponse postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return send(request);
    }

    private ValidationHttpResponse send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new ValidationHttpResponse(response.statusCode(), response.body());
    }

    private URI resolve(String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return baseUri.resolve(normalizedPath);
    }

    record ValidationHttpResponse(int statusCode, String body) {
        boolean hasStatus(int... expectedStatuses) {
            for (int expectedStatus : expectedStatuses) {
                if (statusCode == expectedStatus) {
                    return true;
                }
            }
            return false;
        }
    }
}
