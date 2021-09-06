package com.azerion.prebid.settings.model;

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;

@Builder
@Value
public class Placement {

    String id;

    String accountId;

    String gamAdUnit;

    Double bidFloor;

    String[] waterfall;

    public Placement merge(Placement another) {
        return Placement.builder()
                .id(ObjectUtils.defaultIfNull(id, another.id))
                .accountId(ObjectUtils.defaultIfNull(accountId, another.accountId))
                .bidFloor(ObjectUtils.defaultIfNull(bidFloor, another.bidFloor))
                .waterfall(ObjectUtils.defaultIfNull(waterfall, another.waterfall))
                .build();
    }

    public static Placement empty(String id) {
        return Placement.builder()
                .id(id)
                .build();
    }
}
