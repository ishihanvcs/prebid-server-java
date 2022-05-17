package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.auction.GVastBidCreator;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import io.vertx.core.Future;
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

    private static final boolean PRIORITIZE_IMPROVE_DIGITAL_DEALS = true;
    private static final String IMPROVE_SEATBID_NAME = "improvedigital";

    private final JsonUtils jsonUtils;
    private final MacroProcessor macroProcessor;
    private final String externalUrl;
    private final String gamNetworkCode;
    private final String cacheHost;

    public AuctionResponseHook(
            JsonUtils jsonUtils,
            MacroProcessor macroProcessor,
            String externalUrl,
            String gamNetworkCode,
            String cacheHost) {
        this.jsonUtils = jsonUtils;
        this.macroProcessor = macroProcessor;
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
                bidRequest,
                bidResponse,
                externalUrl,
                gamNetworkCode,
                cacheHost
        );
        final Map<String, SeatBid> lookupSeatBids = new HashedMap<>();
        SeatBid improveSeatBid = null;
        for (final Imp imp : bidRequest.getImp()) {
            final List<SeatBid> seatBidsForImp = bidResponse.getSeatbid().stream()
                    .filter(seatBid -> seatBid.getBid().stream()
                            .anyMatch(bid -> Objects.equals(bid.getImpid(), imp.getId()))
                    ).collect(Collectors.toList());
            if (!shouldProcess(imp)) {
                for (final SeatBid seatBid : seatBidsForImp) {
                    final SeatBid resultSeatBid = findOrCopySeatBidInLookupMap(lookupSeatBids, seatBid);
                    resultSeatBid.getBid().addAll(
                            getBidsForImpId(seatBid, imp)
                    );
                }
            } else {
                improveSeatBid = ObjectUtils.defaultIfNull(improveSeatBid, seatBidsForImp.stream()
                        .filter(seatBid ->
                                IMPROVE_SEATBID_NAME.equals(seatBid.getSeat())
                        ).findFirst().orElseThrow());

                final SeatBid tempSeatBid = improveSeatBid.toBuilder()
                        .bid(
                                getBidsForImpId(seatBidsForImp, imp)
                        ).build();
                final Bid improveBid = getBidsForImpId(improveSeatBid, imp)
                        .stream().findFirst().orElse(null);
                final Bid gVastBid = bidCreator.create(imp, tempSeatBid, improveBid);

                improveSeatBid = findOrCopySeatBidInLookupMap(lookupSeatBids, improveSeatBid);
                improveSeatBid.getBid().add(gVastBid);
            }
        }
        return bidResponse.toBuilder().seatbid(
                new ArrayList<>(lookupSeatBids.values())
        ).build();
    }

    private SeatBid findOrCopySeatBidInLookupMap(Map<String, SeatBid> lookupMap, SeatBid srcSeatBid) {
        final SeatBid seatBid = ObjectUtil.getIfNotNullOrDefault(
                lookupMap.get(srcSeatBid.getSeat()),
                sb -> sb,
                () -> srcSeatBid.toBuilder().bid(new ArrayList<>()).build()
        );

        lookupMap.put(seatBid.getSeat(), seatBid);
        return seatBid;
    }

    private List<Bid> getBidsForImpId(SeatBid seatBid, Imp imp) {
        return getBidsForImpId(List.of(seatBid), imp);
    }

    private List<Bid> getBidsForImpId(List<SeatBid> seatBids, Imp imp) {
        return seatBids.stream().flatMap(seatBid ->
                seatBid.getBid().stream().filter(bid -> bid.getImpid().equals(imp.getId())))
                .collect(Collectors.toList());
    }

    private boolean shouldProcess(Imp imp) {
        return imp.getVideo() != null && ObjectUtils.defaultIfNull(
                ObjectUtil.getIfNotNull(
                        ObjectUtil.getIfNotNull(imp, jsonUtils::getImprovedigitalPbsImpExt),
                        pbsImpExt -> pbsImpExt.getResponseType() != VastResponseType.vast
                ),
                false
        );
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-auction-response";
    }
}
