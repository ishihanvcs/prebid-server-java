package com.improvedigital.prebid.server.customtrackers.hooks.v1;

import com.improvedigital.prebid.server.customtrackers.ModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        final Object context = getModuleContext(invocationContext, bidRequest);
        return Future.succeededFuture(
                InvocationResultImpl.succeeded(
                        payload -> AuctionRequestPayloadImpl.of(bidRequest),
                        context)
        );
    }

    private Object getModuleContext(
            AuctionInvocationContext invocationContext,
            BidRequest bidRequest
    ) {
        if (invocationContext.moduleContext() instanceof ModuleContext) {
            return ((ModuleContext) invocationContext.moduleContext()).with(bidRequest);
        }
        return invocationContext.moduleContext();
    }

    @Override
    public String code() {
        return "custom-tracker-processed-auction-request";
    }
}
