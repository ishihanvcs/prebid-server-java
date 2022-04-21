package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsImpExt {

    public static final String DEFAULT_CONFIG_KEY = "default";

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("floors")
    Map<String, Floor> floors = Map.of(DEFAULT_CONFIG_KEY, Floor.of(0.0, "USD"));

    @JsonProperty("gam")
    ImprovedigitalPbsImpExtGam improvedigitalPbsImpExtGam;

    Map<String, List<String>> waterfall = Map.of(DEFAULT_CONFIG_KEY, List.of("gam"));
}
