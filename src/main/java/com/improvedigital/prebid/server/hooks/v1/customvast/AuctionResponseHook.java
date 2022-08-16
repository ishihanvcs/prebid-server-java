package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.customvast.CustomVastCreator;
import com.improvedigital.prebid.server.customvast.model.CreatorContext;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import com.improvedigital.prebid.server.utils.ResponseUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AuctionResponseHook implements org.prebid.server.hooks.v1.auction.AuctionResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(AuctionResponseHook.class);

    private final JsonUtils jsonUtils;
    private final RequestUtils requestUtils;
    private final CustomVastUtils customVastUtils;

    public AuctionResponseHook(
            RequestUtils requestUtils,
            CustomVastUtils customVastUtils) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.customVastUtils = Objects.requireNonNull(customVastUtils);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(
            AuctionResponsePayload auctionResponsePayload, AuctionInvocationContext invocationContext) {
        final BidResponse bidResponse;
        Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof HooksModuleContext) {
            moduleContext = ((HooksModuleContext) moduleContext)
                    .with(auctionResponsePayload.bidResponse());
            bidResponse = updatedBidResponse((HooksModuleContext) moduleContext);
        } else {
            bidResponse = auctionResponsePayload.bidResponse();
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionResponsePayloadImpl.of(bidResponse), moduleContext
        ));
    }

    private BidResponse updatedBidResponse(HooksModuleContext context) {
        final BidResponse bidResponse = context.getBidResponse();
        final BidRequest bidRequest = context.getBidRequest();
        final CreatorContext commonContext = CreatorContext
                .from(bidRequest, bidResponse, jsonUtils);
        final CustomVastCreator customVastCreator = new CustomVastCreator(customVastUtils);
        try {
            final Map<String, SeatBid> resultSeatBids = new HashedMap<>();
            SeatBid improveSeatBid = null;
            for (final Imp imp : bidRequest.getImp()) {
                final List<SeatBid> seatBidsForImp = ResponseUtils.findSeatBidsForImp(
                        bidResponse, imp
                );
                if (!requestUtils.isCustomVastVideo(imp)) {
                    for (final SeatBid seatBid : seatBidsForImp) {
                        final SeatBid resultSeatBid = copyEmptySeatBidIntoResultMapIfNotExist(resultSeatBids, seatBid);
                        resultSeatBid.getBid().addAll(
                                ResponseUtils.getBidsForImp(seatBid, imp)
                        );
                    }
                } else {
                    improveSeatBid = ObjectUtils.defaultIfNull(
                            improveSeatBid,
                            ResponseUtils.findOrCreateSeatBid(
                                    RequestUtils.IMPROVE_BIDDER_NAME,
                                    seatBidsForImp
                            )
                    );
                    final CreatorContext creatorContext = commonContext.with(
                            imp,
                            ResponseUtils.getBidsForImp(seatBidsForImp, imp),
                            jsonUtils
                    );

                    final CustomVast customVast = customVastCreator.create(creatorContext);
                    final Bid bid = customVastUtils.createBidFromCustomVast(creatorContext, customVast);

                    if (bid != null) {
                        copyEmptySeatBidIntoResultMapIfNotExist(resultSeatBids, improveSeatBid)
                                .getBid().add(bid);
                    }
                }
            }
            return bidResponse.toBuilder().seatbid(
                    new ArrayList<>(resultSeatBids.values())
            ).build();
        } catch (Throwable t) {
            logger.error(context, t);
        }
        return bidResponse;
    }

    private SeatBid copyEmptySeatBidIntoResultMapIfNotExist(Map<String, SeatBid> resultMap, SeatBid srcSeatBid) {
        final SeatBid seatBid = ObjectUtil.getIfNotNullOrDefault(
                resultMap.get(srcSeatBid.getSeat()),
                sb -> sb,
                () -> srcSeatBid.toBuilder().bid(new ArrayList<>()).build()
        );

        resultMap.put(seatBid.getSeat(), seatBid);
        return seatBid;
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-auction-response";
    }
}