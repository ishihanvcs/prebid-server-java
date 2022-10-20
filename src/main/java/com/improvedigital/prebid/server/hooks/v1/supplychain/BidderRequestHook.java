package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;

public class BidderRequestHook implements org.prebid.server.hooks.v1.bidder.BidderRequestHook {

    private final JsonUtils jsonUtils;

    public BidderRequestHook(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public String code() {
        return "improvedigital-supplychain-hooks-bidder-request";
    }

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(
            BidderRequestPayload bidderRequestPayload, BidderInvocationContext invocationContext) {

        BidRequest bidRequest = bidderRequestPayload.bidRequest();
        String bidderName = invocationContext.bidder();

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderRequestPayloadImpl.of(bidRequest.toBuilder()
                        .source(bidRequest.getSource() == null ? null : bidRequest.getSource().toBuilder()
                                .ext(jsonUtils.removeMergedSourceSchain(
                                        bidRequest.getSource(),
                                        !RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME.equalsIgnoreCase(bidderName)
                                ))
                                .build())
                        .build()
                ), invocationContext.moduleContext()
        ));
    }
}
