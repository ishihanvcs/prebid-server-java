package com.azerion.prebid.settings.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
@NoArgsConstructor
public class CustomTrackerSetting implements Iterable<CustomTracker> {

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

    @Override
    public Iterator<CustomTracker> iterator() {
        return trackers.iterator();
    }

    @Override
    public void forEach(Consumer<? super CustomTracker> action) {
        trackers.forEach(action);
    }

    @Override
    public Spliterator<CustomTracker> spliterator() {
        return trackers.spliterator();
    }
}
