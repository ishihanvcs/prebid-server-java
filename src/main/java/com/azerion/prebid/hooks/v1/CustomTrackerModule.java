package com.azerion.prebid.hooks.v1;

import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.azerion.prebid.customtrackers.hooks.v1.EntrypointHook;
import com.azerion.prebid.customtrackers.hooks.v1.ProcessedAuctionRequestHook;
import com.azerion.prebid.customtrackers.hooks.v1.ProcessedBidderResponseHook;
import com.azerion.prebid.settings.SettingsLoader;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collection;

public class CustomTrackerModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(CustomTrackerModule.class);
    private final ApplicationContext applicationContext;
    private final SettingsLoader settingsLoader;
    private final BidderBidModifier bidderBidModifier;
    private final JacksonMapper mapper;

    public CustomTrackerModule(
            ApplicationContext applicationContext,
            SettingsLoader settingsLoader,
            BidderBidModifier bidderBidModifier,
            JacksonMapper mapper
    ) {
        this.applicationContext = applicationContext;
        this.settingsLoader = settingsLoader;
        this.bidderBidModifier = bidderBidModifier;
        this.mapper = mapper;
    }

    @Override
    public String code() {
        return "custom-tracker-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(
                        applicationContext,
                        settingsLoader
                ),
                new ProcessedAuctionRequestHook(),
                new ProcessedBidderResponseHook(
                        settingsLoader,
                        bidderBidModifier
                )
        );
    }
}
