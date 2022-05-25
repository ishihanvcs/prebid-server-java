package com.improvedigital.prebid.server.auction.requestfactory;

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
import com.improvedigital.prebid.server.auction.model.GVastParams;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
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
import java.util.stream.Collectors;

/**
 * Class for constructing auction request from /gvast GET params
 */
public class GVastRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(GVastRequestFactory.class);
    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);

    private final AuctionRequestFactory auctionRequestFactory;
    private final JacksonMapper mapper;
    private final IdGenerator idGenerator;
    private final GVastParamsResolver paramResolver;
    private final Clock clock;

    public GVastRequestFactory(
            GVastParamsResolver gVastParamsResolver,
            AuctionRequestFactory auctionRequestFactory,
            Clock clock,
            IdGenerator idGenerator,
            JacksonMapper mapper) {
        this.clock = clock;
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

    private Imp validateStoredImp(Imp imp) {
        // Add waterfall validation here - gam and no_gam can't mix
        return imp;
    }

    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        try {
            validateUri(routingContext);
            GVastParams gVastParams = paramResolver.resolve(getHttpRequestContext(routingContext));
            BidRequest bidRequest = createBidRequest(routingContext, gVastParams);
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
            GVastParams gVastParams
    ) {
        final String tid = idGenerator.generateId(); // UUID.randomUUID().toString();
        final String language = routingContext.preferredLanguage() == null
                ? null : routingContext.preferredLanguage().tag();

        // Improve Digital only allows IAB v1/oRTB 2.5 category format.
        // Any other category format will cause the Improve bid request to deem the request invalid
        final List<String> categories = gVastParams.getCat().stream()
                .filter(cat -> cat.startsWith("IAB"))
                .collect(Collectors.toList());

        final String gdpr = gVastParams.getGdpr();
        final Integer gdprInt = StringUtils.isBlank(gdpr) ? null : Integer.parseInt(gdpr);
        final BigDecimal bidfloor = gVastParams.getBidfloor() == null
                ? null : BigDecimal.valueOf(gVastParams.getBidfloor());
        final ObjectNode impExt = mapper.mapper().valueToTree(
                ExtImp.of(ExtImpPrebid.builder()
                        .storedrequest(ExtStoredRequest.of(String.valueOf(gVastParams.getImpId())))
                        .build(), null));

        ((ObjectNode) impExt.at("/prebid"))
                .putObject("improvedigitalpbs")
                .put("responseType", VastResponseType.gvast.name());

        if (!gVastParams.getCustParams().isEmpty()) {
            ((ObjectNode) impExt.at("/prebid"))
                    .putObject("bidder")
                    .putObject("improvedigital")
                    .set("keyValues", mapper.mapper().valueToTree(gVastParams.getCustParams()));
        }

        BidRequest bidRequest = BidRequest.builder()
                .id(tid)
                .cur(List.of("EUR"))
                .device(Device.builder()
                        .carrier(gVastParams.getCarrier())
                        .ifa(gVastParams.getIfa())
                        .ip(gVastParams.getIp())
                        .language(language)
                        .lmt(gVastParams.getLmt())
                        .model(gVastParams.getModel())
                        .os(gVastParams.getOs())
                        .osv(gVastParams.getOsv())
                        .ua(gVastParams.getUa())
                        .build())
                .imp(new ArrayList<>(List.of(Imp.builder()
                    .id("1")
                    .bidfloor(bidfloor)
                    .bidfloorcur(gVastParams.getBidfloorcur())
                    .video(Video.builder()
                            .minduration(gVastParams.getMinduration())
                            .maxduration(gVastParams.getMaxduration())
                            .w(gVastParams.getW())
                            .h(gVastParams.getH())
                            .protocols(gVastParams.getProtocols())
                            .api(gVastParams.getApi())
                            .placement(gVastParams.getPlacement())
                            .build())
                    .ext(impExt)
                    .build())))
                .regs(Regs.of(gVastParams.getCoppa(), ExtRegs.of(gdprInt, null)))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .consent(gVastParams.getGdprConsentString())
                                .build())
                        .build())
                .source(Source.builder().tid(tid).build())
                .test(gVastParams.isDebug() ? 1 : 0)
                .tmax(gVastParams.getTmax()).build();

        if (StringUtils.isBlank(gVastParams.getBundle())) {
            // web
            return bidRequest.toBuilder()
                    .site(Site.builder()
                            .cat(categories)
                            .domain(gVastParams.getDomain())
                            .page(gVastParams.getReferrer())
                            .build())
                    .build();
        }
        //  app
        return bidRequest.toBuilder()
                .app(App.builder()
                        .bundle(gVastParams.getBundle())
                        .name(gVastParams.getAppName())
                        .storeurl(gVastParams.getStoreUrl())
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
