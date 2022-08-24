package com.improvedigital.prebid.server.hooks.v1.customtracker;

import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.customtracker.model.AuctionRequestModuleContext;
import com.improvedigital.prebid.server.utils.LogMessage;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.springframework.context.ApplicationContext;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

    private static final Logger logger = LoggerFactory.getLogger(EntrypointHook.class);
    private final SettingsLoader settingsLoader;
    private final ApplicationContext applicationContext;

    public EntrypointHook(
            ApplicationContext applicationContext,
            SettingsLoader settingsLoader
    ) {
        this.applicationContext = applicationContext;
        this.settingsLoader = settingsLoader;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext) {

        return settingsLoader.getCustomTrackersFuture(invocationContext.timeout())
                .compose(customTrackers -> {
                    final AuctionRequestModuleContext context =
                            AuctionRequestModuleContext.from(
                                    applicationContext,
                                    customTrackers
                            );
                    return Future.succeededFuture(InvocationResultImpl.succeeded(
                            payload -> entrypointPayload,
                            context));
                }, t -> {
                        logger.warn(
                                LogMessage.from(t).with("custom tracker setting loading error")
                        );
                        return Future.succeededFuture(InvocationResultImpl.succeeded(payload -> entrypointPayload));
                    });
    }

    @Override
    public String code() {
        return "improvedigital-custom-tracker-hooks-entrypoint";
    }
}
