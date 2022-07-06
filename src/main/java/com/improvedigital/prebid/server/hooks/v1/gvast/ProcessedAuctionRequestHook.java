package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.GVastHookUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Objects;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);
    private final RequestUtils requestUtils;
    private final GVastHookUtils gVastHookUtils;

    public ProcessedAuctionRequestHook(
            RequestUtils requestUtils,
            GVastHookUtils gVastHookUtils
    ) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.gVastHookUtils = Objects.requireNonNull(gVastHookUtils);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        BidRequest bidRequest = auctionRequestPayload.bidRequest();
        try {
            validateRequestWithBusinessLogic(bidRequest);
            Object moduleContext = invocationContext.moduleContext();
            if (moduleContext == null) {
                GVastHooksModuleContext context = gVastHookUtils.createModuleContext(bidRequest);
                bidRequest = context.getBidRequest();
                moduleContext = context;
            }
            BidRequest finalBidRequest = bidRequest;
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> AuctionRequestPayloadImpl.of(finalBidRequest), moduleContext
            ));
        } catch (Throwable t) {
            logger.error(bidRequest, t);
            if (t instanceof GVastHookException) {
                return Future.succeededFuture(
                        InvocationResultImpl.rejected(t.getMessage())
                );
            }
        }
        return Future.succeededFuture(
                InvocationResultImpl.succeeded(
                        payload -> auctionRequestPayload
                )
        );
    }

    public void validateRequestWithBusinessLogic(BidRequest bidRequest) throws GVastHookException {
        if (!bidRequest.getImp().parallelStream().allMatch(
                imp -> requestUtils.getImprovePlacementId(imp) != null
        )) {
            throw new GVastHookException(
                    "improvedigital placementId is not defined for one or more imp(s)"
            );
        }
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-processed-auction-request";
    }
}
