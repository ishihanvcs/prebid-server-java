package com.improvedigital.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Builder(toBuilder = true)
@Value
public class CustomTracker {

    @JsonProperty("urlTemplate")
    String urlTemplate;

    @NonFinal
    String id;

    @Builder.Default
    Boolean enabled = true;

    @JsonProperty("macroResolver")
    @Builder.Default
    String macroResolver = null;

    @Builder.Default
    String injector = null;

    @JsonProperty("excludedAccounts")
    @Builder.Default
    List<String> excludedAccounts = List.of();

    public static Map<String, CustomTracker> filterDisabledTrackers(Map<String, CustomTracker> trackersMap) {
        trackersMap = trackersMap == null ? Map.of() : trackersMap;
        trackersMap.forEach((key, value) -> {
            value.id = key;
        });
        return trackersMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
