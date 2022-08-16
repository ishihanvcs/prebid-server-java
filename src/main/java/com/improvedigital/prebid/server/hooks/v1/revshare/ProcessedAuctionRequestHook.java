package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    private final JsonUtils jsonUtils;

    public ProcessedAuctionRequestHook(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public String code() {
        return "improvedigital-bidadjustment-hooks-processed-auction-request";
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        final BigDecimal bidAdjustment = BigDecimal.valueOf(0.95); /* TODO */

        if (bidAdjustment == null) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        List<String> biddersToCutRevShare = Arrays.asList("generic"); /* TODO */

        final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder().build();
        biddersToCutRevShare.stream().forEach(b -> factors.addFactor(b, bidAdjustment));

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionRequestPayloadImpl.of(auctionRequestPayload.bidRequest().toBuilder()
                        .ext(ExtRequest.of(auctionRequestPayload.bidRequest().getExt().getPrebid().toBuilder()
                                .bidadjustmentfactors(factors)
                                .build()))
                        .build()
                ), invocationContext.moduleContext()
        ));
    }
}

