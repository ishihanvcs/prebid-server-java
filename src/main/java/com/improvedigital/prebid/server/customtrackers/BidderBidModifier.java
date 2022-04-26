package com.improvedigital.prebid.server.customtrackers;

import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.customtrackers.contracts.ITrackerMacroResolver;
import com.improvedigital.prebid.server.settings.model.CustomTracker;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;

import java.util.Collection;
import java.util.Map;
import java.util.Stack;

public class BidderBidModifier {

    private static final Logger logger = LoggerFactory.getLogger(BidderBidModifier.class);
    private final MacroProcessor macroProcessor;
    private final RequestUtils requestUtils;

    public BidderBidModifier(
            MacroProcessor macroProcessor,
            RequestUtils requestUtils
    ) {
        this.macroProcessor = macroProcessor;
        this.requestUtils = requestUtils;
    }

    public BidderBid modifyBidAdm(
            AuctionRequestModuleContext moduleContext,
            BidderBid bidderBid,
            String bidder
    ) {
        if (moduleContext == null) {
            return bidderBid;
        }

        Collection<CustomTracker> customTrackers = moduleContext.getCustomTrackers();
        if (customTrackers == null || customTrackers.isEmpty()) {
            logger.warn("No custom trackers are configured & enabled!");
            return bidderBid;
        }

        Bid bid = bidderBid.getBid();
        if (StringUtils.isBlank(bid.getAdm())) {
            logger.warn("Skipping bid as adm value is blank!");
            return bidderBid;
        }

        String accountId = requestUtils.getAccountId(moduleContext.getBidRequest());
        final Stack<String> admStack = new Stack<>();
        admStack.push(bid.getAdm());
        TrackerContext commonTrackerContext = TrackerContext
                .from(moduleContext)
                .with(bidderBid, bidder);
        customTrackers.forEach(customTracker -> {
            if (!customTracker.getEnabled()) {
                logger.info(String.format(
                        "Skipping Custom Tracker [id=%s] as it is disabled in configuration", customTracker.getId()
                ));
                return;
            } else if (!customTracker.getExcludedAccounts().isEmpty()
                    && StringUtils.isNotBlank(accountId)
                    && customTracker.getExcludedAccounts().contains(accountId)
            ) {
                logger.info(String.format(
                        "Skipping Custom Tracker [id=%s] as [account=%s] is excluded in configuration",
                        customTracker.getId(), accountId
                ));
                return;
            }
            try {
                final TrackerContext trackerContext = commonTrackerContext
                        .with(customTracker);
                final ITrackerMacroResolver macroResolver = trackerContext.getMacroResolver();
                final Map<String, String> macroValues = macroResolver.resolveValues(trackerContext);
                String trackingUrl = macroProcessor.process(customTracker.getUrlTemplate(), macroValues);
                if (trackingUrl != null) {
                    logger.debug(String.format("resolved trackingUrl = %s", trackingUrl));
                    admStack.push(
                            trackerContext.getInjector()
                                    .inject(trackingUrl, admStack.pop(), bidder, bidderBid.getType())
                    );
                } else {
                    logger.warn("Could not generate tracking url for bidder: " + bidder + "!");
                }
            } catch (Exception ex) {
                logger.warn(
                        String.format(
                                "Could not inject impression tag for tagType = %s",
                                customTracker.getId()
                        ), ex
                );
            }
        });

        return BidderBid.of(
                bid.toBuilder()
                        .adm(admStack.pop())
                        .build(),
                bidderBid.getType(),
                bidderBid.getBidCurrency()
        );
    }
}
