package com.azerion.prebid.settings;

import com.azerion.prebid.exception.SettingsLoaderException;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
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
import org.springframework.context.ApplicationContext;

import java.time.Clock;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

public class SettingsLoader {

    private static final long DEFAULT_SETTINGS_LOADING_TIMEOUT = 500L;

    private final ApplicationContext applicationContext;
    private final ApplicationSettings applicationSettings;
    private final CustomSettings customSettings;
    private final Clock clock;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    public SettingsLoader(
            ApplicationContext applicationContext,
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            Metrics metrics,
            JacksonMapper mapper,
            Clock clock) {
        this.applicationContext = applicationContext;
        this.applicationSettings = applicationSettings;
        this.customSettings = customSettings;
        this.metrics = metrics;
        this.mapper = mapper;
        this.clock = clock;
    }

    private <T> Future<T> getSettingFuture(
            String id, String settingType,
            BiFunction<String, Timeout, Future<T>> loaderFn
    ) {
        if (StringUtils.isBlank(id)) {
            return Future.failedFuture(
                    new SettingsLoaderException(
                            String.format("%s id cannot be blank", settingType)
                    ));
        }
        return loaderFn.apply(id, createSettingsLoadingTimeout());
    }

    private <T> T getSetting(
            String id, String settingType,
            BiFunction<String, Timeout, Future<T>> loaderFn
    ) {
        Future<T> future = getSettingFuture(id, settingType, loaderFn);
        final T setting = future.result();
        if (future.failed()) {
            if (future.cause() instanceof SettingsLoaderException) {
                throw (SettingsLoaderException) future.cause();
            }
            throw new SettingsLoaderException(future.cause().getMessage(), future.cause());
        } else if (setting == null) {
            throw new SettingsLoaderException(
                    String.format("No %s found with id: %s", settingType, id)
            );
        }
        return setting;
    }

    private Timeout createSettingsLoadingTimeout() {
        final long lngTimeoutMs = applicationContext
                .getEnvironment()
                .getProperty("settings.default-loading-timeout", Long.class, DEFAULT_SETTINGS_LOADING_TIMEOUT);
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        return timeoutFactory.create(lngTimeoutMs);
    }

    public Future<Account> getAccountFuture(String accountId) {
        return getSettingFuture(accountId, "account", applicationSettings::getAccountById);
    }

    public Future<CustomTrackerSetting> getCustomTrackerSettingFuture() {
        return customSettings.getCustomTrackerSetting(createSettingsLoadingTimeout());
    }

    public Future<Imp> getStoredImp(String impId) {
        return getStoredDataResultFuture(null, Collections.emptySet(), Set.of(impId))
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
            String accountId, Set<String> requestIds, final Set<String> impIds
    ) {
        return applicationSettings.getStoredData(accountId, requestIds, impIds, createSettingsLoadingTimeout())
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

    public CustomTrackerSetting getCustomTrackerSetting() {
        Future<CustomTrackerSetting> future = getCustomTrackerSettingFuture();
        final CustomTrackerSetting customTrackerSetting = future.result();
        if (future.failed()) {
            if (future.cause() instanceof SettingsLoaderException) {
                throw (SettingsLoaderException) future.cause();
            }
            throw new SettingsLoaderException(future.cause().getMessage(), future.cause());
        } else if (customTrackerSetting == null) {
            return new CustomTrackerSetting();
        }
        return customTrackerSetting;
    }
}
