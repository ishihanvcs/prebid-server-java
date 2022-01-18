package com.improvedigital.prebid.server.hooks.v1;

import com.improvedigital.prebid.server.customtrackers.BidderBidModifier;
import com.improvedigital.prebid.server.customtrackers.hooks.v1.BidderRequestHook;
import com.improvedigital.prebid.server.customtrackers.hooks.v1.EntrypointHook;
import com.improvedigital.prebid.server.customtrackers.hooks.v1.ProcessedAuctionRequestHook;
import com.improvedigital.prebid.server.customtrackers.hooks.v1.ProcessedBidderResponseHook;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;

public class CustomTrackerModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(CustomTrackerModule.class);
    private final ApplicationContext applicationContext;
    private final SettingsLoader settingsLoader;
    private final BidderBidModifier bidderBidModifier;

    public CustomTrackerModule(
            ApplicationContext applicationContext,
            SettingsLoader settingsLoader,
            BidderBidModifier bidderBidModifier
    ) {
        this.applicationContext = applicationContext;
        this.settingsLoader = settingsLoader;
        this.bidderBidModifier = bidderBidModifier;
    }

    @Override
    public String code() {
        return "custom-tracker-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(applicationContext, settingsLoader),
                new ProcessedAuctionRequestHook(),
                new BidderRequestHook(),
                new ProcessedBidderResponseHook(bidderBidModifier)
        );
    }
}

