package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class BidFloor {

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    @JsonProperty("bidFloorCur")
    @Builder.Default
    String bidFloorCur = "USD";
}
