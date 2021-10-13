package com.azerion.prebid.hooks.v1;

import com.azerion.prebid.customtrackers.BidRequestContext;
import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.azerion.prebid.customtrackers.hooks.v1.ProcessedAuctionRequestHook;
import com.azerion.prebid.customtrackers.hooks.v1.ProcessedBidderResponseHook;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;

public class CustomTrackerModule implements Module {

    private final ApplicationContext applicationContext;
    private final BidderBidModifier bidderBidModifier;

    private BidRequestContext bidRequestContext = null;

    public CustomTrackerModule(ApplicationContext applicationContext, BidderBidModifier bidderBidModifier) {
        this.applicationContext = applicationContext;
        this.bidderBidModifier = bidderBidModifier;
    }

    @Override
    public String code() {
        return "custom-tracker-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new ProcessedAuctionRequestHook(this),
                new ProcessedBidderResponseHook(this)
        );
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public BidderBidModifier getBidderBidModifier() {
        return bidderBidModifier;
    }

    public BidRequestContext getBidRequestContext() {
        return bidRequestContext;
    }

    public void setBidRequestContext(BidRequestContext bidRequestContext) {
        this.bidRequestContext = bidRequestContext;
    }

    public void setBidRequestContext(BidRequest bidRequest) {
        bidRequestContext = BidRequestContext.from(applicationContext, bidRequest);
    }

    public BidderBid modifyBidAdm(
            BidderBid bidderBid,
            String bidder
    ) {
        return bidderBidModifier.modifyBidAdm(bidRequestContext, bidderBid, bidder);
    }
}
