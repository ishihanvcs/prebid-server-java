package com.azerion.prebid.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ImpExtConfig {

    public static final String DEFAULT_CONFIG_KEY = "default";

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("bidFloors")
    Map<String, BidFloor> bidFloors = Map.of(DEFAULT_CONFIG_KEY, BidFloor.of(0.0));

    @JsonProperty("gamAdUnit")
    String gamAdUnit;

    Map<String, List<String>> waterfall = Map.of(DEFAULT_CONFIG_KEY, List.of("gam"));
}
