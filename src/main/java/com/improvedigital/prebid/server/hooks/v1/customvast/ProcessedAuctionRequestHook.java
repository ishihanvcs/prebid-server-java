package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
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
    private final CustomVastUtils customVastUtils;

    public ProcessedAuctionRequestHook(
            RequestUtils requestUtils,
            CustomVastUtils customVastUtils
    ) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.customVastUtils = Objects.requireNonNull(customVastUtils);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        try {
            validateRequestWithBusinessLogic(bidRequest);
            Object moduleContext = invocationContext.moduleContext();
            if (moduleContext == null) {
                return customVastUtils.resolveCountryAndCreateModuleContext(bidRequest, invocationContext.timeout())
                        .map(context -> InvocationResultImpl.succeeded(
                                payload -> AuctionRequestPayloadImpl.of(context.getBidRequest()), context
                        ));
            }
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> AuctionRequestPayloadImpl.of(bidRequest), moduleContext
            ));
        } catch (Throwable t) {
            logger.error(bidRequest, t);
            if (t instanceof CustomVastHooksException) {
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

    public void validateRequestWithBusinessLogic(BidRequest bidRequest) throws CustomVastHooksException {
        if (!bidRequest.getImp().parallelStream().allMatch(
                imp -> requestUtils.getImprovePlacementId(imp) != null
        )) {
            throw new CustomVastHooksException(
                    "improvedigital placementId is not defined for one or more imp(s)"
            );
        }
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-processed-auction-request";
    }
}
