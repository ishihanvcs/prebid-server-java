package com.improvedigital.prebid.server.customtracker.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
public class ModuleContext {

    @NonNull
    ApplicationContext applicationContext;
    BidRequest bidRequest;
    Account account;

    public static ModuleContext from(
            ApplicationContext applicationContext
    ) {
        return ModuleContext.builder()
                .applicationContext(applicationContext)
                .build();
    }
}

