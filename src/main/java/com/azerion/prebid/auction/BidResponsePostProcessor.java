package com.azerion.prebid.auction;

import com.azerion.prebid.auction.customtrackers.BidResponseModifier;
import com.azerion.prebid.auction.customtrackers.BidResponseModifierContext;
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

    private final BidResponseModifier bidResponseModifier;

    public BidResponsePostProcessor(
            ApplicationContext applicationContext,
            BidResponseModifier bidResponseModifier) {
        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.bidResponseModifier = Objects.requireNonNull(bidResponseModifier);
    }

    @Override
    public Future<BidResponse> postProcess(
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie,
            BidRequest bidRequest,
            BidResponse bidResponse,
            Account account) {
        bidResponseModifier.apply(
                BidResponseModifierContext
                .builder()
                .applicationContext(applicationContext)
                .bidRequest(bidRequest)
                .bidResponse(bidResponse)
                .account(account)
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookie)
                .build()
        );
        return Future.succeededFuture(bidResponse);
    }
}
