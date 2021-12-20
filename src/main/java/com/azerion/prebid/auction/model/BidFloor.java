package com.azerion.prebid.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class BidFloor {

    @JsonProperty("bidFloor")
    double bidFloor = 0.0;
}
