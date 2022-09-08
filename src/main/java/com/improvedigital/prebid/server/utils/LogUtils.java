package com.improvedigital.prebid.server.utils;

import io.vertx.ext.web.impl.ConcurrentLRUCache;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class LogUtils {

    private LogUtils() { }

    private static final ConcurrentMap<String, AtomicInteger> COUNTERS = new ConcurrentLRUCache<>(1000);

    private static boolean shouldLog(LogMessage logMessage) {
        String key = logMessage.resolveLogCounterKey();
        if (StringUtils.isBlank(key)) {
            return false;
        }
        int frequency = ObjectUtils.defaultIfNull(logMessage.getFrequency(), 1);
        if (frequency <= 1) {
            return true;
        }
        if (!COUNTERS.containsKey(key)) {
            COUNTERS.put(key, new AtomicInteger(0));
        }
        final AtomicInteger counter = COUNTERS.get(key);
        return counter.getAndUpdate(i -> (i + 1) % frequency) == 0;
    }

    public static void log(LogMessage logMessage, Consumer<LogMessage> logMethod) {
        try {
            if (logMessage != null && shouldLog(logMessage)) {
                logMethod.accept(logMessage);
            }
        } catch (Exception ignored) { }
    }
}
