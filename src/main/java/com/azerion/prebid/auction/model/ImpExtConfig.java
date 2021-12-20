package com.azerion.prebid.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class ImpExtConfig {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("bidFloors")
    Map<String, BidFloor> bidFloors;

    @JsonProperty("gamAdUnit")
    String gamAdUnit;

    Map<String, List<String>> waterfall;
}
