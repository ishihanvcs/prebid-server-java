package com.azerion.prebid.settings;

import com.azerion.prebid.settings.model.CustomTracker;
import com.azerion.prebid.settings.model.Placement;
import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

import java.util.Map;

public interface CustomSettings {

    /**
     * Returns {@link Placement} for the given account ID.
     */
    Future<Placement> getPlacementById(String placementId, Timeout timeout);

    Future<Map<String, CustomTracker>> getAllCustomTrackers(Timeout timeout);

    Future<CustomTracker> getCustomTrackerById(String trackerId, Timeout timeout);
}
