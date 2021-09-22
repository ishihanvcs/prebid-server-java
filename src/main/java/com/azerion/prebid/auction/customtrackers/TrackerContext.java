package com.azerion.prebid.auction.customtrackers;

import com.azerion.prebid.auction.customtrackers.contracts.ITrackerInjector;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.settings.model.CustomTracker;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

import java.util.function.Supplier;

@Builder(toBuilder = true)
@Value
@AllArgsConstructor
public class TrackerContext {

    CustomTracker tracker;
    ApplicationContext applicationContext;
    BidRequest bidRequest;
    BidResponse bidResponse;
    SeatBid seatBid;
    Bid bid;
    BidType bidType;
    Account account;
    HttpRequestContext httpRequest;
    UidsCookie uidsCookie;

    private <T> T resolveBean(Supplier<String> getter, String defaultBeanName, Class<T> beanClass) {
        if (tracker == null || applicationContext == null) {
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
