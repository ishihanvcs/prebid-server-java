package com.improvedigital.prebid.server.customtrackers;

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
}

