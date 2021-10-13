package com.azerion.prebid.customtrackers.resolvers;

import com.azerion.prebid.customtrackers.TrackerContext;
import com.azerion.prebid.customtrackers.contracts.ITrackingUrlResolver;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.model.BidderBid;
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
        BidderBid bidderBid = context.getBidderBid();
        Bid bid = bidderBid.getBid();
        params.put("adType", bidderBid.getType().getName());
        params.put("bidder", context.getBidder());
        params.put("price", currencyConversionService
                .convertCurrency(
                    bid.getPrice(),
                    bidRequest,
                        bidderBid.getBidCurrency(),
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
