package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class CachingCustomSettings implements CustomSettings {

    private static final Logger logger = LoggerFactory.getLogger(CachingCustomSettings.class);

    private final Map<String, Object> objectCache;
    private final Map<String, String> objectToErrorCache;
    private final Map<String, CustomTracker> customTrackerCache;
    private final Map<String, String> customTrackerToErrorCache;
    private final CustomSettings delegate;

    public CachingCustomSettings(
            CustomSettings delegate,
            int ttl,
            int size
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.objectCache = createCache(ttl, size);
        this.objectToErrorCache = createCache(ttl, size);
        this.customTrackerCache = createCache(ttl, size);
        this.customTrackerToErrorCache = createCache(ttl, size);
    }

    static <T> Map<String, T> createCache(int ttl, int size) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
                .maximumSize(size)
                .<String, T>build()
                .asMap();
    }

    private static <T> Future<T> getFromCacheOrDelegate(
            Map<String, T> cache,
            Map<String, String> errorCache,
            String key,
            Timeout timeout,
            BiFunction<String, Timeout, Future<T>> retriever
    ) {

        final T cachedValue = cache.get(key);
        if (cachedValue != null) {
            logger.info("found in cache: " + key);
            return Future.succeededFuture(cachedValue);
        }
        final String preBidExceptionMessage = errorCache.get(key);
        if (preBidExceptionMessage != null) {
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }

        logger.info("not found in cache: " + key);
        return retriever.apply(key, timeout)
                .map(value -> {
                    logger.info("value retrieved: " + key);
                    cache.put(key, value);
                    return value;
                })
                .recover(throwable -> cacheAndReturnFailedFuture(throwable, key, errorCache));
    }

    private <T> Future<T> getFromObjectCacheOrDelegate(
            String key,
            Timeout timeout,
            Function<Timeout, Future<T>> retriever
    ) {
        return getFromObjectCacheOrDelegate(key, timeout, retriever, null);
    }

    private <T> Future<T> getFromObjectCacheOrDelegate(
            String key,
            Timeout timeout,
            Function<Timeout, Future<T>> retriever,
            Consumer<T> consumer
    ) {
        final Object cachedValue = objectCache.get(key);
        if (cachedValue != null) {
            logger.info("found in objectCache: " + key);
            return Future.succeededFuture((T) cachedValue);
        }
        logger.info("not found in objectCache: " + key);
        final String preBidExceptionMessage = objectToErrorCache.get(key);
        if (preBidExceptionMessage != null) {
            logger.info("found in objectToErrorCache: " + key);
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }
        logger.info("not found in objectToErrorCache: " + key);
        return retriever.apply(timeout)
                .map(value -> {
                    objectCache.put(key, value);
                    logger.info("value set in objectCache: " + key);
                    if (consumer != null) {
                        consumer.accept(value);
                    }
                    return value;
                })
                .recover(throwable -> cacheAndReturnFailedFuture(throwable, key, objectToErrorCache));
    }

    private static <T> Future<T> cacheAndReturnFailedFuture(
            Throwable throwable,
            String key,
            Map<String, String> cache
    ) {
        if (throwable instanceof PreBidException) {
            logger.warn("set objectToErrorCache: " + key);
            cache.put(key, throwable.getMessage());
        }

        return Future.failedFuture(throwable);
    }

    private void updateCustomTrackerCache(Map<String, CustomTracker> trackersMap) {
        customTrackerCache.putAll(trackersMap);
        logger.info("customTrackerCache updated.");
    }

    public Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout) {
        return getFromCacheOrDelegate(customTrackerCache, customTrackerToErrorCache,
                trackerId, timeout, delegate::getCustomTrackerById);
    }

    public Future<Map<String, CustomTracker>> getCustomTrackersMap(Timeout timeout) {
        return getFromObjectCacheOrDelegate(
                "customTrackers",
                timeout,
                delegate::getCustomTrackersMap,
                this::updateCustomTrackerCache
        );
    }

    @Override
    public Future<Collection<CustomTracker>> getCustomTrackers(Timeout timeout) {
        return getCustomTrackersMap(timeout).map(Map::values);
    }
}
