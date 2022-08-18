package com.improvedigital.prebid.server.settings.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.improvedigital.prebid.server.utils.JsonUtils;

import java.math.BigDecimal;

public class AccountExt extends ObjectNode {

    private final JsonUtils jsonUtils;

    public AccountExt(JsonUtils jsonUtils) {
        super(jsonUtils.getObjectMapper().getNodeFactory());

        this.jsonUtils = jsonUtils;
    }

    public AccountExt(JsonUtils jsonUtils, ObjectNode initialNode) {
        super(jsonUtils.getObjectMapper().getNodeFactory());

        this.jsonUtils = jsonUtils;
        initialNode.fieldNames().forEachRemaining(k -> putIfAbsent(k, initialNode.get(k)));
    }

    public BigDecimal getBidPriceAdjustment() {
        return jsonUtils.objectPathToValue(
                this, "/bidPriceAdjustment", BigDecimal.class
        );
    }

    public AccountExt setBidPriceAdjustment(BigDecimal bid) {
        put("bidPriceAdjustment", bid);
        return this;
    }

    public Boolean getBidPriceAdjustmentIncImprove() {
        Boolean isToInclude = jsonUtils.objectPathToValue(
                this, "/bidPriceAdjustmentIncImprove", Boolean.class
        );
        return isToInclude == null ? Boolean.FALSE : isToInclude;
    }

    public AccountExt setBidPriceAdjustmentIncImprove(Boolean isToInclude) {
        put("bidPriceAdjustmentIncImprove", isToInclude);
        return this;
    }
}
