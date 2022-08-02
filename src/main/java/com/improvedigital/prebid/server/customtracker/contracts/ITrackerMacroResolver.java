package com.improvedigital.prebid.server.customtracker.contracts;

import com.improvedigital.prebid.server.customtracker.model.TrackerContext;

import java.util.Map;

public interface ITrackerMacroResolver {

    Map<String, String> resolveValues(TrackerContext context) throws Exception;
}
