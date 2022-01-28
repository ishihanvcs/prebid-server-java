package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class BidFloor {

    @JsonProperty("bidFloor")
    double bidFloor;

    @JsonProperty("bidFloorCur")
    double bidFloorCur;
}
