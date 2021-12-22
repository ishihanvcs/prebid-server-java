package com.azerion.prebid.services;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.vertx.Initializable;

public class AccountCachePeriodicInvalidator implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(AccountCachePeriodicInvalidator.class);

    private final CachingApplicationSettings cachingApplicationSettings;
    private final long accountInvalidationRate;
    private final long cacheTtlMs;
    private final Vertx vertx;

    public AccountCachePeriodicInvalidator(
            CachingApplicationSettings cachingApplicationSettings,
            long accountInvalidationRate,
            long cacheTtlMs,
            Vertx vertx
    ) {
        this.cachingApplicationSettings = cachingApplicationSettings;
        this.accountInvalidationRate = accountInvalidationRate;
        this.cacheTtlMs = cacheTtlMs;
        this.vertx = vertx;
    }

    @Override
    public void initialize() {
        if (cachingApplicationSettings != null
                && accountInvalidationRate > 0
                && accountInvalidationRate < cacheTtlMs
        ) {
            vertx.setPeriodic(accountInvalidationRate, this::invalidate);
        }
    }

    private void invalidate(long timerId) {
        cachingApplicationSettings.invalidateAllAccountCache();
        // logger.info(String.format("Account cache invalidated with timer id: %d", timerId));
    }
}
