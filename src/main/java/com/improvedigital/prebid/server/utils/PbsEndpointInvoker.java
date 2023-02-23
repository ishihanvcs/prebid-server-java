package com.improvedigital.prebid.server.utils;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.vertx.http.HttpClient;

import java.util.Objects;

public class PbsEndpointInvoker {

    private static final Logger logger = LoggerFactory.getLogger(PbsEndpointInvoker.class);

    final String baseUrl;
    private final HttpClient httpClient;

    private final JacksonMapper mapper;

    public PbsEndpointInvoker(
            HttpClient httpClient,
            JacksonMapper mapper,
            boolean ssl,
            int port
    ) {
        String scheme = "http" + (ssl ? "s" : "");
        String portSuffix = port == 80 ? "" : ":" + port;
        this.baseUrl = String.format("%s://localhost%s", scheme, portSuffix);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private String resolvePbsUrl(Endpoint pbsEndPoint) {
        return String.format("%s%s", baseUrl, pbsEndPoint.value());
    }

    public static String getLocalUrlForEndpoint(Endpoint pbsEndPoint, boolean ssl, int port) {
        final String scheme = "http" + (ssl ? "s" : "");
        final String portSuffix = port == 80 ? "" : ":" + port;
        final String baseUrl = String.format("%s://localhost%s", scheme, portSuffix);
        final String endPoint = pbsEndPoint == null ? "" : pbsEndPoint.value();
        return String.format("%s%s", baseUrl, endPoint);
    }

    public Future<CookieSyncResponse> invokeCookieSync(CookieSyncRequest request, MultiMap headers, Timeout timeout) {
        final Future<CookieSyncResponse> defaultReturn = Future.succeededFuture();
        try {
            if (Objects.nonNull(request.getGdpr())
                    && request.getGdpr() == 1
                    && StringUtils.isBlank(request.getGdprConsent())
            ) {
                logger.warn("Invalid cookie_sync request: gdpr_consent is required if gdpr is 1: " + request);
                return defaultReturn;
            }
            String body = mapper.encodeToString(request);
            final long timeRemaining = timeout.remaining();
            if (timeRemaining <= 0) {
                logger.error("No time remaining to invoke cookie_sync handler");
                return defaultReturn;
            }
            // logger.info("Invoking cookie_sync with request: \n" + body);
            return httpClient.post(resolvePbsUrl(Endpoint.cookie_sync), headers, body, timeRemaining)
                    .map(response -> {
                        final long timeElapsed = timeRemaining - timeout.remaining();
                        logger.debug("Time required to invoke cookie_sync endpoint: " + timeElapsed + " ms");
                        return ResponseUtils.processHttpResponse(
                                mapper, response, CookieSyncResponse.class
                        );
                    }).onFailure(t -> {
                        logger.debug("cookie_sync invocation failed for request: \n" + body);
                        logger.error("Error in http client", t);
                    });
        } catch (Exception ex) {
            logger.error("Exception occurred while invoking cookie_sync endpoint", ex);
        }
        return defaultReturn;
    }
}
