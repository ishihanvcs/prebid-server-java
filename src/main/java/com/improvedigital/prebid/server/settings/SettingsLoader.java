package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.exception.SettingsLoaderException;
import com.improvedigital.prebid.server.settings.model.CustomTrackerSetting;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

public class SettingsLoader {

    private static final long DEFAULT_SETTINGS_LOADING_TIMEOUT = 500L;

    private final ApplicationSettings applicationSettings;
    private final CustomSettings customSettings;
    private final TimeoutFactory timeoutFactory;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final long defaultLoadingTimeoutMs;

    public SettingsLoader(
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            Metrics metrics,
            JacksonMapper mapper,
            TimeoutFactory timeoutFactory,
            long defaultLoadingTimeoutMs
    ) {
        this.applicationSettings = applicationSettings;
        this.customSettings = customSettings;
        this.metrics = metrics;
        this.mapper = mapper;
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

    public Future<CustomTrackerSetting> getCustomTrackerSettingFuture(Timeout timeout) {
        return customSettings.getCustomTrackerSetting(createTimeoutIfNull(timeout));
    }

    public Future<CustomTrackerSetting> getCustomTrackerSettingFuture() {
        return getCustomTrackerSettingFuture(null);
    }

    public Future<Imp> getStoredImp(String impId, Timeout timeout) {
        return getStoredDataResultFuture(null, Collections.emptySet(), Set.of(impId), timeout)
                .compose(storedDataResult -> {
                    try {
                        final String storedData = storedDataResult.getStoredIdToImp().getOrDefault(impId, null);
                        if (StringUtils.isBlank(storedData) || storedData.equals("null")) {
                            return Future.failedFuture(new InvalidRequestException(
                                    String.format("Invalid impId '%s' provided.", impId)
                                    ));
                        }
                        final Imp imp = mapper.mapper().readValue(storedData, Imp.class);
                        return Future.succeededFuture(imp);
                    } catch (Exception e) {
                        return Future.failedFuture(new InvalidRequestException(e.getMessage(), e));
                    }
                });
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
