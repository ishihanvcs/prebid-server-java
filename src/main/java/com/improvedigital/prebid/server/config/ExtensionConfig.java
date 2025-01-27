package com.improvedigital.prebid.server.config;

import com.improvedigital.prebid.server.customtracker.BidderBidModifier;
import com.improvedigital.prebid.server.customtracker.contracts.ITrackerInjector;
import com.improvedigital.prebid.server.customtracker.contracts.ITrackerMacroResolver;
import com.improvedigital.prebid.server.customtracker.injectors.TrackerInjector;
import com.improvedigital.prebid.server.customtracker.resolvers.TrackerMacroResolver;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.customvast.handler.GVastHandler;
import com.improvedigital.prebid.server.customvast.requestfactory.GVastRequestFactory;
import com.improvedigital.prebid.server.customvast.resolvers.GVastHandlerParamsResolver;
import com.improvedigital.prebid.server.hooks.v1.customtracker.CustomTrackerHooksModule;
import com.improvedigital.prebid.server.hooks.v1.customvast.CustomVastHooksModule;
import com.improvedigital.prebid.server.hooks.v1.revshare.ImprovedigitalBidAdjustmentModule;
import com.improvedigital.prebid.server.hooks.v1.supplychain.ImprovedigitalSupplyChainModule;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.PbsEndpointInvoker;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.vertx.http.HttpClient;
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
    GVastHandlerParamsResolver gVastParamsResolver(
            CountryCodeMapper countryCodeMapper,
            GdprConfig gdprConfig) {
        return new GVastHandlerParamsResolver(countryCodeMapper, gdprConfig);
    }

    @Bean
    GVastRequestFactory gvastRequestFactory(
            GVastHandlerParamsResolver gVastHandlerParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            @Qualifier("sourceIdGenerator") IdGenerator idGenerator,
            JacksonMapper mapper) {
        return new GVastRequestFactory(
                gVastHandlerParamsResolver,
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
        return new CustomTrackerHooksModule(
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
    PbsEndpointInvoker pbsEndpointInvoker(
            HttpClient httpClient,
            JacksonMapper mapper,
            @Value("${server.ssl}") boolean ssl,
            @Value("${server.http.port}") int port
    ) {
        return new PbsEndpointInvoker(httpClient, mapper, ssl, port);
    }

    @Bean
    CustomVastUtils customVastUtils(
            PbsEndpointInvoker pbsEndpointInvoker,
            JsonMerger merger,
            RequestUtils requestUtils,
            CurrencyConversionService currencyConversionService,
            MacroProcessor macroProcessor,
            @Autowired(required = false) GeoLocationService geoLocationService,
            Metrics metrics,
            CountryCodeMapper countryCodeMapper,
            @Value("${external-url}") String externalUrl,
            @Value("${google-ad-manager.network-code}") String gamNetworkCode,
            @Value("${cache.host}") String cacheHost
    ) {
        return new CustomVastUtils(
                pbsEndpointInvoker,
                requestUtils,
                merger,
                currencyConversionService,
                macroProcessor,
                geoLocationService,
                metrics,
                countryCodeMapper,
                externalUrl,
                gamNetworkCode,
                cacheHost
        );
    }

    @Bean
    Module customVastHooksModule(
            SettingsLoader settingsLoader,
            RequestUtils requestUtils,
            JsonMerger merger,
            CustomVastUtils customVastUtils
    ) {
        return new CustomVastHooksModule(
                settingsLoader,
                requestUtils,
                customVastUtils,
                merger
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

    @Bean
    ImprovedigitalSupplyChainModule improvedigitalSupplyChainModule(
            RequestUtils requestUtils, ApplicationSettings applicationSettings) {
        return new ImprovedigitalSupplyChainModule(requestUtils, applicationSettings);
    }

    @Bean
    ImprovedigitalBidAdjustmentModule improvedigitalBidAdjustmentModule(
            RequestUtils requestUtils, BidderCatalog bidderCatalog, ApplicationSettings applicationSettings) {
        return new ImprovedigitalBidAdjustmentModule(requestUtils, bidderCatalog, applicationSettings);
    }
}
