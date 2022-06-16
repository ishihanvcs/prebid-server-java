package com.improvedigital.prebid.server.config;

import com.improvedigital.prebid.server.auction.requestfactory.GVastParamsResolver;
import com.improvedigital.prebid.server.auction.requestfactory.GVastRequestFactory;
import com.improvedigital.prebid.server.customtrackers.BidderBidModifier;
import com.improvedigital.prebid.server.customtrackers.contracts.ITrackerInjector;
import com.improvedigital.prebid.server.customtrackers.contracts.ITrackerMacroResolver;
import com.improvedigital.prebid.server.customtrackers.injectors.TrackerInjector;
import com.improvedigital.prebid.server.customtrackers.resolvers.TrackerMacroResolver;
import com.improvedigital.prebid.server.handler.GVastHandler;
import com.improvedigital.prebid.server.hooks.v1.customtrackers.TrackerHooksModule;
import com.improvedigital.prebid.server.hooks.v1.gvast.GVastHooksModule;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.GVastHookUtils;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.GdprConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import java.time.Clock;

@Configuration
@DependsOn({"webConfiguration", "serviceConfiguration"})
public class ExtensionConfig {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionConfig.class);

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void postConfigure() {
        final String pbsVersion = applicationContext.getEnvironment().getProperty("app.version.core");
        if (pbsVersion != null) {
            logger.info("Core PBS Version: " + pbsVersion);
        }
        final String extVersion = applicationContext.getEnvironment().getProperty("app.version.improvedigital");
        if (extVersion != null) {
            logger.info("Improve Digital PBS Version: " + extVersion);
        }
    }

    @Bean
    GVastParamsResolver gVastParamsResolver(GdprConfig gdprConfig) {
        return new GVastParamsResolver(gdprConfig);
    }

    @Bean
    GVastRequestFactory gvastRequestFactory(
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            @Qualifier("sourceIdGenerator") IdGenerator idGenerator,
            JacksonMapper mapper) {
        return new GVastRequestFactory(
                gVastParamsResolver,
                auctionRequestFactory,
                clock,
                idGenerator,
                mapper);
    }

    @Bean
    GVastHandler gVastHandler(
            GVastRequestFactory gVastRequestFactory,
            ExchangeService exchangeService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            HttpInteractionLogger httpInteractionLogger,
            Router router) {
        GVastHandler handler = new GVastHandler(
                gVastRequestFactory,
                exchangeService,
                analyticsReporter,
                metrics,
                clock,
                httpInteractionLogger);
        router.get(GVastHandler.END_POINT).handler(handler);
        return handler;
    }

    @Bean
    BidderBidModifier bidderBidModifier(
            MacroProcessor macroProcessor,
            RequestUtils requestUtils
    ) {
        return new BidderBidModifier(macroProcessor, requestUtils);
    }

    @Bean
    Module trackersHooksModule(
            ApplicationContext applicationContext,
            SettingsLoader settingsLoader,
            BidderBidModifier bidderBidModifier,
            JacksonMapper mapper
    ) {
        return new TrackerHooksModule(
                applicationContext,
                settingsLoader,
                bidderBidModifier);
    }

    @Bean
    JsonUtils jsonUtils(JacksonMapper mapper) {
        return new JsonUtils(mapper);
    }

    @Bean
    RequestUtils requestUtils(JsonUtils jsonUtils) {
        return new RequestUtils(jsonUtils);
    }

    @Bean
    GVastHookUtils gVastHookUtils(
            JsonMerger merger,
            RequestUtils requestUtils,
            CurrencyConversionService currencyConversionService
    ) {
        return new GVastHookUtils(
                requestUtils,
                merger,
                currencyConversionService
        );
    }

    @Bean
    Module gVastHooksModule(
            SettingsLoader settingsLoader,
            RequestUtils requestUtils,
            JsonMerger merger,
            GVastHookUtils gVastHookUtils,
            CurrencyConversionService currencyConversionService,
            MacroProcessor macroProcessor,
            @Value("${external-url}") String externalUrl,
            @Value("${google-ad-manager.network-code}") String gamNetworkCode,
            @Value("${cache.host}") String cacheHost
    ) {
        return new GVastHooksModule(
                settingsLoader,
                requestUtils,
                gVastHookUtils,
                merger,
                macroProcessor,
                externalUrl,
                gamNetworkCode,
                cacheHost
        );
    }

    @Bean
    MacroProcessor macroProcessor() {
        return new MacroProcessor();
    }

    @Bean
    ITrackerMacroResolver trackerMacroResolver(
            CurrencyConversionService currencyConversionService,
            RequestUtils requestUtils
    ) {
        return new TrackerMacroResolver(currencyConversionService, requestUtils);
    }

    @Bean
    ITrackerInjector trackerInjector(JacksonMapper mapper) {
        return new TrackerInjector(mapper);
    }
}
