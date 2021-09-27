package com.azerion.prebid.auction.customtrackers.resolvers;

import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.auction.customtrackers.TrackerContext;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.currency.CurrencyConversionService;

import java.util.Map;
import java.util.Objects;

public class TrackingUrlResolver implements ITrackingUrlResolver {

    private static final Logger logger = LoggerFactory.getLogger(TrackingUrlResolver.class);
    protected final CurrencyConversionService currencyConversionService;

    public TrackingUrlResolver(
            CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
    }

    @Override
    public String resolve(TrackerContext context) {
        Map<String, String> params = resolveUrlParams(context);
        return constructUrl(context, params);
    }

    protected String constructUrl(TrackerContext context, Map<String, String> params) {
        try {
            URIBuilder b = new URIBuilder(context.getTracker().getBaseUrl());
            params.forEach(b::addParameter);
            return b.build().toURL().toExternalForm();
        } catch (Exception ignored) { }
        return null;
    }

    protected Map<String, String> resolveUrlParams(TrackerContext context) {
        Map<String, String> params = new HashedMap<>();
        BidRequest bidRequest = context.getBidRequest();
        params.put("adType", context.getBidType().getName());
        params.put("bidder", context.getSeatBid().getSeat());
        params.put("price", currencyConversionService
                .convertCurrency(
                    context.getBid().getPrice(),
                    bidRequest,
                    context.getBidResponse().getCur(),
                    context.getTracker().getCurrency()
                ).toString()
        );

        if (context.getPlacement() != null) {
            params.put("pid", context.getPlacement().getId());
        } else {
            bidRequest.getImp().stream()
                    .filter(imp -> !imp.getExt()
                            .path("prebid")
                            .path("bidder")
                            .path("improvedigital")
                            .path("placementId")
                            .isMissingNode()
                    )
                    .map(imp -> imp.getExt()
                            .get("prebid")
                            .get("bidder")
                            .get("improvedigital")
                    )
                    .findFirst()
                    .ifPresent(idExt ->
                            params.put("pid", String.valueOf(idExt.get("placementId").asLong())));
        }

        return params;
    }
}
