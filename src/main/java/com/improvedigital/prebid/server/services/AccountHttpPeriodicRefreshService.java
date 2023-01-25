package com.improvedigital.prebid.server.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.improvedigital.prebid.server.settings.proto.response.HttpAccountRefreshResponse;
import com.improvedigital.prebid.server.utils.ReflectionUtils;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Instant;
import java.util.HashMap;
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
 *     "account4": { "deleted": true },
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
    private final Account defaultAccount;
    private final JsonMerger jsonMerger;
    private final PriceFloorsConfigResolver priceFloorsConfigResolver;

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
            String defaultAccountConfig,
            PriceFloorsConfigResolver priceFloorsConfigResolver,
            JsonMerger jsonMerger,
            JacksonMapper mapper
    ) {
        this.cachingApplicationSettings = cachingApplicationSettings;
        this.refreshUrl = HttpUtil.validateUrl(Objects.requireNonNull(refreshUrl));
        this.refreshPeriod = refreshPeriod;
        this.timeout = timeout;
        this.cacheTtlMs = cacheTtlMs;
        this.vertx = vertx;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.defaultAccount = parseAccount(defaultAccountConfig, mapper);
        this.priceFloorsConfigResolver = Objects.requireNonNull(priceFloorsConfigResolver);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
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
        if (statusCode != 200) {
            throw new PreBidException("HTTP status code " + statusCode);
        }

        try {
            final HttpAccountRefreshResponse refreshResponse = mapper.decodeValue(
                    response.getBody(), HttpAccountRefreshResponse.class
            );
            final Map<String, Account> result = new HashMap<>();

            if (refreshResponse.getAccounts() == null) { // if there is no "accounts" key in response or the key value is null
                return result;
            }

            for (Map.Entry<String, ObjectNode> entry : refreshResponse.getAccounts().entrySet()) {
                final ObjectNode objectNode = entry.getValue();
                final String accountId = entry.getKey();
                Account parsedAccount = null;
                if (objectNode != null && !(objectNode.has("deleted") && objectNode.get("deleted").asBoolean())) {
                    parsedAccount = mapper.mapper().convertValue(objectNode, Account.class);
                }
                result.put(accountId, parsedAccount);
            }
            return result;
        } catch (DecodeException e) {
            throw new PreBidException(
                    String.format("Error parsing periodic refresh accounts response: %s", e.getMessage())
            );
        }
    }

    private static Account parseAccount(String accountConfig, JacksonMapper mapper) {
        try {
            final Account account = StringUtils.isNotBlank(accountConfig)
                    ? mapper.decodeValue(accountConfig, Account.class)
                    : null;

            return isNotEmpty(account) ? account : null;
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    private static boolean isNotEmpty(Account account) {
        return account != null && !account.equals(Account.builder().build());
    }

    private Account mergeDefaultAccount(Account account) {
        if (defaultAccount == null) {
            return account;
        }
        return jsonMerger.merge(account, defaultAccount, Account.class);
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to account http periodic refresh service", exception);
        return Future.failedFuture(exception);
    }

    private void loadAccountCache() {
        Map<String, Account> accountCache;
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
        if (MapUtils.isNotEmpty(accountMap)) {
            for (Map.Entry<String, Account> entry: accountMap.entrySet()) {
                if (entry.getValue() == null) {
                    this.accountCache.remove(entry.getKey());
                    logger.debug(
                            String.format(
                                    "Account with id=%s is deleted and hence removed from cache.",
                                    entry.getKey()
                            )
                    );
                } else {
                    final Account enrichedAccount = priceFloorsConfigResolver
                            .updateFloorsConfig(entry.getValue())
                            .map(this::mergeDefaultAccount)
                            .result();
                    this.accountCache.put(entry.getKey(), enrichedAccount);
                    logger.debug(
                            String.format(
                                    "Account with id=%s has been saved in cache successfully.",
                                    entry.getKey()
                            )
                    );
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
