package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.model.ImprovedigitalPbsAccountExt;
import com.improvedigital.prebid.server.utils.LogMessage;
import com.improvedigital.prebid.server.utils.RequestUtils;
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

    private final RequestUtils requestUtils;

    private final BidderCatalog bidderCatalog;

    private final ApplicationSettings applicationSettings;

    public ProcessedAuctionRequestHook(
            RequestUtils requestUtils, BidderCatalog bidderCatalog, ApplicationSettings applicationSettings) {
        this.requestUtils = requestUtils;
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

        String accountId = requestUtils.getAccountId(auctionRequestPayload.bidRequest());
        if (StringUtils.isEmpty(accountId)) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        return applicationSettings.getAccountById(accountId, invocationContext.timeout()).map(account -> {
            if (!hasPriceFloorsTurnedOn(account)) {
                logger.warn(LogMessage
                        .from(auctionRequestPayload.bidRequest())
                        .withFrequency(1000)
                        .withMessage(String.format("Account %s has auction.price-floors.enabled=false."
                                + " Not applying bid adjustment factors", account.getId()))
                );

                return InvocationResultImpl.succeeded(
                        payload -> auctionRequestPayload, invocationContext.moduleContext()
                );
            }

            ImprovedigitalPbsAccountExt accExt = requestUtils.getJsonUtils().getAccountExt(account);
            if (accExt == null || accExt.getBidPriceAdjustment() == null) {
                return InvocationResultImpl.succeeded(
                        payload -> auctionRequestPayload, invocationContext.moduleContext()
                );
            }

            final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder().build();
            bidderCatalog.names().stream()
                    .filter(b -> canIncludeBidderForBidAdjustment(accExt, b))
                    .forEach(b -> factors.addFactor(b, accExt.getBidPriceAdjustmentRounded()));

            return InvocationResultImpl.succeeded(
                    payload -> AuctionRequestPayloadImpl.of(auctionRequestPayload.bidRequest().toBuilder()
                            .ext(ExtRequest.of(auctionRequestPayload.bidRequest().getExt().getPrebid().toBuilder()
                                    .bidadjustmentfactors(factors)
                                    .build()))
                            .build()
                    ), invocationContext.moduleContext()
            );
        });
    }

    private boolean canIncludeBidderForBidAdjustment(ImprovedigitalPbsAccountExt accExt, String bidderName) {
        if (!bidderCatalog.isActive(bidderName)) {
            return false;
        }

        if (RequestUtils.IMPROVE_BIDDER_NAME.equalsIgnoreCase(bidderName)) {
            return accExt.shouldIncludeImproveForAdjustment();
        }

        return true;
    }

    public boolean hasPriceFloorsTurnedOn(Account account) {
        // We treat missing value as true.
        if (account == null || account.getAuction() == null || account.getAuction().getPriceFloors() == null) {
            return true;
        }
        Boolean isEnabled = account.getAuction().getPriceFloors().getEnabled();
        return isEnabled == null ? true : isEnabled.booleanValue();
    }
}

