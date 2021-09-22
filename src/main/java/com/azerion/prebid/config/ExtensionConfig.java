package com.azerion.prebid.config;

import com.azerion.prebid.auction.GVastResponseCreator;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackerInjector;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackingUrlResolver;
import com.azerion.prebid.auction.customtrackers.injectors.TrackerInjector;
import com.azerion.prebid.auction.customtrackers.resolvers.TrackingUrlResolver;
import com.azerion.prebid.auction.requestfactory.GVastParamsResolver;
import com.azerion.prebid.auction.requestfactory.GVastRequestFactory;
import com.azerion.prebid.handler.GVastHandler;
import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.auction.BidResponsePostProcessor;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.GdprConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

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
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Timeout settingsLoadingTimeout,
            @Qualifier("sourceIdGenerator")
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        return new GVastRequestFactory(
                applicationSettings,
                customSettings,
                gVastParamsResolver,
                auctionRequestFactory,
                settingsLoadingTimeout,
                idGenerator,
                mapper);
    }

    @Bean
    GVastResponseCreator gVastResponseCreator(
            CustomTrackerSetting customTrackerSetting,
            @Value("${external-url}") String externalUrl,
            @Value("${google-ad-manager.network-code}") String gamNetworkCode) {
        return new GVastResponseCreator(
                customTrackerSetting,
                externalUrl,
                gamNetworkCode
        );
    }

    @Bean
    GVastHandler gVastHandler(
            GVastRequestFactory gVastRequestFactory,
            GVastResponseCreator gVastResponseCreator,
            ExchangeService exchangeService,
            AnalyticsReporterDelegator analyticsReporter,
            Metrics metrics,
            Clock clock,
            HttpInteractionLogger httpInteractionLogger,
            Router router) {
        GVastHandler handler = new GVastHandler(
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
    CustomTrackerSetting customTrackerSetting(
            CustomSettings customSettings,
            Timeout settingsLoadingTimeout
    ) {
        return customSettings.getCustomTrackerSetting(settingsLoadingTimeout).result();
    }

    @Bean
    @Primary
    BidResponsePostProcessor customResponsePostProcessor(
            ApplicationContext applicationContext,
            CustomTrackerSetting customTrackerSetting
    ) {

        return new com.azerion.prebid.auction.BidResponsePostProcessor(
                applicationContext, customTrackerSetting
        );
    }

    @Bean
    ITrackingUrlResolver trackingUrlResolver(
            CurrencyConversionService currencyConversionService
    ) {
        return new TrackingUrlResolver(currencyConversionService);
    }

    @Bean
    ITrackerInjector trackerInjector() {
        return new TrackerInjector();
    }
}
