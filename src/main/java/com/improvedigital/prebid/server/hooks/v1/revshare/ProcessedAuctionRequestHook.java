package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.fasterxml.jackson.databind.JsonNode;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
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
import org.prebid.server.settings.model.ExtAccount;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

        String publisherId = requestUtils.getAccountId(auctionRequestPayload.bidRequest());
        if (StringUtils.isEmpty(publisherId)) {
            return Future.succeededFuture(InvocationResultImpl.succeeded(
                    payload -> auctionRequestPayload, invocationContext.moduleContext()
            ));
        }

        return applicationSettings.getAccountById(publisherId, invocationContext.timeout()).map(account -> {
            ExtAccount accExt = account.getExt();
            if (accExt == null || getBidPriceAdjustment(accExt) == null) {
                return InvocationResultImpl.succeeded(
                        payload -> auctionRequestPayload, invocationContext.moduleContext()
                );
            }

            final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder().build();
            bidderCatalog.names().stream()
                    .filter(b -> canIncludeBidderForBidAdjustment(accExt, b))
                    .forEach(b -> factors.addFactor(b, getBidPriceAdjustment(accExt)));

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

    private boolean canIncludeBidderForBidAdjustment(ExtAccount accExt, String bidderName) {
        if (!bidderCatalog.isActive(bidderName)) {
            return false;
        }

        if (RequestUtils.IMPROVE_BIDDER_NAME.equalsIgnoreCase(bidderName)) {
            return getBidPriceAdjustmentIncImprove(accExt);
        }

        return true;
    }

    private BigDecimal getBidPriceAdjustment(ExtAccount accExt) {
        JsonNode value = accExt.getProperty("bidPriceAdjustment");
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }

        return BigDecimal.valueOf(value.asDouble()).setScale(4, RoundingMode.HALF_EVEN);
    }

    private boolean getBidPriceAdjustmentIncImprove(ExtAccount accExt) {
        JsonNode value = accExt.getProperty("bidPriceAdjustmentIncImprove");
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false; /* By default, improve is excluded from bid adjustment. */
        }

        return value.asBoolean();
    }
}

