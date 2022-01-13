package com.improvedigital.prebid.server.customtrackers.hooks.v1;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.customtrackers.AuctionRequestModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    // private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof AuctionRequestModuleContext) {
            moduleContext = ((AuctionRequestModuleContext) moduleContext).with(bidRequest);
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, moduleContext
        ));
    }

    @Override
    public String code() {
        return "custom-tracker-processed-auction-request";
    }
}

