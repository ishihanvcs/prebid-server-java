package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.math.BigDecimal;

public class AuctionResponseHook implements org.prebid.server.hooks.v1.auction.AuctionResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(AuctionResponseHook.class);

    private final JsonUtils jsonUtils;

    public AuctionResponseHook(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    @Override
    public String code() {
        return "improvedigital-bidadjustment-hooks-processed-auction-request";
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(
            AuctionResponsePayload auctionResponsePayload, AuctionInvocationContext invocationContext) {

        final BigDecimal bidAdjustment = null; /* TODO: Take from account. */

        if (bidAdjustment == null) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionResponsePayload, invocationContext.moduleContext()
            ));
        }

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionResponsePayloadImpl.of(auctionResponsePayload.bidResponse().toBuilder()
                        .seatbid(auctionResponsePayload.bidResponse().getSeatbid().stream()
                                .map(seatBid -> seatBid.toBuilder()
                                        .bid(seatBid.getBid().stream()
                                                .map(bid -> bid.toBuilder()
                                                        // Adjusting the bid.
                                                        .price(bid.getPrice().multiply(bidAdjustment))
                                                        .build())
                                                .toList())
                                        .build())
                                .toList())
                        .build()
                ), invocationContext.moduleContext()
        ));
    }
}

