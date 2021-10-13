package com.azerion.prebid.customtrackers.contracts;

import com.azerion.prebid.customtrackers.TrackerContext;

public interface ITrackingUrlResolver {

    String resolve(TrackerContext context);
}
