package com.improvedigital.prebid.server.services;

import com.improvedigital.prebid.server.utils.ReflectionUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * As per current implementation of {@link org.prebid.server.settings.service.HttpPeriodicRefreshService},
 * the service periodically calls external HTTP API for only stored request updates, but
 * <a href="https://github.com/prebid/prebid-server-java/issues/1665">cannot handle account updates</a>.
 *
 * <p>
 * So, until the account updates are officially supported by
 * {@link org.prebid.server.settings.service.HttpPeriodicRefreshService}
 * we needed to create this service that will call a similar HTTP API, and update account cache directly
 * via Reflection in {@link CachingApplicationSettings} bean.
 * </p>
 * <p>
 * To keep the implementation consistent, it uses the same endpoint configured for HttpPeriodicRefreshService
 * and expects following API spec to send periodic account updates:
 * </p>
 * <p>
 * GET {endpoint}?accounts=true
 * -- Returns all accounts, regardless of their update time
 *
 * GET {endpoint}?accounts=true&last-modified={timestamp}
 * -- Returns only the accounts that have been updated since the last refresh timestamp.
 * This timestamp will be sent in the rfc3339 format, using UTC and no timezone shift.
 * For more info, see: https://tools.ietf.org/html/rfc3339
 * </p>
 * <p>
 * The responses should be JSON like this:
 * </p>
 * <pre>
 * {
 *   "accounts": {
 *     "account1": { ... account data ... },
 *     "account2": { ... account data ... },
 *     "account3": { ... account data ... },
 *   }
 * }
 * </pre>
 */

public class AccountHttpPeriodicRefreshService implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AccountHttpPeriodicRefreshService.class);

    private final CachingApplicationSettings cachingApplicationSettings;
    private final String refreshUrl;
    private final long refreshPeriod;
    private final long timeout;
    private final long cacheTtlMs;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    private Map<String, Account> accountCache;
    private Instant lastUpdateTime;

    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public AccountHttpPeriodicRefreshService(
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
            loadAccountCache();
            this.refresh(0L);
            vertx.setPeriodic(refreshPeriod, this::refresh);
        }
    }

    private void refresh(long timerId) {
        final String lastModifiedParam = "accounts=true" + (
                lastUpdateTime == null ? "" : "&last-modified=" + lastUpdateTime
        );
        final String andOrParam = refreshUrl.contains("?") ? "&" : "?";
        final String refreshEndpoint = refreshUrl + andOrParam + lastModifiedParam;

        httpClient.get(refreshEndpoint, HttpUtil.headers(), timeout)
                .map(this::processResponse)
                .map(this::cacheAccounts)
                .recover(AccountHttpPeriodicRefreshService::failResponse);
    }

    private Map<String, Account> processResponse(HttpClientResponse response) {
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
        return parsedResponse.getAccounts();
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to http periodic refresh service", exception);
        return Future.failedFuture(exception);
    }

    private void loadAccountCache() {
        Map<String, Account> accountCache = null;
        try {
            accountCache = ReflectionUtils.getPrivateProperty(
                    "accountCache", cachingApplicationSettings,
                    CachingApplicationSettings.class
            );
            if (accountCache == null) {
                throw new PreBidException("accountCache is null in cachingApplicationSettings!");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        this.accountCache = accountCache;
    }

    private Void cacheAccounts(Map<String, Account> accountMap) {
        if (!accountMap.isEmpty()) {
            for (Map.Entry<String, Account> entry: accountMap.entrySet()) {
                if (entry.getValue() == null) {
                    this.accountCache.remove(entry.getKey());
                } else {
                    this.accountCache.put(entry.getKey(), entry.getValue());
                }
            }
            logger.info(
                    String.format(
                            "Successfully %s %d accounts with ids: %s.",
                            lastUpdateTime == null ? "cached" : "updated",
                            accountMap.size(),
                            accountMap.keySet()
                    )
            );
        } else {
            logger.debug("No accounts have been updated since last refresh.");
        }
        lastUpdateTime = Instant.now();
        return null;
    }
}
