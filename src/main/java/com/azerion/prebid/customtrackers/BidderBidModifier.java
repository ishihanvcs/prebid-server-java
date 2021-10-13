package com.azerion.prebid.customtrackers;

import com.azerion.prebid.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import com.iab.openrtb.response.Bid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;

import java.util.Stack;

public class BidderBidModifier {

    private static final Logger logger = LoggerFactory.getLogger(BidderBidModifier.class);
    private final CustomTrackerSetting customTrackerSetting;

    public BidderBidModifier(CustomTrackerSetting customTrackerSetting) {
        this.customTrackerSetting = customTrackerSetting;
    }

    public BidderBid modifyBidAdm(
            BidRequestContext bidRequestContext,
            BidderBid bidderBid,
            String bidder
    ) {
        if (bidRequestContext == null
                || customTrackerSetting == null
                || !customTrackerSetting.isEnabled()
        ) {
            return bidderBid;
        }

        Bid bid = bidderBid.getBid();
        if (StringUtils.isBlank(bid.getAdm())) {
            logger.warn("Skipping bid as adm value is blank!");
            return bidderBid;
        }
        final Stack<String> admStack = new Stack<>();
        admStack.push(bid.getAdm());
        TrackerContext commonTrackerContext = TrackerContext
                .from(bidRequestContext)
                .with(bidderBid, bidder);
        customTrackerSetting.forEach(customTracker -> {
            try {
                TrackerContext trackerContext = commonTrackerContext
                        .with(customTracker);
                ITrackingUrlResolver urlResolver = trackerContext.getUrlResolver();
                String trackingUrl = urlResolver.resolve(trackerContext);
                if (trackingUrl != null) {
                    logger.info(String.format("resolved trackingUrl = %s", trackingUrl));
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
