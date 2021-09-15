package com.azerion.prebid.auction.customtrackers;

import com.azerion.prebid.auction.customtrackers.contracts.ITrackerInjector;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTracker;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

// @Builder(toBuilder = true)
@Value
public class TrackerContext {

    CustomTracker tracker;
    ApplicationContext applicationContext;
    HttpRequestContext httpRequest;
    UidsCookie uidsCookie;
    BidRequest bidRequest;
    BidResponse bidResponse;
    SeatBid seatBid;
    Bid bid;
    BidType bidType;
    Account account;

    public ITrackingUrlResolver getUrlResolver() {
        String beanName = this.tracker.getUrlResolver();
        if (StringUtils.isBlank(beanName)) {
            beanName = "trackingUrlResolver";
            if (applicationContext.containsBean(beanName + this.tracker.getId())) {
                beanName = beanName + this.tracker.getId();
            }
        }
        return applicationContext.getBean(beanName, ITrackingUrlResolver.class);
    }

    public ITrackerInjector getInjector() {
        String beanName = this.tracker.getInjector();
        if (StringUtils.isBlank(beanName)) {
            beanName = "trackerInjector";
            if (applicationContext.containsBean(beanName + this.tracker.getId())) {
                beanName = beanName + this.tracker.getId();
            }
        }
        return applicationContext.getBean(beanName, ITrackerInjector.class);
    }
}
