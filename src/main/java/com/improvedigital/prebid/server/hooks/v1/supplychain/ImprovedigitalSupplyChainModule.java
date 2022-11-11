package com.improvedigital.prebid.server.hooks.v1.supplychain;

import com.improvedigital.prebid.server.utils.RequestUtils;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.settings.ApplicationSettings;

import java.util.Arrays;
import java.util.Collection;

/**
 * Module as part of: https://azerion-tech.atlassian.net/browse/HBT-241
 */
public class ImprovedigitalSupplyChainModule implements Module {

    public static final String CODE = "improvedigital-supplychain-module";

    private final RequestUtils requestUtils;

    private final ApplicationSettings applicationSettings;

    public ImprovedigitalSupplyChainModule(RequestUtils requestUtils, ApplicationSettings applicationSettings) {
        this.requestUtils = requestUtils;
        this.applicationSettings = applicationSettings;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new ProcessedAuctionRequestHook(requestUtils, applicationSettings),
                new BidderRequestHook(requestUtils.getJsonUtils())
        );
    }
}
