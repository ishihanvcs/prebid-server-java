package com.improvedigital.prebid.server.customtracker.model;

import com.improvedigital.prebid.server.customtracker.contracts.ITrackerInjector;
import com.improvedigital.prebid.server.customtracker.contracts.ITrackerMacroResolver;
import com.improvedigital.prebid.server.settings.model.CustomTracker;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;

import java.util.function.Supplier;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
public class TrackerContext extends ModuleContext {

    CustomTracker tracker;
    BidderBid bidderBid;
    String bidder;

    public static TrackerContext from(ModuleContext moduleContext) {
        return TrackerContext
                .builder()
                .applicationContext(moduleContext.applicationContext)
                .bidRequest(moduleContext.bidRequest)
                .build();
    }

    public TrackerContext with(CustomTracker tracker) {
        return this.toBuilder().tracker(tracker).build();
    }

    public TrackerContext with(BidderBid bidderBid, String bidder) {
        return this.toBuilder().bidderBid(bidderBid).bidder(bidder).build();
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

    public ITrackerMacroResolver getMacroResolver() {
        return resolveBean(tracker::getMacroResolver, "trackerMacroResolver", ITrackerMacroResolver.class);
    }

    public ITrackerInjector getInjector() {
        return resolveBean(tracker::getInjector, "trackerInjector", ITrackerInjector.class);
    }
}
