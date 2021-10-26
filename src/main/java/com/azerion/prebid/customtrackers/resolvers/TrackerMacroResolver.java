package com.azerion.prebid.customtrackers.resolvers;

import com.azerion.prebid.customtrackers.TrackerContext;
import com.azerion.prebid.customtrackers.contracts.ITrackerMacroResolver;
import com.azerion.prebid.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.map.HashedMap;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TrackerMacroResolver implements ITrackerMacroResolver {

    private static final Logger logger = LoggerFactory.getLogger(TrackerMacroResolver.class);
    private final CurrencyConversionService currencyConversionService;
    private final JsonUtils jsonUtils;

    public TrackerMacroResolver(
            CurrencyConversionService currencyConversionService,
            JacksonMapper mapper
    ) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.jsonUtils = new JsonUtils(mapper);
    }

    @Override
    public Map<String, String> resolveValues(TrackerContext context) throws Exception {
        final Map<String, String> macroValues = new HashedMap<>();

        final String placementId = resolvePlacementId(context);
        final BidderBid bidderBid = context.getBidderBid();
        final Bid bid = bidderBid.getBid();
        final BigDecimal bidPrice = bid.getPrice();
        final String bidCurrency = bidderBid.getBidCurrency();
        final String bidType = bidderBid.getType().getName();
        final String bidder = context.getBidder();
        final BigDecimal bidPriceUsd = currencyConversionService.convertCurrency(
                bidPrice, context.getBidRequest(),
                "USD",
                bidCurrency
        );

        macroValues.put("bid_type", bidType);
        macroValues.put("bidder", bidder);
        macroValues.put("bid_price", bidPrice.toPlainString());
        macroValues.put("bid_currency", bidCurrency);
        macroValues.put("bid_price_usd", bidPriceUsd.toPlainString());
        macroValues.put("improve_digital_placement_id", placementId);

        return macroValues;
    }

    protected String resolvePlacementId(TrackerContext context) throws Exception {
        if (context.getPlacement() != null) {
            return context.getPlacement().getId();
        }

        final BidRequest bidRequest = context.getBidRequest();
        final JsonNode placementIdNode = jsonUtils.findFirstNode(
                bidRequest.getImp().stream()
                        .map(Imp::getExt).collect(Collectors.toList()),
                "prebid/bidder/improvedigital/placementId"
        );

        if (placementIdNode == null) {
            throw new Exception("improve_digital_placement_id could not be resolved from bidRequest");
        }
        return placementIdNode.asText();
    }
}
