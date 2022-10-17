package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.customvast.CustomVastCreator;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.customvast.model.CreatorContext;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.LogMessage;
import com.improvedigital.prebid.server.utils.RequestUtils;
import com.improvedigital.prebid.server.utils.ResponseUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        final CreatorContext commonContext = CreatorContext.from(context, jsonUtils);
        final CustomVastCreator customVastCreator = new CustomVastCreator(customVastUtils);
        try {
            final Map<String, SeatBid> resultSeatBids = createResultSeatBidsMap(bidResponse);

            for (final Imp imp : bidRequest.getImp()) {
                final List<SeatBid> seatBidsForImp = ResponseUtils.findSeatBidsForImp(
                        bidResponse, imp
                );

                if (requestUtils.isCustomVastVideo(imp)) { // imp is configured for custom vast
                    final List<Bid> allBids = ResponseUtils.getBidsForImp(seatBidsForImp, imp);
                    final Bid winningBid = allBids.stream()
                            .reduce((bid1, bid2) ->
                                    bid1.getPrice().max(bid2.getPrice()).equals(bid1.getPrice()) ? bid1 : bid2
                            ).orElse(null);

                    if (jsonUtils.isBidWithVideoType(winningBid) || allBids.isEmpty()) {
                        final List<Bid> videoBids = allBids.parallelStream()
                                .filter(jsonUtils::isBidWithVideoType)
                                .collect(Collectors.toList());
                        // we'll create a Bid with custom vast only if:
                        // 1. the winning bid is of video type
                        // 2. no SSP has bid for this imp
                        final CreatorContext creatorContext = commonContext.with(
                                imp, new ArrayList<>(videoBids),
                                jsonUtils
                        );

                        final CustomVast customVast = customVastCreator.create(creatorContext);
                        final Bid customVastBid = customVastUtils.createBidFromCustomVast(creatorContext, customVast);
                        if (customVastBid != null) {
                            resultSeatBids.get(RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME)
                                    .getBid()
                                    .add(customVastBid);
                        }
                    } else if (winningBid != null) {
                        String seatName = seatBidsForImp.stream()
                                .filter(seatBid -> seatBid.getBid().contains(winningBid))
                                .map(SeatBid::getSeat)
                                .findFirst().orElse(null);
                        if (StringUtils.isNotBlank(seatName)) {
                            resultSeatBids.get(seatName).getBid().add(winningBid);
                        }
                    }
                } else { // imp is not configured for custom vast
                    // So, we'll simply copy all Bids into respective SeatBid
                    for (final SeatBid seatBid : seatBidsForImp) {
                        resultSeatBids.get(seatBid.getSeat()).getBid().addAll(
                                ResponseUtils.getBidsForImp(seatBid, imp)
                        );
                    }
                }
            }
            return bidResponse.toBuilder().seatbid(
                    new ArrayList<>(resultSeatBids.values()
                            .stream()
                            .filter(sb -> !sb.getBid().isEmpty()) // skip SeatBids with no bids
                            .collect(Collectors.toList())
                    )
            ).build();
        } catch (Throwable t) {
            logger.error(
                    LogMessage.from(bidRequest)
                            .with(bidResponse)
                            .with(t)
            );
        }
        return bidResponse;
    }

    private Map<String, SeatBid> createResultSeatBidsMap(BidResponse bidResponse) {
        final Map<String, SeatBid> resultMap = new HashedMap<>();
        for (final SeatBid seatBid : bidResponse.getSeatbid()) {
            resultMap.put(
                    seatBid.getSeat(),
                    seatBid.toBuilder().bid(new ArrayList<>()).build()
            );
        }

        resultMap.putIfAbsent(
                RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME,
                SeatBid.builder()
                        .seat(RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME)
                        .bid(new ArrayList<>())
                        .build()
        );
        return resultMap;
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-auction-response";
    }
}
