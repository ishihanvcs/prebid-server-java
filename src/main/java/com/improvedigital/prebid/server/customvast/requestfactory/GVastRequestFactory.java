package com.improvedigital.prebid.server.customvast.requestfactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.improvedigital.prebid.server.customvast.model.GVastHandlerParams;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import com.improvedigital.prebid.server.customvast.resolvers.GVastHandlerParamsResolver;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class for constructing auction request from /gvast GET params
 */
public class GVastRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(GVastRequestFactory.class);
    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);

    private final AuctionRequestFactory auctionRequestFactory;
    private final JacksonMapper mapper;
    private final IdGenerator idGenerator;
    private final GVastHandlerParamsResolver paramResolver;
    private final Clock clock;

    public GVastRequestFactory(
            GVastHandlerParamsResolver gVastHandlerParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        this.clock = clock;
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.paramResolver = Objects.requireNonNull(gVastHandlerParamsResolver);
    }

    private void validateUri(RoutingContext routingContext) throws IllegalArgumentException {
        try {
            new URI(routingContext.request().uri());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(String.format("Malformed URL %s", routingContext.request().uri()), e);
        }
    }

    private Imp validateStoredImp(Imp imp) {
        // Add waterfall validation here - gam and no_gam can't mix
        return imp;
    }

    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        try {
            validateUri(routingContext);
            GVastHandlerParams gVastHandlerParams = paramResolver.resolve(getHttpRequestContext(routingContext));
            BidRequest bidRequest = createBidRequest(routingContext, gVastHandlerParams);
            String body = mapper.encodeToString(bidRequest);
            routingContext.setBody(Buffer.buffer(body));
            return auctionRequestFactory
                    .fromRequest(routingContext, clock.millis())
                    .map(auctionContext -> updateAuctionTimeout(auctionContext, startTime));
        } catch (Throwable throwable) {
            return Future.failedFuture(throwable);
        }
    }

    public AuctionContext updateAuctionTimeout(AuctionContext auctionContext, long actualStartTime) {
        final long timeSpent = clock.millis() - actualStartTime;
        return auctionContext.toBuilder().timeout(auctionContext.getTimeout().minus(timeSpent)).build();
    }

    /**
     * Constructs oRTB bid request from /gvast GET params, request header, and stored data
     */
    private BidRequest createBidRequest(
            RoutingContext routingContext,
            GVastHandlerParams gVastHandlerParams
    ) {
        final String tid = idGenerator.generateId(); // UUID.randomUUID().toString();
        final String language = routingContext.preferredLanguage() == null
                ? null : routingContext.preferredLanguage().tag();

        final String gdpr = gVastHandlerParams.getGdpr();
        final Integer gdprInt = StringUtils.isBlank(gdpr) ? null : Integer.parseInt(gdpr);
        final BigDecimal bidfloor = gVastHandlerParams.getBidfloor() == null
                ? null : BigDecimal.valueOf(gVastHandlerParams.getBidfloor());
        final ObjectNode impExt = mapper.mapper().valueToTree(
                ExtImp.of(ExtImpPrebid.builder()
                        .storedrequest(ExtStoredRequest.of(gVastHandlerParams.getImpId()))
                        .build(), null));

        ((ObjectNode) impExt.at("/prebid"))
                .putObject("improvedigitalpbs")
                .put("responseType", VastResponseType.gvast.name());

        if (!gVastHandlerParams.getCustParams().isEmpty()) {
            ((ObjectNode) impExt.at("/prebid"))
                    .putObject("bidder")
                    .putObject("improvedigital")
                    .set("keyValues", mapper.mapper().valueToTree(gVastHandlerParams.getCustParams()));
        }

        BidRequest bidRequest = BidRequest.builder()
                .id(tid)
                .cur(List.of("EUR"))
                .device(Device.builder()
                        .carrier(gVastHandlerParams.getCarrier())
                        .ifa(gVastHandlerParams.getIfa())
                        .ip(gVastHandlerParams.getIp())
                        .language(language)
                        .lmt(gVastHandlerParams.getLmt())
                        .model(gVastHandlerParams.getModel())
                        .os(gVastHandlerParams.getOs())
                        .osv(gVastHandlerParams.getOsv())
                        .ua(gVastHandlerParams.getUa())
                        .build())
                .imp(new ArrayList<>(List.of(Imp.builder()
                    .id("1")
                    .bidfloor(bidfloor)
                    .bidfloorcur(gVastHandlerParams.getBidfloorcur())
                    .video(Video.builder()
                            .minduration(gVastHandlerParams.getMinduration())
                            .maxduration(gVastHandlerParams.getMaxduration())
                            .w(gVastHandlerParams.getW())
                            .h(gVastHandlerParams.getH())
                            .protocols(gVastHandlerParams.getProtocols())
                            .api(gVastHandlerParams.getApi())
                            .placement(gVastHandlerParams.getPlacement())
                            .build())
                    .ext(impExt)
                    .build())))
                .regs(Regs.of(gVastHandlerParams.getCoppa(), ExtRegs.of(gdprInt, null)))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .consent(gVastHandlerParams.getGdprConsent())
                                .build())
                        .build())
                .source(Source.builder().tid(tid).build())
                .test(gVastHandlerParams.isDebug() ? 1 : 0)
                .tmax(gVastHandlerParams.getTmax()).build();

        if (StringUtils.isBlank(gVastHandlerParams.getBundle())) {
            // web
            return bidRequest.toBuilder()
                    .site(Site.builder()
                            .cat(gVastHandlerParams.getCat())
                            .domain(gVastHandlerParams.getDomain())
                            .page(gVastHandlerParams.getReferrer())
                            .build())
                    .build();
        }
        //  app
        return bidRequest.toBuilder()
                .app(App.builder()
                        .bundle(gVastHandlerParams.getBundle())
                        .name(gVastHandlerParams.getAppName())
                        .storeurl(gVastHandlerParams.getStoreUrl())
                        .build())
                .build();
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
}
