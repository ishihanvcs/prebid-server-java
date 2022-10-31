package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.SupplyChain;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.context.ApplicationContext;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
public class SupplyChainContext {

    private ApplicationContext applicationContext;

    private SupplyChain mergedSupplyChain;

    public static SupplyChainContext from(ApplicationContext applicationContext) {
        return SupplyChainContext.builder()
                .applicationContext(applicationContext)
                .build();
    }

    public SupplyChainContext with(SupplyChain mergedSupplyChain) {
        return this.toBuilder().mergedSupplyChain(mergedSupplyChain).build();
    }
}
