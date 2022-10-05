package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

public class BidderRequestHook implements org.prebid.server.hooks.v1.bidder.BidderRequestHook {

    @Override
    public String code() {
        return "improvedigital-bidadjustment-hooks-bidder-request";
    }

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
            BidderRequestPayload bidderRequestPayload, BidderInvocationContext invocationContext) {

        final BidRequest updatedBidRequest = removeUnnecessaryProperties(bidderRequestPayload.bidRequest());

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .ext(updatedBidRequest.getExt())
                        .build()), invocationContext.moduleContext())
        );
    }

    private BidRequest removeUnnecessaryProperties(BidRequest request) {
        return request.toBuilder()
                .ext(request.getExt() == null ? null : ExtRequest.of(
                        request.getExt().getPrebid() == null ? null : request.getExt().getPrebid().toBuilder()
                                /* We do not want to propagate bid adjusting factors to the SSP. */
                                .bidadjustmentfactors(null)
                                .build()
                ))
                .build();
    }
}
