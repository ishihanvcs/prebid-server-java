package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.auction.model.Floor;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.gvast.GVastHooksModuleContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GVastHookUtils {

    private static final Logger logger = LoggerFactory.getLogger(GVastHookUtils.class);

    private static final String DEFAULT_PRICE_GRANULARITY = "{\"precision\":2,\"ranges\":"
            + "[{\"max\":2,\"increment\":0.01},{\"max\":5,\"increment\":0.05},{\"max\":10,\"increment\":0.1},"
            + "{\"max\":40,\"increment\":0.5},{\"max\":100,\"increment\":1}]}";

    private final RequestUtils requestUtils;
    private final JsonUtils jsonUtils;
    private final ExtRequest defaultExtRequest;
    private final ExtRequest prioritizedExtRequestForGVast;
    private final JsonMerger merger;
    private final CurrencyConversionService currencyConversionService;

    public GVastHookUtils(
            RequestUtils requestUtils,
            JsonMerger merger,
            CurrencyConversionService currencyConversionService
    ) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
        this.merger = Objects.requireNonNull(merger);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);

        JsonNode priceGranularity;
        try {
            priceGranularity = jsonUtils.getObjectMapper().readTree(DEFAULT_PRICE_GRANULARITY);
        } catch (JsonProcessingException e) {
            logger.warn("Unable to parse priceGranularity: " + e.getMessage(), e);
            priceGranularity = null;
        }

        this.defaultExtRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null,
                        ExtRequestPrebidCacheVastxml.of(null, true),
                        false))
                .targeting(ExtRequestTargeting.builder()
                        .includebidderkeys(true)
                        .includeformat(true)
                        .includewinners(true)
                        .pricegranularity(priceGranularity)
                        .build()
                ).build()
        );

        // Set attributes that needs to be prioritized for gvast, over attributes specified in
        // client provided bid request.
        // Currently, only ext.prebid.targeting.includebidderkeys is set to true
        this.prioritizedExtRequestForGVast = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder()
                        .includebidderkeys(true)
                        .build()
                ).build()
        );
    }

    public List<SeatBid> findSeatBidsForImp(BidResponse bidResponse, Imp imp) {
        return bidResponse.getSeatbid().stream()
                .filter(seatBid -> seatBid.getBid().stream()
                        .anyMatch(bid -> Objects.equals(bid.getImpid(), imp.getId()))
                ).collect(Collectors.toList());
    }

    public SeatBid findOrCreateSeatBid(String name, BidResponse bidResponse, Imp imp) {
        return findOrCreateSeatBid(name, findSeatBidsForImp(bidResponse, imp));
    }

    public SeatBid findOrCreateSeatBid(String name, List<SeatBid> seatBidsForImp) {
        return seatBidsForImp.stream()
                .filter(seatBid ->
                        name.equals(seatBid.getSeat())
                ).findFirst().orElse(
                        SeatBid.builder()
                                .seat(name)
                                .bid(new ArrayList<>())
                                .build()
                );
    }

    public List<Bid> getBidsForImpId(SeatBid seatBid, Imp imp) {
        return getBidsForImpId(List.of(seatBid), imp);
    }

    public List<Bid> getBidsForImpId(List<SeatBid> seatBids, Imp imp) {
        return seatBids.stream().flatMap(seatBid ->
                        seatBid.getBid().stream().filter(bid ->
                                bid.getImpid().equals(imp.getId())
                        )
                ).collect(Collectors.toList());
    }

    public GVastHooksModuleContext createModuleContext(BidRequest bidRequest) {
        final Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt = new HashMap<>();
        Geo geoInfo = ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getGeo);
        Map<String, Floor> impIdToEffectiveFloor = new HashMap<>();
        boolean enableCache = false;
        boolean hasGVastImp = false;
        for (final Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();
            final ImprovedigitalPbsImpExt pbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
            if (!enableCache && requestUtils.isNonVastVideo(imp, pbsImpExt)) {
                enableCache = true;
                if (requestUtils.hasGVastResponseType(pbsImpExt)) {
                    hasGVastImp = true;
                }
            }
            impIdToPbsImpExt.put(impId, pbsImpExt);
            impIdToEffectiveFloor.put(impId, computeEffectiveFloor(imp, pbsImpExt, geoInfo));
        }

        GVastHooksModuleContext context = GVastHooksModuleContext
                .from(impIdToPbsImpExt)
                .with(impIdToEffectiveFloor);

        updateImpsWithBidFloorInUsd(bidRequest, context::getEffectiveFloor);

        if (enableCache) {
            bidRequest = updateExtWithCacheSettings(bidRequest, hasGVastImp);
        }

        return context.with(bidRequest);
    }

    private void updateImpsWithBidFloorInUsd(BidRequest bidRequest, Function<Imp, Floor> floorRetriever) {
        bidRequest.getImp().replaceAll(imp -> {
            Floor effectiveFloor = floorRetriever.apply(imp);
            if (effectiveFloor == null) {
                return imp;
            }
            final BigDecimal bidFloorInUsd;
            if (StringUtils.compareIgnoreCase("USD", effectiveFloor.getBidFloorCur()) == 0) {
                bidFloorInUsd = effectiveFloor.getBidFloor().doubleValue() < 0.0
                        ? BigDecimal.ZERO
                        : effectiveFloor.getBidFloor();
            } else {
                bidFloorInUsd = currencyConversionService.convertCurrency(
                        effectiveFloor.getBidFloor(), bidRequest,
                        effectiveFloor.getBidFloorCur(),
                        "USD"
                );
            }

            if (bidFloorInUsd == null) {
                return imp;
            }
            return imp.toBuilder()
                    .bidfloor(bidFloorInUsd)
                    .bidfloorcur("USD")
                    .build();
        });
    }

    private BidRequest updateExtWithCacheSettings(BidRequest bidRequest, boolean hasGVastImp) {
        ExtRequest mergedExtRequest = merger.merge(
                bidRequest.getExt(),
                defaultExtRequest,
                ExtRequest.class
        );

        if (hasGVastImp) {
            mergedExtRequest = merger.merge(
                    prioritizedExtRequestForGVast,
                    mergedExtRequest,
                    ExtRequest.class
            );
        }
        return bidRequest.toBuilder().ext(mergedExtRequest).build();
    }

    private Floor computeEffectiveFloor(Imp imp, ImprovedigitalPbsImpExt pbsImpExt, Geo geo) {
        Floor floor = ObjectUtil.getIfNotNull(pbsImpExt, pie -> pie.getFloor(geo));
        BigDecimal effectiveFloorPrice = getEffectiveFloorPrice(imp, floor);
        if (effectiveFloorPrice == null) {
            return floor;
        }
        String effectiveFloorCur = getEffectiveFloorCur(imp, floor);
        return Floor.of(effectiveFloorPrice, effectiveFloorCur);
    }

    private String getEffectiveFloorCur(Imp imp, Floor floor) {
        if (imp.getBidfloorcur() != null) {
            return imp.getBidfloorcur();
        }

        if (imp.getBidfloor() != null) {
            return "USD"; // default in ortb spec
        }

        return ObjectUtils.defaultIfNull(
                ObjectUtil.getIfNotNull(
                        floor, Floor::getBidFloorCur
                ),
                ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
        );
    }

    private BigDecimal getEffectiveFloorPrice(Imp imp, Floor floor) {
        return ObjectUtils.defaultIfNull(
                imp.getBidfloor(),
                ObjectUtil.getIfNotNull(
                        floor, Floor::getBidFloor
                )
        );
    }
}
