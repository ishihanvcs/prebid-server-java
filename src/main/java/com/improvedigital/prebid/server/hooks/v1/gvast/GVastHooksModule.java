package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.currency.CurrencyConversionService;
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
    private final CurrencyConversionService currencyConversionService;
    private final RequestUtils requestUtils;

    public GVastHooksModule(
            SettingsLoader settingsLoader,
            JsonUtils jsonUtils,
            RequestUtils requestUtils, JsonMerger merger,
            CurrencyConversionService currencyConversionService
    ) {
        this.settingsLoader = settingsLoader;
        this.jsonUtils = jsonUtils;
        this.requestUtils = requestUtils;
        this.merger = merger;
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(settingsLoader, jsonUtils, requestUtils, merger),
                new RawAuctionRequestHook(jsonUtils),
                new ProcessedAuctionRequestHook(jsonUtils, currencyConversionService)
        );
    }
}

