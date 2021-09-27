package com.azerion.prebid.auction.customtrackers;

import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Stack;

public class BidResponseModifier {

    private static final Logger logger = LoggerFactory.getLogger(BidResponseModifier.class);
    private final CustomTrackerSetting customTrackerSetting;

    public BidResponseModifier(CustomTrackerSetting customTrackerSetting) {
        this.customTrackerSetting = customTrackerSetting;
    }

    private Bid injectTrackerIntoBidAdm(
            BidResponseContext bidResponseContext,
            SeatBid seatBid,
            Bid bid) {

        final BidType bidType = bidResponseContext.getBidType(bid);

        if (bidType == null || StringUtils.isBlank(bid.getAdm())) {
            logger.warn("Could not determine bidType or adm value is blank in bid!");
            return bid;
        }
        final Stack<String> admStack = new Stack<>();
        admStack.push(bid.getAdm());
        TrackerContext commonTrackerContext = TrackerContext
                .from(bidResponseContext)
                .with(seatBid, bid, bidType);
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
                                    .inject(trackingUrl, admStack.pop(), seatBid.getSeat(), bidType)
                    );
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

        return bid.toBuilder().adm(admStack.pop()).build();
    }

    private void modifyBidsInSeatBid(BidResponseContext context, SeatBid seatBid) {
        seatBid.getBid().replaceAll(bid -> injectTrackerIntoBidAdm(
                context, seatBid, bid
        ));
    }

    public void apply(BidResponseContext context) {
        if (customTrackerSetting != null && customTrackerSetting.isEnabled()) {
            context.getBidResponse().getSeatbid().forEach(seatBid -> this.modifyBidsInSeatBid(context, seatBid));
        }
    }
}
