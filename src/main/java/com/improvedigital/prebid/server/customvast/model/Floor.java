package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Floor {

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    @JsonProperty("bidFloorCur")
    String bidFloorCur;
}
