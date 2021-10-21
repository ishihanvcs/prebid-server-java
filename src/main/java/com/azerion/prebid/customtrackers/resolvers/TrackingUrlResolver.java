package com.azerion.prebid.customtrackers.resolvers;

import com.azerion.prebid.customtrackers.TrackerContext;
import com.azerion.prebid.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TrackingUrlResolver implements ITrackingUrlResolver {

    private static final Logger logger = LoggerFactory.getLogger(TrackingUrlResolver.class);
    private final CurrencyConversionService currencyConversionService;
    private final JsonUtils jsonUtils;

    public TrackingUrlResolver(
            CurrencyConversionService currencyConversionService,
            JacksonMapper mapper
    ) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.jsonUtils = new JsonUtils(mapper);
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
        } catch (Exception ignored) {
        }
        return null;
    }

    protected Map<String, String> resolveUrlParams(TrackerContext context) {
        Map<String, String> params = new HashedMap<>();
        BidRequest bidRequest = context.getBidRequest();
        BidderBid bidderBid = context.getBidderBid();
        Bid bid = bidderBid.getBid();
        params.put("adType", bidderBid.getType().getName());
        params.put("bidder", context.getBidder());
        params.put("price", currencyConversionService.convertCurrency(
                        bid.getPrice(), bidRequest,
                        context.getTracker().getCurrency(),
                        bidderBid.getBidCurrency()
                ).toPlainString()
        );

        if (context.getPlacement() != null) {
            params.put("pid", context.getPlacement().getId());
        } else {
            JsonNode placementIdNode = jsonUtils.findFirstNode(
                    bidRequest.getImp().stream()
                            .map(Imp::getExt).collect(Collectors.toList()),
                    "prebid/bidder/improvedigital/placementId"
            );

            if (placementIdNode != null) {
                params.put("pid", placementIdNode.asText());
            }
        }

        return params;
    }
}
