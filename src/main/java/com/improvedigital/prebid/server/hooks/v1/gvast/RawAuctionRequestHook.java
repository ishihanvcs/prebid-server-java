package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.HashMap;
import java.util.Map;

public class RawAuctionRequestHook implements org.prebid.server.hooks.v1.auction.RawAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(RawAuctionRequestHook.class);
    private final ObjectMapper mapper;
    private final JsonUtils jsonUtils;

    public RawAuctionRequestHook(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
        this.mapper = jsonUtils.getObjectMapper();
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, createModuleContext(bidRequest)
        ));
    }

    private GVastHooksModuleContext createModuleContext(BidRequest bidRequest) {
        final Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt = new HashMap<>();
        for (final Imp imp : bidRequest.getImp()) {
            impIdToPbsImpExt.put(imp.getId(), jsonUtils.getImprovedigitalPbsImpExt(imp));
        }
        return GVastHooksModuleContext.from(impIdToPbsImpExt);
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-raw-auction-request";
    }
}
