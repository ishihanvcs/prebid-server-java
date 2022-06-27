package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.auction.GVastBidCreator;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.GVastHookUtils;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
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
import java.util.stream.Collectors;

public class AuctionResponseHook implements org.prebid.server.hooks.v1.auction.AuctionResponseHook {

    private static final Logger logger = LoggerFactory.getLogger(AuctionResponseHook.class);

    private final JsonUtils jsonUtils;
    private final MacroProcessor macroProcessor;
    private final String externalUrl;
    private final String gamNetworkCode;
    private final String cacheHost;
    private final RequestUtils requestUtils;
    private final GVastHookUtils gVastHookUtils;

    public AuctionResponseHook(
            RequestUtils requestUtils,
            GVastHookUtils gVastHookUtils,
            MacroProcessor macroProcessor,
            String externalUrl,
            String gamNetworkCode,
            String cacheHost) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.gVastHookUtils = Objects.requireNonNull(gVastHookUtils);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
        this.macroProcessor = Objects.requireNonNull(macroProcessor);
        this.externalUrl = externalUrl;
        this.gamNetworkCode = gamNetworkCode;
        this.cacheHost = cacheHost;
    }

    @Override
    public Future<InvocationResult<AuctionResponsePayload>> call(
            AuctionResponsePayload auctionResponsePayload, AuctionInvocationContext invocationContext) {
        final BidResponse bidResponse;
        Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof GVastHooksModuleContext) {
            moduleContext = ((GVastHooksModuleContext) moduleContext)
                    .with(auctionResponsePayload.bidResponse());
            bidResponse = updatedBidResponse((GVastHooksModuleContext) moduleContext);
        } else {
            bidResponse = auctionResponsePayload.bidResponse();
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionResponsePayloadImpl.of(bidResponse), moduleContext
        ));
    }

    private BidResponse updatedBidResponse(GVastHooksModuleContext context) {
        final BidResponse bidResponse = context.getBidResponse();
        final BidRequest bidRequest = context.getBidRequest();
        final GVastBidCreator bidCreator = new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                externalUrl,
                gamNetworkCode,
                cacheHost
        );
        try {
            final Map<String, SeatBid> resultSeatBids = new HashedMap<>();
            SeatBid improveSeatBid = null;
            for (final Imp imp : bidRequest.getImp()) {
                final List<SeatBid> seatBidsForImp = bidResponse.getSeatbid().stream()
                        .filter(seatBid -> seatBid.getBid().stream()
                                .anyMatch(bid -> Objects.equals(bid.getImpid(), imp.getId()))
                        ).collect(Collectors.toList());
                if (!requestUtils.isNonVastVideo(imp)) {
                    for (final SeatBid seatBid : seatBidsForImp) {
                        final SeatBid resultSeatBid = copyEmptySeatBidIntoResultMapIfNotExist(resultSeatBids, seatBid);
                        resultSeatBid.getBid().addAll(
                                gVastHookUtils.getBidsForImpId(seatBid, imp)
                        );
                    }
                } else {
                    improveSeatBid = ObjectUtils.defaultIfNull(
                            improveSeatBid,
                            gVastHookUtils.findOrCreateSeatBid(
                                    RequestUtils.IMPROVE_BIDDER_NAME,
                                    seatBidsForImp
                            )
                    );

                    final SeatBid tempSeatBid = improveSeatBid.toBuilder().bid(
                            gVastHookUtils.getBidsForImpId(seatBidsForImp, imp)
                    ).build();

                    final Bid gVastBid = bidCreator.create(imp, tempSeatBid, true);

                    copyEmptySeatBidIntoResultMapIfNotExist(resultSeatBids, improveSeatBid)
                            .getBid().add(gVastBid);
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
        return "improvedigital-gvast-hooks-auction-response";
    }
}
