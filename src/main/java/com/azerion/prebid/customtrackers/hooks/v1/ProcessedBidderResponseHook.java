package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.hooks.v1.CustomTrackerModule;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessedBidderResponseHook implements org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook {

    private final CustomTrackerModule module;

    public ProcessedBidderResponseHook(CustomTrackerModule module) {
        this.module = module;
    }

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload,
            BidderInvocationContext invocationContext) {
        final List<BidderBid> originalBids = bidderResponsePayload.bids();
        final String bidder = invocationContext.bidder();
        final List<BidderBid> updatedBids = originalBids.stream()
                .map(bidderBid -> module.modifyBidAdm(bidderBid, bidder))
                .collect(Collectors.toList());

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderResponsePayloadImpl.of(updatedBids)));
    }

    @Override
    public String code() {
        return "custom-tracker-processed-bidder-response";
    }
}
