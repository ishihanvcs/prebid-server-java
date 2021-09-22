package com.azerion.prebid.settings.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
@NoArgsConstructor
public class CustomTrackerSetting {

    @NonFinal
    boolean enabled = false;
    @NonFinal
    List<CustomTracker> trackers = Collections.emptyList();

    @Getter(AccessLevel.NONE)
    @NonFinal
    Map<String, CustomTracker> trackersMap = null;

    public Map<String, CustomTracker> getTrackersMap() {
        if (trackersMap != null) {
            return trackersMap;
        }
        trackersMap = this.enabled
                ? trackers.stream().collect(Collectors.toMap(CustomTracker::getId, Function.identity()))
                : Collections.emptyMap();

        return trackersMap;
    }
}
