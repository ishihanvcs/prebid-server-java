package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.settings.model.Placement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.*;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.*;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Class for constructing auction request from /gvast GET params
 */
public class GVastRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(GVastRequestFactory.class);

    private final ApplicationSettings applicationSettings;
    private final AuctionRequestFactory auctionRequestFactory;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    public GVastRequestFactory(ApplicationSettings applicationSettings,
                        AuctionRequestFactory auctionRequestFactory,
                        Metrics metrics,
                        JacksonMapper mapper) {

        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Future<AuctionContext> fromRequest(GVastParams gvastParams, Placement placement,
                                              RoutingContext routingContext, long startTime) {
        return convertGVastGetRequestToRtbPost(gvastParams, placement)
                .map(bidRequest -> updateRoutingContext(routingContext, bidRequest))
                .compose(updatedRoutingContext -> auctionRequestFactory.fromRequest(updatedRoutingContext, startTime))
                .map(this::setImprovePlacementId);
    }

    /**
     * Overrides Improve Digital placementId to the one from GVAST GET param
     */
    private AuctionContext setImprovePlacementId(AuctionContext auctionContext) {
        String placementId = auctionContext.getHttpRequest().getQueryParams().get("p");

        int placementIdNum;
        try {
            placementIdNum = Integer.parseInt(placementId);
        } catch (NumberFormatException e) {
            placementIdNum = 0;
        }

        JsonNode impExt = auctionContext.getBidRequest().getImp().get(0).getExt();
        JsonNode improveParamsNode = impExt.at("/prebid/bidder/improvedigital");
        if (!improveParamsNode.isMissingNode()) {
            ((ObjectNode) improveParamsNode).put("placementId", placementIdNum);
        }
        return auctionContext;
    }

    /**
     * Constructs oRTB bid request from /gvast GET params, request header, and stored data
     */
    private Future<BidRequest> convertGVastGetRequestToRtbPost(GVastParams gvastParams, Placement placement) {
        final String tid = UUID.randomUUID().toString();

        // TODO validation should be moved to GVastParams
        if (gvastParams.getPlacementId() == null) {
            return Future.failedFuture(new InvalidRequestException("'p' parameter required"));
        }

        // TODO timeout should come from elsewhere
        Clock clock = Clock.systemUTC();
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        final Timeout timeout = timeoutFactory.create(500L);

        final String domain;
        // Check if referrer is just a domain, not a full path (Improve Digital only has domain as macro so sends domain as ref)
        if (gvastParams.getReferrer().startsWith("http")) {
            domain = HttpUtil.getHostFromUrl(gvastParams.getReferrer());
        } else {
            domain = gvastParams.getReferrer();
        }

        return applicationSettings.getAccountById(placement.getAccountId(), timeout)
                .map(account ->
                        BidRequest.builder()
                                .id(tid)
                                // .imp(Collections.singletonList(Imp.builder()
                                //         .id(placementIdStr)
                                //         .ext(mapper.mapper().valueToTree(ExtImp.of(ExtImpPrebid.builder()
                                //                 .storedrequest(ExtStoredRequest.of(placementIdStr))
                                //                 .build(),
                                //                 null)))
                                //         .video(Video.builder().mimes(Collections.singletonList("video/mp4")).build())
                                //         .build()))
                                //          .ext(mapper.mapper().valueToTree(
                                //                  ImprovedigitalImpExt.of(ImprovedigitalImpExtImprovedigital.of(placementId))))
                                .site(Site.builder()
                                        .cat(gvastParams.getCat())
                                        .domain(domain.startsWith("www.") ? domain.substring(4) : domain)
                                        .page(gvastParams.getReferrer())
                                        .publisher(Publisher.builder().id(account.getId()).build())
                                        .build())
                                .regs(Regs.of(null, ExtRegs.of(1, null)))
                                .user(User.builder()
                                        .ext(ExtUser.builder()
                                                .consent(gvastParams.getGdprConsentString())
                                                // .eids(singletonList(ExtUserEid.of("test.com", "some_user_id",
                                                //         singletonList(ExtUserEidUid.of("uId", 1, null)), null)))
                                                .build())
                                        .build())
                                // .device(Device.builder().ua("ua").ip("ip").ifa("ifaId").build())
                                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                        .storedrequest(ExtStoredRequest.of("gv-" + account.getId()))
                                        .build()))
                                .source(Source.builder().tid(tid).build())
                                .test(gvastParams.isDebug() ? 1 : 0)
                                .build()
                );
    }

    /**
     * Sets/replaces request body in the routing context
     */
    private RoutingContext updateRoutingContext(RoutingContext routingContext, BidRequest bidRequest) {
        String body = mapper.encode(bidRequest);
        routingContext.setBody(Buffer.buffer(body));
        return routingContext;
    }

}
