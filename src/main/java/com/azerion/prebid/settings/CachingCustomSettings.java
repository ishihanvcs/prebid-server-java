package com.azerion.prebid.settings;

import com.azerion.prebid.settings.model.CustomTracker;
import com.azerion.prebid.settings.model.Placement;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CachingCustomSettings implements CustomSettings {

    // private static final Logger logger = LoggerFactory.getLogger(CachingCustomSettings.class);
    private final Map<String, Placement> placementCache;
    private final Map<String, String> placementToErrorCache;

    private final Map<String, Map<String, CustomTracker>> customTrackerCache;
    private final Map<String, String> customTrackerToErrorCache;
    private final CustomSettings delegate;

    public CachingCustomSettings(
            CustomSettings delegate,
            int ttl,
            int size
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.placementCache = createCache(ttl, size);
        this.placementToErrorCache = createCache(ttl, size);

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
            return Future.succeededFuture(cachedValue);
        }
        final String preBidExceptionMessage = errorCache.get(key);
        if (preBidExceptionMessage != null) {
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }

        return retriever.apply(key, timeout)
                .map(value -> {
                    cache.put(key, value);
                    return value;
                })
                .recover(throwable -> cacheAndReturnFailedFuture(throwable, key, errorCache));
    }

    private static <T> Future<T> getFromCacheOrDelegate(
            Map<String, T> cache,
            Map<String, String> errorCache,
            String key,
            Timeout timeout,
            Function<Timeout, Future<T>> retriever
    ) {
        final T cachedValue = cache.get(key);
        if (cachedValue != null) {
            return Future.succeededFuture(cachedValue);
        }
        final String preBidExceptionMessage = errorCache.get(key);
        if (preBidExceptionMessage != null) {
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }

        return retriever.apply(timeout)
                .map(value -> {
                    cache.put(key, value);
                    return value;
                })
                .recover(throwable -> cacheAndReturnFailedFuture(throwable, key, errorCache));
    }

    private static <T> Future<T> cacheAndReturnFailedFuture(
            Throwable throwable,
            String key,
            Map<String, String> cache
    ) {

        if (throwable instanceof PreBidException) {
            cache.put(key, throwable.getMessage());
        }

        return Future.failedFuture(throwable);
    }

    @Override
    public Future<Placement> getPlacementById(String placementId, Timeout timeout) {
        return getFromCacheOrDelegate(
                placementCache,
                placementToErrorCache,
                placementId,
                timeout,
                delegate::getPlacementById
        );
    }

    @Override
    public Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout) {
        return getAllCustomTrackers(timeout).map(customTrackers -> customTrackers.get(trackerId));
    }

    @Override
    public Future<Map<String, CustomTracker>> getAllCustomTrackers(Timeout timeout) {
        return getFromCacheOrDelegate(
                customTrackerCache,
                customTrackerToErrorCache,
                "customTrackers",
                timeout,
                delegate::getAllCustomTrackers
        );
    }
}
