package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.auction.model.Floor;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final String DEFAULT_PRICE_GRANULARITY = "{\"precision\":2,\"ranges\":"
            + "[{\"max\":2,\"increment\":0.01},{\"max\":5,\"increment\":0.05},{\"max\":10,\"increment\":0.1},"
            + "{\"max\":40,\"increment\":0.5},{\"max\":100,\"increment\":1}]}";

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);
    private final CurrencyConversionService currencyConversionService;
    private final JsonUtils jsonUtils;
    private final ExtRequest defaultExtRequest;
    private final RequestUtils requestUtils;
    private final JsonMerger merger;
    private final ExtRequest prioritizedExtRequestForGVast;

    public ProcessedAuctionRequestHook(
            JsonMerger merger,
            RequestUtils requestUtils,
            CurrencyConversionService currencyConversionService
    ) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.merger = Objects.requireNonNull(merger);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
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

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        BidRequest bidRequest = auctionRequestPayload.bidRequest();
        try {
            validateRequestWithBusinessLogic(bidRequest);
            Object moduleContext = invocationContext.moduleContext();
            if (moduleContext == null) {
                GVastHooksModuleContext context = createModuleContext(bidRequest);
                updateImpsWithBidFloorInUsd(bidRequest, context::getEffectiveFloor);
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

    private GVastHooksModuleContext createModuleContext(BidRequest bidRequest) {
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
        if (enableCache) {
            bidRequest = updateExtWithCacheSettings(bidRequest, hasGVastImp);
        }
        return GVastHooksModuleContext
                        .from(impIdToPbsImpExt)
                        .with(impIdToEffectiveFloor)
                        .with(bidRequest);
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

    private void updateImpsWithBidFloorInUsd(BidRequest bidRequest, Function<Imp, Floor> floorRetriever) {
        bidRequest.getImp().replaceAll(imp -> {
            Floor effectiveFloor = floorRetriever.apply(imp);
            if (effectiveFloor == null) {
                return imp;
            }
            final BigDecimal bidFloorInUsd;
            if (StringUtils.compareIgnoreCase("USD", effectiveFloor.getBidFloorCur()) == 0) {
                bidFloorInUsd = effectiveFloor.getBidFloor().doubleValue() <= 0.0
                        ? BigDecimal.valueOf(0.0)
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

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-processed-auction-request";
    }
}
