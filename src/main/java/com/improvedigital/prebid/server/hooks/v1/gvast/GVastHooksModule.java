package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
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
    private final MacroProcessor macroProcessor;
    private final String externalUrl;
    private final String gamNetworkCode;
    private final String cacheHost;
    private final RequestUtils requestUtils;

    public GVastHooksModule(
            SettingsLoader settingsLoader,
            JsonUtils jsonUtils,
            RequestUtils requestUtils, JsonMerger merger,
            CurrencyConversionService currencyConversionService,
            MacroProcessor macroProcessor,
            String externalUrl,
            String gamNetworkCode,
            String cacheHost
    ) {
        this.settingsLoader = settingsLoader;
        this.jsonUtils = jsonUtils;
        this.requestUtils = requestUtils;
        this.merger = merger;
        this.currencyConversionService = currencyConversionService;
        this.macroProcessor = macroProcessor;
        this.externalUrl = externalUrl;
        this.gamNetworkCode = gamNetworkCode;
        this.cacheHost = cacheHost;
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-module";
    }

    @Override
    public Collection<? extends Hook<?, ? extends InvocationContext>> hooks() {
        return Arrays.asList(
                new EntrypointHook(settingsLoader, jsonUtils, requestUtils, merger),
                new ProcessedAuctionRequestHook(jsonUtils, currencyConversionService),
                new AuctionResponseHook(jsonUtils, macroProcessor,
                        externalUrl, gamNetworkCode, cacheHost)
        );
    }
}

