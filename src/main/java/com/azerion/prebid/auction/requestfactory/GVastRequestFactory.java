package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.CustParams;
import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.exception.PlacementAccountNullException;
import com.azerion.prebid.settings.CustomSettings;
import com.azerion.prebid.settings.model.Placement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.ApplicationSettings;
import org.springframework.context.ApplicationContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class for constructing auction request from /gvast GET params
 */
public class GVastRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(GVastRequestFactory.class);
    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final long DEFAULT_SETTINGS_LOADING_TIMEOUT = 2000L;

    private final ApplicationSettings applicationSettings;
    private final CustomSettings customSettings;
    private final AuctionRequestFactory auctionRequestFactory;
    private final JacksonMapper mapper;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final GVastParamsResolver paramResolver;
    private final ApplicationContext applicationContext;
    private final Timeout settingsLoadingTimeout;

    public GVastRequestFactory(
            ApplicationContext applicationContext,
            ApplicationSettings applicationSettings,
            CustomSettings customSettings,
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            IdGenerator idGenerator,
            JacksonMapper mapper) {

        this.applicationContext = Objects.requireNonNull(applicationContext);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.customSettings = Objects.requireNonNull(customSettings);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.paramResolver = Objects.requireNonNull(gVastParamsResolver);
        this.settingsLoadingTimeout = createSettingsLoadingTimeout();
    }

    private void validateUri(RoutingContext routingContext) throws IllegalArgumentException {
        try {
            new URI(routingContext.request().uri());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(String.format("Malformed URL %s", routingContext.request().uri()), e);
        }

    }

    public Future<GVastContext> fromRequest(RoutingContext routingContext, long startTime) {
        try {
            validateUri(routingContext);
            GVastParams gVastParams = paramResolver.resolve(getHttpRequestContext(routingContext));
            return customSettings.getPlacementById(String.valueOf(gVastParams.getPlacementId()), settingsLoadingTimeout)
                .map(this::validatePlacement)
                .map(placement -> GVastContext.from(gVastParams).with(placement).with(routingContext))
                .compose(this::updateContextWithAccountAndBidRequest)
                .map(this::updateRoutingContextBody)
                .compose(gVastContext ->
                    auctionRequestFactory
                        .fromRequest(gVastContext.getRoutingContext(), startTime)
                        .map(auctionContext ->
                            this.setImproveDigitalParams(
                                auctionContext, gVastParams
                            )
                        )
                        .map(gVastContext::with)
                );
        } catch (Throwable throwable) {
            return Future.failedFuture(throwable);
        }
    }

    /**
     * Create {{@link Timeout}} object based on configuration or default, to be used
     * during placement or account loading
     * @return Timeout
     */
    private Timeout createSettingsLoadingTimeout() {
        long lngTimeoutMs = DEFAULT_SETTINGS_LOADING_TIMEOUT;
        try {
            final String strTimeoutMs = applicationContext
                    .getEnvironment()
                    .getProperty("settings.default-loading-timeout", Long.toString(lngTimeoutMs));
            lngTimeoutMs = Long.parseLong(strTimeoutMs);
        } catch (Throwable e) {
            logger.warn(e.getMessage());
        }
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        return timeoutFactory.create(lngTimeoutMs);
    }

    /**
     * Verifies if placement belongs to a valid account
     */
    private Placement validatePlacement(Placement placement) {
        if (placement.getAccountId() == null) {
            final String msg = String.format("Undefined account for placement %s", placement.getId());
            EMPTY_ACCOUNT_LOGGER.error(msg, 100);
            throw new PlacementAccountNullException(msg, placement.getId());
        }
        return placement;
    }

    /**
     * Sets some Improve Digital request params from the GVAST GET params
     */
    private AuctionContext setImproveDigitalParams(AuctionContext auctionContext, GVastParams gVastParams) {
        JsonNode impExt = auctionContext.getBidRequest().getImp().get(0).getExt();

        if (impExt.isMissingNode()) {
            return auctionContext;
        }

        JsonNode improveParamsNode = impExt.at("/prebid/bidder/improvedigital");

        if (improveParamsNode.isMissingNode()) {
            return auctionContext;
        }

        CustParams custParams = gVastParams.getCustParams();
        if (!custParams.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            ((ObjectNode) improveParamsNode).set("keyValues", mapper.valueToTree(custParams));
        }
        return auctionContext;
    }

    /**
     * Constructs oRTB bid request from /gvast GET params, request header, and stored data
     */
    private Future<GVastContext> updateContextWithAccountAndBidRequest(GVastContext gVastContext) {
        final String tid = idGenerator.generateId(); // UUID.randomUUID().toString();
        final GVastParams gVastParams = gVastContext.getGVastParams();
        final RoutingContext routingContext = gVastContext.getRoutingContext();
        final String language = routingContext.preferredLanguage() == null
                ? null : routingContext.preferredLanguage().tag();

        // Improve Digital only allows IAB v1/oRTB 2.5 category format.
        // Any other category format will cause the Improve bid request to deem the request invalid
        final List<String> categories = gVastParams.getCat().stream()
                .filter(cat -> cat.startsWith("IAB"))
                .collect(Collectors.toList());

        final String gdpr = gVastParams.getGdpr();
        final Integer gdprInt = StringUtils.isBlank(gdpr) ? null : Integer.parseInt(gdpr);

        return applicationSettings.getAccountById(gVastContext.getPlacement().getAccountId(), settingsLoadingTimeout)
            .map(account -> {
                BidRequest commonBidRequest = BidRequest.builder()
                        .id(tid)
                        .imp(Collections.singletonList(Imp.builder()
                                .id("1")
                                .video(Video.builder()
                                        .minduration(gVastParams.getMinduration())
                                        .maxduration(gVastParams.getMaxduration())
                                        .w(gVastParams.getW())
                                        .h(gVastParams.getH())
                                        .protocols(gVastParams.getProtocols())
                                        .api(gVastParams.getApi())
                                        .placement(gVastParams.getPlacement())
                                        .build())
                                .ext(mapper.mapper().valueToTree(ExtImp.of(ExtImpPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of(gVastContext.getPlacement().getId()))
                                        .build(), null)))
                                .build()))
                        .regs(Regs.of(null, ExtRegs.of(gdprInt, null)))
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent(gVastParams.getGdprConsentString())
                                        .build())
                                .build())
                        .source(Source.builder().tid(tid).build())
                        .test(gVastParams.isDebug() ? 1 : 0)
                        .build();

                final BidRequest bidRequest;
                if (StringUtils.isBlank(gVastParams.getBundle())) {
                    // web
                    bidRequest = commonBidRequest.toBuilder()
                            .site(Site.builder()
                                    .cat(categories)
                                    .domain(gVastParams.getDomain())
                                    .page(gVastParams.getReferrer())
                                    .publisher(Publisher.builder().id(account.getId()).build())
                                    .build())
                            .device(Device.builder().language(language).build())
                            .build();
                } else {
                    //  app
                    bidRequest = commonBidRequest.toBuilder()
                            .app(App.builder()
                                    .bundle(gVastParams.getBundle())
                                    .storeurl(gVastParams.getReferrer())
                                    .build())
                            .device(Device.builder()
                                    .ifa(gVastParams.getIfa())
                                    .language(language)
                                    .ua(gVastParams.getUa())
                                    .build())
                            .build();
                }

                return gVastContext.with(account).with(bidRequest);
            });
    }

    private static CaseInsensitiveMultiMap toCaseInsensitiveMultiMap(MultiMap originalMap) {
        final CaseInsensitiveMultiMap.Builder mapBuilder = CaseInsensitiveMultiMap.builder();
        originalMap.entries().forEach(entry -> mapBuilder.add(entry.getKey(), entry.getValue()));

        return mapBuilder.build();
    }

    private static HttpRequestContext getHttpRequestContext(RoutingContext routingContext) {
        return HttpRequestContext.builder()
            .body(routingContext.getBodyAsString())
            .queryParams(toCaseInsensitiveMultiMap(routingContext.queryParams()))
            .headers(toCaseInsensitiveMultiMap(routingContext.request().headers()))
            .absoluteUri(routingContext.request().absoluteURI())
            .scheme(routingContext.request().scheme())
            .remoteHost(routingContext.request().remoteAddress().host())
            .build();
    }

    /**
     * Sets/replaces request body in the routing context
     */
    private GVastContext updateRoutingContextBody(GVastContext gVastContext) {
        String body = mapper.encode(gVastContext.getBidRequest());
        gVastContext.getRoutingContext().setBody(Buffer.buffer(body));
        return gVastContext;
    }
}
