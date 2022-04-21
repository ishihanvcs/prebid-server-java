package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;

import java.util.Arrays;
import java.util.Collection;

public class GVastHooksModule implements Module {

    private static final Logger logger = LoggerFactory.getLogger(GVastHooksModule.class);
    private final SettingsLoader settingsLoader;
    private final ObjectMapper mapper;
    private final JsonMerger merger;

    public GVastHooksModule(
            SettingsLoader settingsLoader,
            JacksonMapper mapper,
            JsonMerger merger
    ) {
        this.settingsLoader = settingsLoader;
        this.mapper = mapper.mapper();
        this.merger = merger;
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(settingsLoader, mapper, merger)
        );
    }
}

