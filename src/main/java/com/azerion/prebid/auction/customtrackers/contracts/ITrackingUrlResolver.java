package com.azerion.prebid.auction.customtrackers.contracts;

import com.azerion.prebid.auction.customtrackers.TrackerContext;

public interface ITrackingUrlResolver {

    String resolve(TrackerContext context);
}
