package com.improvedigital.prebid.server.customtrackers.hooks.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

import java.util.List;
import java.util.stream.Collectors;

public class BidderRequestHook implements org.prebid.server.hooks.v1.bidder.BidderRequestHook {

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
            BidderRequestPayload bidderRequestPayload, BidderInvocationContext invocationContext) {

        final BidRequest originalBidRequest = bidderRequestPayload.bidRequest();

        final BidRequest updatedBidRequest = removeExtraPropertiesFromImpExt(originalBidRequest);

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .imp(updatedBidRequest.getImp())
                        .build()), invocationContext.moduleContext())
        );
    }

    @Override
    public String code() {
        return "custom-tracker-bidder-request";
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
}
