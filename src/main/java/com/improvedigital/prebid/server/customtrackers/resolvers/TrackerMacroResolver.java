package com.improvedigital.prebid.server.customtrackers.resolvers;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.customtrackers.TrackerContext;
import com.improvedigital.prebid.server.customtrackers.contracts.ITrackerMacroResolver;
import com.improvedigital.prebid.server.utils.FluentMap;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

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
        final String placementId = resolvePlacementId(context);
        final BidderBid bidderBid = context.getBidderBid();
        final Bid bid = bidderBid.getBid();
        final String bidType = bidderBid.getType().getName();
        final String bidder = context.getBidder();

        /**
         * HBT-207: {@link org.prebid.server.auction.ExchangeService}'s method updateBidderBidWithBidPriceChanges()
         * has a bug where it updates the price ({@link Bid#price}) after currency conversion but does not
         * update the currency ({@link BidderBid#bidCurrency}) accordingly :(
         *
         * So, we are using the "bid.ext.origbidcpm" and "bid.ext.origbidcur" for our calculation atomically.
         * Meaning, if we do not get any of those, we use {@link Bid#price} and adserver-currency both.
         * This is because, we do not want to use ("bid.ext.origbidcpm" and adserver-currency) pair or
         * ({@link Bid#price} and "bid.ext.origbidcur") pair.
         *
         * Note: at some point, prebid java team might be fixing the bug and we should revisit this code later.
         */
        BigDecimal bidPrice = this.jsonUtils.getBigDecimalAt(bid.getExt(), "/origbidcpm");
        String bidCurrency = this.jsonUtils.getStringAt(bid.getExt(), "/origbidcur");
        if (bidPrice == null || bidCurrency == null) {
            logger.warn("Cannot find bid.ext.origbidcpm or bid.ext.origbidcur. Ext={0}. Falling back..", bid.getExt());
            bidPrice = bid.getPrice();
            bidCurrency = context.getBidRequest().getCur().get(0);
        }

        final BigDecimal bidPriceUsd = currencyConversionService.convertCurrency(
                bidPrice, context.getBidRequest(),
                bidCurrency,
                "USD"
        );

        return FluentMap.<String, String>create()
                .put("bid_type", bidType)
                .put("bidder", bidder)
                .put("bid_price", bidPrice.toPlainString())
                .put("bid_currency", bidCurrency)
                .put("bid_price_usd", bidPriceUsd.toPlainString())
                .put("improve_digital_placement_id", placementId)
                .result();
    }

    protected String resolvePlacementId(TrackerContext context) throws Exception {
        final BidRequest bidRequest = context.getBidRequest();
        final String impId = context.getBidderBid().getBid().getImpid();
        final Imp imp = bidRequest.getImp().stream()
                .filter(i -> i.getId().equals(impId))
                .findFirst().orElse(null);

        if (imp == null) {
            throw new Exception("Cannot find matching imp in request with id: " + impId);
        }

        final JsonNode placementIdNode = ObjectUtil.getIfNotNull(imp.getExt(),
                impExt -> impExt.at("/prebid/bidder/improvedigital/placementId")
        );

        if (placementIdNode == null || placementIdNode.isMissingNode()) {
            throw new Exception("imp[" + impId + "]: Improve Digital placement ID is not defined!");
        }
        return placementIdNode.asText();
    }
}