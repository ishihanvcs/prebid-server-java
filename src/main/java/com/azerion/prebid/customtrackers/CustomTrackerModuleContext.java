package com.azerion.prebid.customtrackers;

import com.azerion.prebid.settings.model.Placement;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class CustomTrackerModuleContext {

    BidRequest bidRequest;
    Placement placement;

    public CustomTrackerModuleContext with(
            BidRequest bidRequest
    ) {
        return this.toBuilder()
                .bidRequest(bidRequest)
                .build();
    }

    public static CustomTrackerModuleContext from(
            BidRequest bidRequest,
            Placement placement
    ) {
        return CustomTrackerModuleContext.builder()
                .bidRequest(bidRequest)
                .placement(placement)
                .build();
    }
}
