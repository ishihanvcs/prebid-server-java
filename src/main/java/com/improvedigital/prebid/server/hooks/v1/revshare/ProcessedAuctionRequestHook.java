package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.model.AccountExt;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    private final JsonUtils jsonUtils;

    private final BidderCatalog bidderCatalog;

    private final ApplicationSettings applicationSettings;

    public ProcessedAuctionRequestHook(
            JsonUtils jsonUtils, BidderCatalog bidderCatalog, ApplicationSettings applicationSettings) {
        this.jsonUtils = jsonUtils;
        this.bidderCatalog = bidderCatalog;
        this.applicationSettings = applicationSettings;
    }

    @Override
    public String code() {
        return "improvedigital-bidadjustment-hooks-processed-auction-request";
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {

        AccountExt accExt = getPublisherAccountExt(auctionRequestPayload.bidRequest());
        if (accExt == null || accExt.getBidPriceAdjustment() == null) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder().build();
        bidderCatalog.names().stream()
                .filter(b -> bidderCatalog.isActive(b) && (
                        !"improvedigital".equalsIgnoreCase(b) || accExt.getBidPriceAdjustmentIncImprove()
                ))
                .forEach(b -> factors.addFactor(b, accExt.getBidPriceAdjustment()));

        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> AuctionRequestPayloadImpl.of(auctionRequestPayload.bidRequest().toBuilder()
                        .ext(ExtRequest.of(auctionRequestPayload.bidRequest().getExt().getPrebid().toBuilder()
                                .bidadjustmentfactors(factors)
                                .build()))
                        .build()
                ), invocationContext.moduleContext()
        ));
    }

    private AccountExt getPublisherAccountExt(BidRequest bidRequest) {
        String publisherId = getPublisherId(bidRequest);
        if (StringUtils.isEmpty(publisherId)) {
            return null;
        }

        Account publisherAccount = applicationSettings.getAccountById(publisherId, null).result();

        if (publisherAccount == null || publisherAccount.getExt() == null) {
            return null;
        }

        return new AccountExt(jsonUtils, publisherAccount.getExt());
    }

    private String getPublisherId(BidRequest bidRequest) {
        if (bidRequest.getSite() != null && bidRequest.getSite().getPublisher() != null) {
            return bidRequest.getSite().getPublisher().getId();
        }

        if (bidRequest.getApp() != null && bidRequest.getApp().getPublisher() != null) {
            return bidRequest.getApp().getPublisher().getId();
        }

        return "";
    }
}

