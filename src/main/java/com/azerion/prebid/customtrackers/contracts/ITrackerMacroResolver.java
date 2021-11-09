package com.azerion.prebid.customtrackers.contracts;

import com.azerion.prebid.customtrackers.TrackerContext;

import java.util.Map;

public interface ITrackerMacroResolver {

    Map<String, String> resolveValues(TrackerContext context) throws Exception;
}
