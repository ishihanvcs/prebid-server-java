package com.improvedigital.prebid.server.utils;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.model.Endpoint;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Objects;

public class LocalHttpClient {

    final String baseUrl;
    private final HttpClient httpClient;

    public LocalHttpClient(
            HttpClient httpClient,
            boolean ssl,
            int port
    ) {
        String scheme = "http" + (ssl ? "s" : "");
        String portSuffix = port == 80 ? "" : ":" + port;
        this.baseUrl = String.format("%s://localhost%s", scheme, portSuffix);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private String resolvePbsUrl(Endpoint pbsEndPoint) {
        return String.format("%s%s", baseUrl, pbsEndPoint.value());
    }

    public static String getLocalPbsUrl(boolean ssl, int port, Endpoint pbsEndPoint) {
        final String scheme = "http" + (ssl ? "s" : "");
        final String portSuffix = port == 80 ? "" : ":" + port;
        final String baseUrl = String.format("%s://localhost%s", scheme, portSuffix);
        final String endPoint = pbsEndPoint == null ? "" : pbsEndPoint.value();
        return String.format("%s%s", baseUrl, endPoint);
    }

    public Future<HttpClientResponse> request(
            HttpMethod method, Endpoint pbsEndPoint, MultiMap headers,
            String body, long timeoutMs, long maxResponseSize
    ) {
        return httpClient.request(method, resolvePbsUrl(pbsEndPoint), headers, body, timeoutMs, maxResponseSize);
    }

    public Future<HttpClientResponse> request(
            HttpMethod method, Endpoint pbsEndPoint, MultiMap headers, byte[] body,
            long timeoutMs, long maxResponseSize
    ) {
        return httpClient.request(method, resolvePbsUrl(pbsEndPoint), headers, body, timeoutMs, maxResponseSize);
    }

    public Future<HttpClientResponse> request(
            HttpMethod method, Endpoint pbsEndPoint, MultiMap headers,
            String body, long timeoutMs
    ) {
        return httpClient.request(method, resolvePbsUrl(pbsEndPoint), headers, body, timeoutMs);
    }

    public Future<HttpClientResponse> request(
            HttpMethod method, Endpoint pbsEndPoint, MultiMap headers,
            byte[] body, long timeoutMs
    ) {
        return httpClient.request(method, resolvePbsUrl(pbsEndPoint), headers, body, timeoutMs);
    }

    public Future<HttpClientResponse> get(Endpoint pbsEndPoint, long timeoutMs, long maxResponseSize) {
        return httpClient.get(resolvePbsUrl(pbsEndPoint), timeoutMs, maxResponseSize);
    }

    public Future<HttpClientResponse> get(Endpoint pbsEndPoint, MultiMap headers, long timeoutMs) {
        return httpClient.get(resolvePbsUrl(pbsEndPoint), headers, timeoutMs);
    }

    public Future<HttpClientResponse> get(Endpoint pbsEndPoint, long timeoutMs) {
        return httpClient.get(resolvePbsUrl(pbsEndPoint), timeoutMs);
    }

    public Future<HttpClientResponse> post(Endpoint pbsEndPoint, MultiMap headers, String body, long timeoutMs) {
        return httpClient.post(resolvePbsUrl(pbsEndPoint), headers, body, timeoutMs);
    }

    public Future<HttpClientResponse> post(Endpoint pbsEndPoint, String body, long timeoutMs) {
        return httpClient.post(resolvePbsUrl(pbsEndPoint), body, timeoutMs);
    }
}
