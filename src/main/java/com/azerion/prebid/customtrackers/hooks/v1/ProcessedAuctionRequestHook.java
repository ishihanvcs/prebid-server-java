package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.hooks.v1.CustomTrackerModule;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private final CustomTrackerModule module;

    public ProcessedAuctionRequestHook(CustomTrackerModule module) {
        this.module = module;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest originalBidRequest = auctionRequestPayload.bidRequest();
        module.setBidRequestContext(originalBidRequest);
        return Future.succeededFuture(
                InvocationResultImpl.succeeded(
                        payload -> AuctionRequestPayloadImpl.of(originalBidRequest))
        );
    }

    @Override
    public String code() {
        return "custom-tracker-processed-auction-request";
    }
}
