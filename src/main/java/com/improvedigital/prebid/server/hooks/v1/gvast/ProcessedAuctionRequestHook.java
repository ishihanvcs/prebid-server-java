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
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
        if (invocationContext.moduleContext() == null) {
            GVastHooksModuleContext context = createModuleContext(bidRequest);
            context = context.with(bidRequest);
            BidRequest updatedBidRequest = updateImpsWithBidFloor(context);
            context = context.with(updatedBidRequest);
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> AuctionRequestPayloadImpl.of(updatedBidRequest), context
            ));
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, invocationContext.moduleContext()
        ));
    }

    private GVastHooksModuleContext createModuleContext(BidRequest bidRequest) {
        final Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt = new HashMap<>();
        for (final Imp imp : bidRequest.getImp()) {
            impIdToPbsImpExt.put(imp.getId(), jsonUtils.getImprovedigitalPbsImpExt(imp));
        }
        return GVastHooksModuleContext.from(impIdToPbsImpExt);
    }

    public BidRequest updateImpsWithBidFloor(GVastHooksModuleContext context) {
        BidRequest bidRequest = context.getBidRequest();
        bidRequest.getImp().replaceAll(imp -> {
            BigDecimal bidFloorInUsd = this.getBidFloorInUsd(
                    imp, context
            );
            if (bidFloorInUsd == null) {
                return imp;
            }
            return imp.toBuilder()
                    .bidfloor(bidFloorInUsd)
                    .bidfloorcur("USD")
                    .build();
        });
        return bidRequest;
    }

    private BigDecimal getBidFloorInUsd(Imp imp, GVastHooksModuleContext context) {
        BidRequest bidRequest = context.getBidRequest();
        Geo geoInfo = ObjectUtil.getIfNotNull(bidRequest.getDevice(), Device::getGeo);
        Floor floor = ObjectUtil.getIfNotNull(context.getPbsImpExt(imp), pie -> pie.getFloor(geoInfo));
        BigDecimal bidFloor = ObjectUtils.defaultIfNull(
                imp.getBidfloor(),
                ObjectUtil.getIfNotNull(floor, Floor::getBidFloor)
        );
        if (bidFloor == null) {
            return null;
        }
        String bidFloorCur = ObjectUtils.defaultIfNull(
                ObjectUtils.defaultIfNull(
                        imp.getBidfloorcur(),
                        ObjectUtil.getIfNotNull(
                                floor, Floor::getBidFloorCur
                        )
                ),
                ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
        );

        if (bidFloor.doubleValue() <= 0.0
                || StringUtils.compareIgnoreCase("USD", bidFloorCur) == 0) {
            return bidFloor;
        }
        return currencyConversionService.convertCurrency(
                bidFloor, bidRequest,
                "USD",
                bidFloorCur
        );
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-processed-auction-request";
    }
}
