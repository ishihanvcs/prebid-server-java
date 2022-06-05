package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.auction.model.Floor;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);
    private final ObjectMapper mapper;
    private final CurrencyConversionService currencyConversionService;
    private final JsonUtils jsonUtils;

    public ProcessedAuctionRequestHook(
            JsonUtils jsonUtils,
            CurrencyConversionService currencyConversionService
    ) {
        this.jsonUtils = jsonUtils;
        this.mapper = jsonUtils.getObjectMapper();
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        BidRequest bidRequest = auctionRequestPayload.bidRequest();
        Object moduleContext = invocationContext.moduleContext();
        if (moduleContext == null) {
            GVastHooksModuleContext context = createModuleContext(bidRequest);
            updateImpsWithBidFloorInUsd(bidRequest, context::getEffectiveFloor);
            moduleContext = context;
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, moduleContext
        ));
    }

    private GVastHooksModuleContext createModuleContext(BidRequest bidRequest) {
        final Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt = new HashMap<>();
        Geo geoInfo = ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getGeo);
        Map<String, Floor> impIdToEffectiveFloor = new HashMap<>();
        for (final Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();
            final ImprovedigitalPbsImpExt pbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
            impIdToPbsImpExt.put(impId, pbsImpExt);
            impIdToEffectiveFloor.put(impId, computeEffectiveFloor(imp, pbsImpExt, geoInfo));
        }
        return GVastHooksModuleContext
                        .from(impIdToPbsImpExt)
                        .with(impIdToEffectiveFloor)
                        .with(bidRequest);
    }

    public void updateImpsWithBidFloorInUsd(BidRequest bidRequest, Function<Imp, Floor> floorRetriever) {
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
                        "USD",
                        effectiveFloor.getBidFloorCur()
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
