package com.improvedigital.prebid.server.customtrackers.contracts;

import com.improvedigital.prebid.server.customtrackers.TrackerContext;

import java.util.Map;

public interface ITrackerMacroResolver {

    Map<String, String> resolveValues(TrackerContext context) throws Exception;
}
