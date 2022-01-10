package com.azerion.prebid.customtrackers;

import com.iab.openrtb.request.BidRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.springframework.context.ApplicationContext;

@SuperBuilder(toBuilder = true)
@Getter
public class ModuleContext {

    @NonNull
    ApplicationContext applicationContext;
    BidRequest bidRequest;

    public static ModuleContext from(
            ApplicationContext applicationContext
    ) {
        return ModuleContext.builder()
                .applicationContext(applicationContext)
                .build();
    }

    public ModuleContext with(
            BidRequest bidRequest
    ) {
        return this.toBuilder()
                .bidRequest(bidRequest)
                .build();
    }
}
