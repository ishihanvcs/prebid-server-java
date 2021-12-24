package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.CustParams;
import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.settings.SettingsLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.geolocation.GeoLocationService;
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

    private final SettingsLoader settingsLoader;
    private final AuctionRequestFactory auctionRequestFactory;
    private final JacksonMapper mapper;
    private final IdGenerator idGenerator;
    private final GVastParamsResolver paramResolver;
    private final GeoLocationService geoLocationService;
    private final TimeoutFactory timeoutFactory;
    private final Clock clock;

    public GVastRequestFactory(
            SettingsLoader settingsLoader,
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            GeoLocationService geoLocationService,
            TimeoutFactory timeoutFactory,
            Clock clock,
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        this.settingsLoader = Objects.requireNonNull(settingsLoader);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.clock = clock;
        this.geoLocationService = geoLocationService;
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.paramResolver = Objects.requireNonNull(gVastParamsResolver);
    }

    private void validateUri(RoutingContext routingContext) throws IllegalArgumentException {
        try {
            new URI(routingContext.request().uri());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(String.format("Malformed URL %s", routingContext.request().uri()), e);
        }
    }

    private Future<GVastContext> createContext(
            Imp imp,
            RoutingContext routingContext,
            GVastParams gVastParams,
            Timeout initialTimeout
    ) {
        try {
            return updateContextWithAccountAndBidRequest(
                    GVastContext
                    .from(gVastParams)
                    .with(routingContext)
                    .with(imp, mapper),
                    initialTimeout
            );
        } catch (JsonProcessingException e) {
            return Future.failedFuture(e);
        }
    }

    private Future<AuctionContext> resolveGeoInfoIfNull(AuctionContext auctionContext) {
        if (auctionContext.getGeoInfo() == null) {
            final String ipAddress = auctionContext.getBidRequest().getDevice().getIp();
            if (geoLocationService != null && StringUtils.isNotBlank(ipAddress)) {
                return geoLocationService.lookup(ipAddress, auctionContext.getTimeout())
                        .map(geoInfo -> auctionContext.toBuilder().geoInfo(geoInfo).build());
            }
        }
        return Future.succeededFuture(auctionContext);
    }

    public Future<GVastContext> fromRequest(RoutingContext routingContext, long startTime) {
        try {
            final Timeout initialTimeout = settingsLoader.createSettingsLoadingTimeout(startTime);
            validateUri(routingContext);
            GVastParams gVastParams = paramResolver.resolve(getHttpRequestContext(routingContext));
            return settingsLoader.getStoredImp(gVastParams.getImpId(), initialTimeout)
                .compose(imp -> this.createContext(imp, routingContext, gVastParams, initialTimeout))
                .map(this::updateRoutingContextBody)
                .compose(gVastContext ->
                    auctionRequestFactory
                        .fromRequest(gVastContext.getRoutingContext(), clock.millis())
                        .compose(this::resolveGeoInfoIfNull)
                        .map(auctionContext -> updateAuctionTimeout(auctionContext, startTime))
                        .map(auctionContext -> setImproveDigitalParams(auctionContext, gVastParams))
                        .map(gVastContext::with)
                );
        } catch (Throwable throwable) {
            return Future.failedFuture(throwable);
        }
    }

    public AuctionContext updateAuctionTimeout(AuctionContext auctionContext, long actualStartTime) {
        final long timeSpent = clock.millis() - actualStartTime;
        return auctionContext.toBuilder().timeout(auctionContext.getTimeout().minus(timeSpent)).build();
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
    private Future<GVastContext> updateContextWithAccountAndBidRequest(
            GVastContext gVastContext,
            Timeout initialTimeout
    ) {
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
        final String accountId = gVastContext.getImpExtConfig().getAccountId();
        return settingsLoader.getAccountFuture(accountId, initialTimeout)
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
                                .ext(mapper.mapper().valueToTree(
                                        ExtImp.of(ExtImpPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of(String.valueOf(gVastParams.getImpId())))
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
        String body = mapper.encodeToString(gVastContext.getBidRequest());
        gVastContext.getRoutingContext().setBody(Buffer.buffer(body));
        return gVastContext;
    }
}
