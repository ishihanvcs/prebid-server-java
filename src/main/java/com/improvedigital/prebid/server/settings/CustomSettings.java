package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.improvedigital.prebid.server.settings.model.CustomTrackerSetting;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

public interface CustomSettings {

    Future<CustomTrackerSetting> getCustomTrackerSetting(Timeout timeout);

    Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout);
}
