package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JsonMerger;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class CustomVastHooksModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(CustomVastHooksModule.class);
    private final SettingsLoader settingsLoader;
    private final RequestUtils requestUtils;
    private final JsonMerger merger;
    private final CustomVastUtils customVastUtils;

    public CustomVastHooksModule(
            SettingsLoader settingsLoader,
            RequestUtils requestUtils,
            CustomVastUtils customVastUtils,
            JsonMerger merger
    ) {
        this.settingsLoader = Objects.requireNonNull(settingsLoader);
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.merger = Objects.requireNonNull(merger);
        this.customVastUtils = Objects.requireNonNull(customVastUtils);
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(settingsLoader, requestUtils, merger),
                new ProcessedAuctionRequestHook(settingsLoader, requestUtils, customVastUtils),
                new AuctionResponseHook(requestUtils, customVastUtils)
        );
    }
}

