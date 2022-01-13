package com.improvedigital.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

@Value
public class ParsedCustomSettings {

    @JsonProperty("trackers")
    Map<String, CustomTracker> trackers = Map.of();
}
