package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.customtrackers.CustomTrackerModuleContext;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import com.azerion.prebid.settings.model.Placement;
import com.azerion.prebid.settings.SettingsLoader;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

    private final SettingsLoader settingsLoader;

    public EntrypointHook(
            SettingsLoader settingsLoader
    ) {
        this.settingsLoader = settingsLoader;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext) {
        final String placementId = entrypointPayload.queryParams().get("p");
        Placement placement = null;
        if (StringUtils.isNotBlank(placementId)) {
            try {
                placement = settingsLoader.getPlacement(placementId);
            } catch (Exception ex) {
                return Future.succeededFuture(
                        InvocationResultImpl.rejected(ex.getMessage())
                );
            }
        }
        return Future.succeededFuture(
                InvocationResultImpl.succeeded(
                    payload -> entrypointPayload,
                        CustomTrackerModuleContext.builder()
                                .placement(placement)
                                .build()
                )
        );
    }

    @Override
    public String code() {
        return "custom-tracker-entrypoint";
    }
}
