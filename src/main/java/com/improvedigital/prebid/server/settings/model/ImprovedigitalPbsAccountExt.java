package com.improvedigital.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsAccountExt {

    @JsonProperty("bidPriceAdjustment")
    Double bidPriceAdjustment;

    @JsonProperty("bidPriceAdjustmentIncImprove")
    Boolean bidPriceAdjustmentIncImprove;

    @JsonIgnore
    public BigDecimal getBidPriceAdjustmentRounded() {
        if (bidPriceAdjustment == null) {
            return null;
        }

        return BigDecimal.valueOf(bidPriceAdjustment)
                .setScale(4, RoundingMode.HALF_EVEN);
    }

    @JsonIgnore
    public boolean shouldIncludeImproveForAdjustment() {
        return bidPriceAdjustmentIncImprove == null ? false : bidPriceAdjustmentIncImprove;
    }
}
