package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.customtrackers.ModuleContext;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import com.azerion.prebid.settings.SettingsLoader;
import com.azerion.prebid.settings.model.Placement;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.springframework.context.ApplicationContext;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

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
        final String placementId = entrypointPayload.queryParams().get("p");
        Future<Placement> future = Future.succeededFuture(null);
        if (StringUtils.isNotBlank(placementId)) {
            future = settingsLoader.getPlacementFuture(placementId);
        }

        return future.compose(
                // successMapper
                placement -> Future.succeededFuture(InvocationResultImpl.succeeded(
                        payload -> entrypointPayload,
                        ModuleContext.from(applicationContext, placement)
                )),
                // failureMapper
                throwable -> Future.succeededFuture(InvocationResultImpl.rejected(throwable.getMessage()))
        );
    }

    @Override
    public String code() {
        return "custom-tracker-entrypoint";
    }
}
