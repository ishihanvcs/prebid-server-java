package com.improvedigital.prebid.server.customtracker.model;

import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.iab.openrtb.request.BidRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

@SuperBuilder(toBuilder = true)
@Getter
public class AuctionRequestModuleContext extends ModuleContext {

    Collection<CustomTracker> customTrackers;

    public static AuctionRequestModuleContext from(
            ApplicationContext applicationContext,
            Collection<CustomTracker> customTrackers
    ) {
        return AuctionRequestModuleContext.builder()
                .applicationContext(applicationContext)
                .customTrackers(customTrackers)
                .build();
    }

    public AuctionRequestModuleContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }
}
