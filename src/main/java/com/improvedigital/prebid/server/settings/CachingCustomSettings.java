package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.improvedigital.prebid.server.settings.model.CustomTrackerSetting;
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

    private final Map<String, Object> objectCache;
    private final Map<String, String> objectToErrorCache;
    private final CustomSettings delegate;

    public CachingCustomSettings(
            CustomSettings delegate,
            int ttl,
            int size
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.objectCache = createCache(ttl, size);
        this.objectToErrorCache = createCache(ttl, size);
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

    private <T> Future<T> getFromObjectCacheOrDelegate(
            String key,
            Timeout timeout,
            Function<Timeout, Future<T>> retriever
    ) {
        final T cachedValue = (T) objectCache.get(key);
        if (cachedValue != null) {
            return Future.succeededFuture(cachedValue);
        }
        final String preBidExceptionMessage = objectToErrorCache.get(key);
        if (preBidExceptionMessage != null) {
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }

        return retriever.apply(timeout)
                .map(value -> {
                    objectCache.put(key, value);
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
            cache.put(key, throwable.getMessage());
        }

        return Future.failedFuture(throwable);
    }

    @Override
    public Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout) {
        return getCustomTrackerSetting(timeout)
                .map(customTrackerSetting -> !Objects.isNull(customTrackerSetting)
                    ? customTrackerSetting.getTrackersMap().get(trackerId)
                    : null
                );
    }

    @Override
    public Future<CustomTrackerSetting> getCustomTrackerSetting(Timeout timeout) {
        return getFromObjectCacheOrDelegate(
                "customTrackers",
                timeout,
                delegate::getCustomTrackerSetting
        );
    }
}
