package com.improvedigital.prebid.server.customtrackers.hooks.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.customtrackers.AuctionRequestModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.List;
import java.util.stream.Collectors;

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

    private BidRequest removeExtraPropertiesFromImpExt(BidRequest request) {
        return request.toBuilder().imp(
                request.getImp().stream().map(imp -> {
                    final ObjectNode extCopy = imp.getExt().deepCopy();
                    final JsonNode extPrebidNode = extCopy.at("/prebid");
                    if (extPrebidNode.isObject()) {
                        ((ObjectNode) extPrebidNode).remove(List.of("improvedigitalpbs", "storedrequest"));
                        return imp.toBuilder().ext(extCopy).build();
                    }
                    return imp;
                }).collect(Collectors.toList())
        ).build();
    }

    @Override
    public String code() {
        return "custom-tracker-processed-auction-request";
    }
}

