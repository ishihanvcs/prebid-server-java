package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JsonMerger;

import java.util.Arrays;
import java.util.Collection;

public class GVastHooksModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(GVastHooksModule.class);
    private final SettingsLoader settingsLoader;
    private final JsonUtils jsonUtils;
    private final JsonMerger merger;

    public GVastHooksModule(
            SettingsLoader settingsLoader,
            JsonUtils jsonUtils,
            JsonMerger merger
    ) {
        this.settingsLoader = settingsLoader;
        this.jsonUtils = jsonUtils;
        this.merger = merger;
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(settingsLoader, jsonUtils, merger),
                new RawAuctionRequestHook(jsonUtils)
        );
    }
}

