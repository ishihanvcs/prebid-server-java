package com.azerion.prebid.config;

import com.azerion.prebid.auction.GVastResponseCreator;
import com.azerion.prebid.auction.requestfactory.GVastParamsResolver;
import com.azerion.prebid.auction.requestfactory.GVastRequestFactory;
import com.azerion.prebid.handler.GVastHandler;
import com.azerion.prebid.settings.CustomSettings;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
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
public class GVastConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GVastConfiguration.class);

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void registerCustomRoutes() {
        Router router = (Router) applicationContext.getBean("router");
        GVastHandler gVastHandler = (GVastHandler) applicationContext.getBean("gVastHandler");
        router.get(GVastHandler.END_POINT).handler(gVastHandler);
        logger.debug("Custom routes are registered successfully");
    }

    @Bean
    GVastParamsResolver gVastParamsResolver() {
        return new GVastParamsResolver();
    }

    @Bean
    GVastRequestFactory gvastRequestFactory(
            ApplicationContext applicationContext,
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            @Qualifier("sourceIdGenerator")
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        return new GVastRequestFactory(
                applicationContext,
                applicationSettings,
                customSettings,
                gVastParamsResolver,
                auctionRequestFactory,
                clock,
                idGenerator,
                mapper);
    }

    @Bean
    GVastResponseCreator gVastResponseCreator(
            // Metrics metrics,
            @Value("${external-url}") String externalUrl,
            @Value("${google-ad-manager.network-code}") String gamNetworkCode) {
        return new GVastResponseCreator(
                // metrics,
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
            HttpInteractionLogger httpInteractionLogger) {

        return new GVastHandler(
                gVastRequestFactory,
                gVastResponseCreator,
                exchangeService,
                analyticsReporter,
                metrics,
                clock,
                httpInteractionLogger);
    }
}
