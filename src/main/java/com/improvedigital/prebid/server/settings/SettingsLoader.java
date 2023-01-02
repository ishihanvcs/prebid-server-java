package com.improvedigital.prebid.server.settings;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.exception.SettingsLoaderException;
import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class SettingsLoader {

    private static final Logger logger = LoggerFactory.getLogger(SettingsLoader.class);

    private final ApplicationSettings applicationSettings;
    private final CustomSettings customSettings;
    private final TimeoutFactory timeoutFactory;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final long defaultLoadingTimeoutMs;
    private final RequestUtils requestUtils;

    public SettingsLoader(
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            RequestUtils requestUtils,
            Metrics metrics,
            TimeoutFactory timeoutFactory,
            long defaultLoadingTimeoutMs
    ) {
        this.applicationSettings = applicationSettings;
        this.customSettings = customSettings;
        this.metrics = metrics;
        this.requestUtils = requestUtils;
        this.mapper = requestUtils.getJsonUtils().getMapper();
        this.timeoutFactory = timeoutFactory;
        this.defaultLoadingTimeoutMs = defaultLoadingTimeoutMs;
    }

    private <T> Future<T> getSettingFuture(
            String id, String settingType,
            BiFunction<String, Timeout, Future<T>> loaderFn,
            Timeout timeout
    ) {
        if (StringUtils.isBlank(id)) {
            return Future.failedFuture(
                    new SettingsLoaderException(
                            String.format("%s id cannot be blank", settingType)
                    ));
        }
        return loaderFn.apply(id, createTimeoutIfNull(timeout));
    }

    public Timeout createSettingsLoadingTimeout() {
        return createSettingsLoadingTimeout(0);
    }

    public Timeout createSettingsLoadingTimeout(long startTime) {
        return startTime <= 0
                ? timeoutFactory.create(defaultLoadingTimeoutMs)
                : timeoutFactory.create(startTime, defaultLoadingTimeoutMs);
    }

    private Timeout createTimeoutIfNull(Timeout timeout) {
        if (timeout == null) {
            timeout = createSettingsLoadingTimeout();
        }
        return timeout;
    }

    public Future<Account> getAccountFuture(String accountId) {
        return getAccountFuture(accountId, null);
    }

    public Future<Account> getAccountFuture(String accountId, Timeout timeout) {
        timeout = createTimeoutIfNull(timeout);
        return getSettingFuture(accountId, "account", applicationSettings::getAccountById, timeout);
    }

    public Future<Account> getAccountFuture(BidRequest bidRequest, Timeout timeout) {
        final String accountId = requestUtils.getAccountId(bidRequest);
        return getAccountFuture(accountId, timeout);
    }

    public Future<Collection<CustomTracker>> getCustomTrackersFuture(Timeout timeout) {
        return customSettings.getCustomTrackers(createTimeoutIfNull(timeout));
    }

    public Future<Collection<CustomTracker>> getCustomTrackersFuture() {
        return getCustomTrackersFuture(null);
    }

    public Future<Imp> getStoredImp(String impId, Timeout timeout) {
        return getStoredDataResultFuture(null, Collections.emptySet(), Set.of(impId), timeout)
                .map(storedDataResult -> {
                    try {
                        final String storedData = storedDataResult.getStoredIdToImp().getOrDefault(impId, null);
                        if (StringUtils.isBlank(storedData) || storedData.equals("null")) {
                            throw new InvalidRequestException(
                                    String.format("Invalid impId '%s' provided.", impId)
                            );
                        }
                        return mapper.mapper().readValue(storedData, Imp.class);
                    } catch (InvalidRequestException ire) {
                        throw ire;
                    } catch (Exception e) {
                        throw new InvalidRequestException(e.getMessage(), e);
                    }
                });
    }

    public Future<Map<String, Imp>> getStoredImps(Set<String> impIds, Timeout timeout) {
        return this.getStoredImps(impIds, timeout, false);
    }

    public Future<Map<String, Imp>> getStoredImps(Set<String> impIds, Timeout timeout, boolean suppressErrors) {
        final Map<String, Imp> emptyResult = new HashMap<>();
        if (impIds.isEmpty()) {
            return Future.succeededFuture(emptyResult);
        }
        return getStoredDataResultFuture(null, Collections.emptySet(), impIds, timeout)
                .map(storedDataResult -> {
                    final Map<String, String> storedDataToImp = storedDataResult.getStoredIdToImp();
                    final Map<String, Imp> imps = new HashedMap<>();
                    storedDataToImp.forEach((impId, storedData) -> {
                        boolean error = true;
                        if (StringUtils.isNotBlank(storedData) && !storedData.equals("null")) {
                            try {
                                final Imp imp = mapper.mapper().readValue(storedData, Imp.class);
                                imps.putIfAbsent(impId, imp);
                                error = false;
                            } catch (Exception ignored) { }
                        }

                        if (error) {
                            final String errorMessage = String.format("Invalid impId '%s' provided.", impId);
                            if (!suppressErrors) {
                                throw new InvalidRequestException(errorMessage);
                            } else {
                                logger.warn(errorMessage);
                            }
                        }
                    });
                    return imps;
                }).recover(throwable -> {
                    if (suppressErrors) {
                        logger.warn("Ignored error while fetching imps: " + throwable.getMessage());
                        return Future.succeededFuture(emptyResult);
                    }
                    return Future.failedFuture(throwable);
                });
    }

    public Future<Map<String, Imp>> getStoredImpsSafely(Set<String> impIds, Timeout timeout) {
        return this.getStoredImps(impIds, timeout, true);
    }

    public Future<StoredDataResult> getStoredDataResultFuture(
            String accountId, Set<String> requestIds, final Set<String> impIds, Timeout timeout
    ) {
        return applicationSettings.getStoredData(accountId, requestIds, impIds, timeout)
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed: %s", exception.getMessage()))))
                .compose(result -> !result.getErrors().isEmpty()
                        ? Future.failedFuture(new InvalidRequestException(result.getErrors()))
                        : Future.succeededFuture(result))
                .compose(storedDataResult -> {
                    requestIds.forEach(
                            id -> metrics.updateStoredRequestMetric(
                                    storedDataResult.getStoredIdToRequest().containsKey(id)
                            )
                    );
                    impIds.forEach(
                            id -> metrics.updateStoredImpsMetric(
                                    storedDataResult.getStoredIdToImp().containsKey(id)
                            )
                    );
                    return Future.succeededFuture(storedDataResult);
                });
    }
}

