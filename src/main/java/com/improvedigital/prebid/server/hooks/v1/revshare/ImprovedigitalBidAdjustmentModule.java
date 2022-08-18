package com.improvedigital.prebid.server.hooks.v1.revshare;

import com.improvedigital.prebid.server.utils.JsonUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.settings.ApplicationSettings;

import java.util.Arrays;
import java.util.Collection;

/**
 * Module as part of: https://azerion-tech.atlassian.net/browse/HBT-251
 */
public class ImprovedigitalBidAdjustmentModule implements Module {

    public static final String CODE = "improvedigital-bidadjustment-module";

    private final JsonUtils jsonUtils;

    private final BidderCatalog bidderCatalog;

    private final ApplicationSettings applicationSettings;

    public ImprovedigitalBidAdjustmentModule(
            JsonUtils jsonUtils, BidderCatalog bidderCatalog, ApplicationSettings applicationSettings) {
        this.jsonUtils = jsonUtils;
        this.bidderCatalog = bidderCatalog;
        this.applicationSettings = applicationSettings;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new ProcessedAuctionRequestHook(jsonUtils, bidderCatalog, applicationSettings)
        );
    }
}
