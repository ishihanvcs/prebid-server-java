package com.azerion.prebid.auction.customtrackers;

import com.azerion.prebid.auction.customtrackers.contracts.ITrackerInjector;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTracker;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.function.Supplier;

@SuperBuilder(toBuilder = true)
@Getter
public class TrackerContext extends BidResponseContext {

    CustomTracker tracker;
    SeatBid seatBid;
    Bid bid;
    BidType bidType;

    public static TrackerContext from(BidResponseContext bidResponseContext) {
        return TrackerContext
                .builder()
                .applicationContext(bidResponseContext.applicationContext)
                .bidRequest(bidResponseContext.bidRequest)
                .bidResponse(bidResponseContext.bidResponse)
                .account(bidResponseContext.account)
                .httpRequest(bidResponseContext.httpRequest)
                .uidsCookie(bidResponseContext.uidsCookie)
                .build();
    }

    public TrackerContext with(CustomTracker tracker) {
        return this.toBuilder().tracker(tracker).build();
    }

    public TrackerContext with(SeatBid seatBid, Bid bid, BidType bidType) {
        return this.toBuilder().seatBid(seatBid).bid(bid).bidType(bidType).build();
    }

    private <T> T resolveBean(Supplier<String> getter, String defaultBeanName, Class<T> beanClass) {
        if (tracker == null) {
            return null;
        }
        String beanName = getter.get();
        if (StringUtils.isBlank(beanName)) {
            beanName = defaultBeanName;
            if (applicationContext.containsBean(beanName + tracker.getId())) {
                beanName = beanName + tracker.getId();
            }
        }
        return applicationContext.getBean(beanName, beanClass);
    }

    public ITrackingUrlResolver getUrlResolver() {
        return resolveBean(tracker::getUrlResolver, "trackingUrlResolver", ITrackingUrlResolver.class);
    }

    public ITrackerInjector getInjector() {
        return resolveBean(tracker::getInjector, "trackerInjector", ITrackerInjector.class);
    }
}
