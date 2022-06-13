package com.improvedigital.prebid.server.hooks.v1.customtrackers;

import com.improvedigital.prebid.server.customtrackers.AuctionRequestModuleContext;
import com.improvedigital.prebid.server.customtrackers.BidderBidModifier;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessedBidderResponseHook implements org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedBidderResponseHook.class);
    private final BidderBidModifier bidderBidModifier;

    public ProcessedBidderResponseHook(
            BidderBidModifier bidderBidModifier
    ) {
        this.bidderBidModifier = bidderBidModifier;
    }

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload,
            BidderInvocationContext invocationContext) {

        final List<BidderBid> originalBids = bidderResponsePayload.bids();
        final String bidder = invocationContext.bidder();
        final Object moduleContext = invocationContext.moduleContext();

        final List<BidderBid> updatedBids = maybeUpdateBids(
                moduleContext, originalBids, bidder
        );
        return Future.succeededFuture(
                InvocationResultImpl.succeeded(payload ->
                        BidderResponsePayloadImpl.of(updatedBids), moduleContext));
    }

    private List<BidderBid> maybeUpdateBids(
            Object moduleContext,
            List<BidderBid> originalBids,
            String bidder
    ) {
        if (!(moduleContext instanceof AuctionRequestModuleContext)) {
            return originalBids;
        }

        try {
            return originalBids.stream()
                    .map(bidderBid -> bidderBidModifier.modifyBidAdm(
                            (AuctionRequestModuleContext) moduleContext, bidderBid, bidder
                    ))
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            logger.warn(((AuctionRequestModuleContext) moduleContext).getBidRequest(), t);
        }
        return originalBids;
    }

    @Override
    public String code() {
        return "improvedigital-custom-trackers-hooks-processed-bidder-response";
    }
}

