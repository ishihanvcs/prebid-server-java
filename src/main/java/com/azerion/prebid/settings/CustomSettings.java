package com.azerion.prebid.settings;

import com.azerion.prebid.settings.model.CustomTracker;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

public interface CustomSettings {

    Future<CustomTrackerSetting> getCustomTrackerSetting(Timeout timeout);

    Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout);
}
