package com.azerion.prebid.settings;

import com.azerion.prebid.exception.SettingsLoaderException;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import com.azerion.prebid.settings.model.Placement;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.util.function.BiFunction;

public class SettingsLoader {

    private final ApplicationSettings applicationSettings;
    private final CustomSettings customSettings;
    private final Timeout settingsLoadingTimeout;

    public SettingsLoader(
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            Timeout settingsLoadingTimeout) {
        this.applicationSettings = applicationSettings;
        this.customSettings = customSettings;
        this.settingsLoadingTimeout = settingsLoadingTimeout;
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
        return loaderFn.apply(id, settingsLoadingTimeout);
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

    public Future<Placement> getPlacementFuture(String placementId) {
        return getSettingFuture(placementId, "placement", customSettings::getPlacementById);
    }

    public Future<Account> getAccountFuture(String accountId) {
        return getSettingFuture(accountId, "account", applicationSettings::getAccountById);
    }

    public Future<CustomTrackerSetting> getCustomTrackerSettingFuture() {
        return customSettings.getCustomTrackerSetting(settingsLoadingTimeout);
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
