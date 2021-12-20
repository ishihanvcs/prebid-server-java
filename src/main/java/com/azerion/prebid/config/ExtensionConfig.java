package com.azerion.prebid.config;

import com.azerion.prebid.auction.GVastResponseCreator;
import com.azerion.prebid.auction.requestfactory.GVastParamsResolver;
import com.azerion.prebid.auction.requestfactory.GVastRequestFactory;
import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.azerion.prebid.customtrackers.contracts.ITrackerInjector;
import com.azerion.prebid.customtrackers.contracts.ITrackerMacroResolver;
import com.azerion.prebid.customtrackers.injectors.TrackerInjector;
import com.azerion.prebid.customtrackers.resolvers.TrackerMacroResolver;
import com.azerion.prebid.handler.GVastHandler;
import com.azerion.prebid.hooks.v1.CustomTrackerModule;
import com.azerion.prebid.settings.SettingsLoader;
import com.azerion.prebid.utils.JsonUtils;
import com.azerion.prebid.utils.MacroProcessor;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
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
        final String pbsVersion = applicationContext.getEnvironment().getProperty("app.version.pbs");
        if (pbsVersion != null) {
            logger.info("Core PBS Version: " + pbsVersion);
        }
        final String extVersion = applicationContext.getEnvironment().getProperty("app.version.extension");
        if (extVersion != null) {
            logger.info("Azerion Extension Version: " + extVersion);
        }
    }

    @Bean
    GVastParamsResolver gVastParamsResolver(GdprConfig gdprConfig) {
        return new GVastParamsResolver(gdprConfig);
    }

    @Bean
    GVastRequestFactory gvastRequestFactory(
            SettingsLoader settingsLoader,
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            @Qualifier("sourceIdGenerator")
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        return new GVastRequestFactory(
                settingsLoader,
                gVastParamsResolver,
                auctionRequestFactory,
                idGenerator,
                mapper);
    }

    @Bean
    GVastResponseCreator gVastResponseCreator(
            MacroProcessor macroProcessor,
            JacksonMapper mapper,
            @Value("${external-url}") String externalUrl,
            @Value("${google-ad-manager.network-code}") String gamNetworkCode,
            @Value("${cache.host}") String cacheHost
    ) {
        return new GVastResponseCreator(
                macroProcessor,
                new JsonUtils(mapper),
                externalUrl,
                gamNetworkCode,
                cacheHost
        );
    }

    @Bean
    GVastHandler gVastHandler(
            ApplicationContext applicationContext,
            GVastRequestFactory gVastRequestFactory,
            GVastResponseCreator gVastResponseCreator,
            ExchangeService exchangeService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            HttpInteractionLogger httpInteractionLogger,
            Router router) {
        GVastHandler handler = new GVastHandler(
                applicationContext,
                gVastRequestFactory,
                gVastResponseCreator,
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
            MacroProcessor macroProcessor
    ) {
        return new BidderBidModifier(macroProcessor);
    }

    @Bean
    Module customTrackerModule(
            ApplicationContext applicationContext,
            SettingsLoader settingsLoader,
            BidderBidModifier bidderBidModifier,
            JacksonMapper mapper
    ) {
        return new CustomTrackerModule(
                applicationContext,
                settingsLoader,
                bidderBidModifier, mapper);
    }

    @Bean
    MacroProcessor macroProcessor() {
        return new MacroProcessor();
    }

    @Bean
    ITrackerMacroResolver trackerMacroResolver(
            CurrencyConversionService currencyConversionService,
            JacksonMapper mapper
    ) {
        return new TrackerMacroResolver(currencyConversionService, mapper);
    }

    @Bean
    ITrackerInjector trackerInjector() {
        return new TrackerInjector();
    }
}
