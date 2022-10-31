package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.SupplyChain;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;

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

        SupplyChain newSchain = getMergedSchain(invocationContext);
        if (newSchain == null) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> bidderRequestPayload, invocationContext.moduleContext()
            ));
        }

        BidRequest bidRequest = bidderRequestPayload.bidRequest();
        return Future.succeededFuture(InvocationResultImpl.succeeded(payload ->
                BidderRequestPayloadImpl.of(bidRequest.toBuilder()
                        .source(bidRequest.getSource() == null ? null : bidRequest.getSource().toBuilder()
                                .schain(newSchain) /* RTB 2.6 */
                                .ext(getMergedExtSource(bidRequest, newSchain)) /* RTB 2.5 */
                                .build())
                        .build()
                ), invocationContext.moduleContext()
        ));
    }

    private SupplyChain getMergedSchain(BidderInvocationContext context) {
        if (!(context.moduleContext() instanceof SupplyChainContext)) {
            return null;
        }

        // No schain update for improve digital.
        if (RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME.equalsIgnoreCase(context.bidder())) {
            return null;
        }

        return ((SupplyChainContext) context.moduleContext()).getMergedSupplyChain();
    }

    private ExtSource getMergedExtSource(BidRequest bidRequest, SupplyChain newSchain) {
        ExtSource extSource = ExtSource.of(newSchain);

        if (bidRequest.getSource().getExt() != null) {
            extSource.addProperties(bidRequest.getSource().getExt().getProperties());
        }

        return extSource;
    }
}
