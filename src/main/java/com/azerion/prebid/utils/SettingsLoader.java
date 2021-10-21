package com.azerion.prebid.utils;

import com.azerion.prebid.exception.CustomSettingsLoaderException;
import com.azerion.prebid.settings.CustomSettings;
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

    private <T> T getSetting(
            String id, String settingType,
            BiFunction<String, Timeout, Future<T>> loaderFn
    ) {
        if (StringUtils.isBlank(id)) {
            throw new CustomSettingsLoaderException(
                    String.format("%s id cannot be blank", settingType)
            );
        }
        Future<T> future = loaderFn.apply(id, settingsLoadingTimeout);
        final T setting = future.result();
        if (future.failed()) {
            if (future.cause() instanceof CustomSettingsLoaderException) {
                throw (CustomSettingsLoaderException) future.cause();
            }
            throw new CustomSettingsLoaderException(future.cause().getMessage(), future.cause());
        } else if (setting == null) {
            throw new CustomSettingsLoaderException(
                    String.format("No %s found with id: %s", settingType, id)
            );
        }
        return setting;
    }

    public Placement getPlacement(String placementId) throws Exception {
        return getSetting(placementId, "placement", customSettings::getPlacementById);
    }

    public Account getAccount(String accountId) throws Exception {
        return getSetting(accountId, "account", applicationSettings::getAccountById);
    }

    public CustomTrackerSetting getCustomTrackerSetting() {
        Future<CustomTrackerSetting> future = customSettings.getCustomTrackerSetting(settingsLoadingTimeout);
        final CustomTrackerSetting customTrackerSetting = future.result();
        if (future.failed()) {
            if (future.cause() instanceof CustomSettingsLoaderException) {
                throw (CustomSettingsLoaderException) future.cause();
            }
            throw new CustomSettingsLoaderException(future.cause().getMessage(), future.cause());
        } else if (customTrackerSetting == null) {
            return new CustomTrackerSetting();
        }
        return customTrackerSetting;
    }
}
