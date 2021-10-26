package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.customtrackers.BidRequestContext;
import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.azerion.prebid.customtrackers.CustomTrackerModuleContext;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessedBidderResponseHook implements org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedBidderResponseHook.class);
    private final ApplicationContext applicationContext;
    private final BidderBidModifier bidderBidModifier;

    public ProcessedBidderResponseHook(
            ApplicationContext applicationContext,
            BidderBidModifier bidderBidModifier
    ) {
        this.bidderBidModifier = bidderBidModifier;
        this.applicationContext = applicationContext;
    }

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload,
            BidderInvocationContext invocationContext) {
        final List<BidderBid> originalBids = bidderResponsePayload.bids();
        final String bidder = invocationContext.bidder();
        final BidRequestContext bidRequestContext = getBidRequestContext(invocationContext);
        final List<BidderBid> updatedBids = updateBids(bidRequestContext, originalBids, bidder);

        return Future.succeededFuture(
                InvocationResultImpl.succeeded(payload ->
                BidderResponsePayloadImpl.of(updatedBids), bidRequestContext));
    }

    private List<BidderBid> updateBids(
            BidRequestContext bidRequestContext,
            List<BidderBid> originalBids,
            String bidder
    ) {
        if (bidRequestContext == null) {
            return originalBids;
        }
        return originalBids.stream()
                .map(bidderBid -> bidderBidModifier.modifyBidAdm(bidRequestContext, bidderBid, bidder))
                .collect(Collectors.toList());
    }

    private BidRequestContext getBidRequestContext(
            AuctionInvocationContext invocationContext
    ) {
        final Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof BidRequestContext) {
            return (BidRequestContext) invocationContext.moduleContext();
        }
        if (moduleContext instanceof CustomTrackerModuleContext) {
            return BidRequestContext.from(
                    applicationContext,
                    (CustomTrackerModuleContext) moduleContext
            );
        }
        return null;
    }

    @Override
    public String code() {
        return "custom-tracker-processed-bidder-response";
    }
}
