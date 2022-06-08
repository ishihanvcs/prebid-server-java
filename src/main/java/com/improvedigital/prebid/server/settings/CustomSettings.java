package com.improvedigital.prebid.server.settings;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

import java.util.Collection;
import java.util.Map;

public interface CustomSettings {

    Future<Collection<CustomTracker>> getCustomTrackers(Timeout timeout);

    Future<Map<String, CustomTracker>> getCustomTrackersMap(Timeout timeout);

    Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout);
}
