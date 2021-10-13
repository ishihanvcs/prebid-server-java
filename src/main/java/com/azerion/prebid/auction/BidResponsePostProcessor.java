package com.azerion.prebid.auction;

import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

import java.util.Objects;

public class BidResponsePostProcessor implements org.prebid.server.auction.BidResponsePostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BidResponsePostProcessor.class);

    private final ApplicationContext applicationContext;

    private final BidderBidModifier bidderBidModifier;

    public BidResponsePostProcessor(
            ApplicationContext applicationContext,
            BidderBidModifier bidderBidModifier) {
        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.bidderBidModifier = Objects.requireNonNull(bidderBidModifier);
    }

    @Override
    public Future<BidResponse> postProcess(
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie,
            BidRequest bidRequest,
            BidResponse bidResponse,
            Account account) {
        // BidRequestContext bidRequestContext = BidRequestContext.from(
        //         applicationContext,
        //         bidRequest,
        //         account,
        //         httpRequest,
        //         uidsCookie
        // );
        // bidResponseModifier.apply(bidRequestContext);
        return Future.succeededFuture(bidResponse);
    }
}
