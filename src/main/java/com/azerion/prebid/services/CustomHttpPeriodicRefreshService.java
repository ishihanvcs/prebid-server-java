package com.azerion.prebid.services;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CustomHttpPeriodicRefreshService implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(CustomHttpPeriodicRefreshService.class);

    private final CachingApplicationSettings cachingApplicationSettings;
    private final String refreshUrl;
    private final long refreshPeriod;
    private final long timeout;
    private final long cacheTtlMs;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    private Instant lastUpdateTime;

    public CustomHttpPeriodicRefreshService(
            CachingApplicationSettings cachingApplicationSettings,
            String refreshUrl,
            long refreshPeriod,
            long timeout,
            long cacheTtlMs,
            Vertx vertx,
            HttpClient httpClient,
            JacksonMapper mapper
    ) {
        this.cachingApplicationSettings = cachingApplicationSettings;
        this.refreshUrl = HttpUtil.validateUrl(Objects.requireNonNull(refreshUrl));
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.cacheTtlMs = cacheTtlMs;
        this.vertx = vertx;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void initialize() {
        if (cachingApplicationSettings != null
                && refreshPeriod > 0
                && refreshPeriod < cacheTtlMs
        ) {
            lastUpdateTime = Instant.now();
            vertx.setPeriodic(refreshPeriod, this::refresh);
        }
    }

    private void refresh(long timerId) {
        final String lastModifiedParam = "accounts=true&last-modified=" + lastUpdateTime;
        final String andOrParam = refreshUrl.contains("?") ? "&" : "?";
        final String refreshEndpoint = refreshUrl + andOrParam + lastModifiedParam;

        httpClient.get(refreshEndpoint, HttpUtil.headers(), timeout)
                .map(this::processResponse)
                .map(this::invalidateAccounts)
                .recover(CustomHttpPeriodicRefreshService::failResponse);
    }

    private Set<Account> processResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException(String.format("Error fetching last modified accounts via http: "
                    + "unexpected response status %d", statusCode));
        }
        final String body = response.getBody();

        final HttpAccountsResponse parsedResponse;
        try {
            parsedResponse = mapper.decodeValue(body, HttpAccountsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(
                    String.format("Error parsing last modified accounts response: %s", e.getMessage())
            );
        }
        final Map<String, Account> accounts = parsedResponse.getAccounts();

        return MapUtils.isNotEmpty(accounts) ? new HashSet<>(accounts.values()) : Collections.emptySet();
    }

    private Void invalidateAccounts(Set<Account> accounts) {
        accounts.forEach(account -> cachingApplicationSettings.invalidateAccountCache(account.getId()));
        lastUpdateTime = Instant.now();
        return null;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to http periodic refresh service", exception);
        return Future.failedFuture(exception);
    }
}
