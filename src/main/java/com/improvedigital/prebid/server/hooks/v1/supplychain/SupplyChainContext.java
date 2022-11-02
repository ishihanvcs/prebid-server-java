package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.SupplyChain;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
public class SupplyChainContext {

    private SupplyChain mergedSupplyChain;

    public static SupplyChainContext from(SupplyChain mergedSupplyChain) {
        return SupplyChainContext.builder()
                .mergedSupplyChain(mergedSupplyChain)
                .build();
    }
}
