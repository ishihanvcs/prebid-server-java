package com.improvedigital.prebid.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class RequestUtilsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonUtils jsonUtils = new JsonUtils(new JacksonMapper(mapper));
    private final RequestUtils requestUtils = new RequestUtils(jsonUtils);

    private BidRequest siteRequestWithoutPublisher;
    private BidRequest appRequestWithoutPublisher;
    private BidRequest siteRequestWithoutParentAccountId;
    private BidRequest siteRequestWithParentAccountId;
    private BidRequest appRequestWithoutParentAccountId;
    private BidRequest appRequestWithParentAccountId;

    private final String publisherId = "publisher-id";
    private final String accountId = "account-id";

    @Before
    public void setUp() {
        siteRequestWithoutParentAccountId = createRequest(builder -> builder.site(Site.builder()
                .publisher(Publisher.builder()
                        .id(publisherId)
                        .build()
                )
                .build()
        ));

        siteRequestWithParentAccountId = createRequest(builder -> builder.site(Site.builder()
                        .publisher(Publisher.builder()
                                .ext(ExtPublisher.of(
                                        ExtPublisherPrebid.of(accountId)
                                ))
                                .build()
                        )
                .build()
        ));

        siteRequestWithoutPublisher = createRequest(builder -> builder.site(Site.builder().build()));

        appRequestWithoutParentAccountId = createRequest(builder -> builder.app(App.builder()
                .publisher(Publisher.builder()
                        .id(publisherId)
                        .build()
                )
                .build()
        ));

        appRequestWithParentAccountId = createRequest(builder -> builder.app(App.builder()
                .publisher(Publisher.builder()
                        .ext(ExtPublisher.of(
                                ExtPublisherPrebid.of(accountId)
                        ))
                        .build()
                )
                .build()
        ));

        appRequestWithoutPublisher = createRequest(builder -> builder.app(App.builder().build()));
    }

    @Test
    public void testGetAccountId() {
        String result = requestUtils.getAccountId(null);
        Assert.assertNull(result);

        result = requestUtils.getAccountId(siteRequestWithoutPublisher);
        Assert.assertNull(result);

        result = requestUtils.getAccountId(siteRequestWithoutParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(accountId, result);
        Assert.assertEquals(publisherId, result);

        result = requestUtils.getAccountId(siteRequestWithParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(publisherId, result);
        Assert.assertEquals(accountId, result);

        result = requestUtils.getAccountId(appRequestWithoutPublisher);
        Assert.assertNull(result);

        result = requestUtils.getAccountId(appRequestWithoutParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(accountId, result);
        Assert.assertEquals(publisherId, result);

        result = requestUtils.getAccountId(appRequestWithParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(publisherId, result);
        Assert.assertEquals(accountId, result);
    }

    @Test
    public void testGetParentAccountId() {
        String result = requestUtils.getParentAccountId(null);
        Assert.assertNull(result);

        result = requestUtils.getParentAccountId(siteRequestWithoutPublisher);
        Assert.assertNull(result);

        result = requestUtils.getParentAccountId(siteRequestWithoutParentAccountId);
        Assert.assertNull(result);

        result = requestUtils.getParentAccountId(siteRequestWithParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(publisherId, result);
        Assert.assertEquals(accountId, result);

        result = requestUtils.getParentAccountId(appRequestWithoutPublisher);
        Assert.assertNull(result);

        result = requestUtils.getParentAccountId(appRequestWithoutParentAccountId);
        Assert.assertNull(result);

        result = requestUtils.getParentAccountId(appRequestWithParentAccountId);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(publisherId, result);
        Assert.assertEquals(accountId, result);
    }

    @Test
    public void testGetStoredRequestId() {
        final String storedRequestId = "stored-request-id";

        String result = requestUtils.getStoredRequestId(null);
        Assert.assertNull(result);

        result = requestUtils.getStoredRequestIdFromExtRequest(null);
        Assert.assertNull(result);

        result = requestUtils.getStoredRequestIdFromExtRequestNode(null);
        Assert.assertNull(result);

        BidRequest bidRequest = BidRequest.builder().build();

        result = requestUtils.getStoredRequestId(bidRequest);
        Assert.assertNull(result);

        ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .build()
        );

        ObjectNode extRequestNode =
                (ObjectNode) jsonUtils.valueToTree(extRequest);

        bidRequest = BidRequest.builder().ext(extRequest).build();

        result = requestUtils.getStoredRequestId(bidRequest);
        Assert.assertNull(result);

        result = requestUtils.getStoredRequestIdFromExtRequest(extRequest);
        Assert.assertNull(result);

        result = requestUtils.getStoredRequestIdFromExtRequestNode(extRequestNode);
        Assert.assertNull(result);

        extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .storedrequest(
                                ExtStoredRequest.of(storedRequestId)
                        )
                        .build()
        );

        extRequestNode =
                (ObjectNode) jsonUtils.valueToTree(extRequest);

        bidRequest = BidRequest.builder().ext(extRequest).build();

        result = requestUtils.getStoredRequestId(bidRequest);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedRequestId, result);

        result = requestUtils.getStoredRequestIdFromExtRequest(extRequest);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedRequestId, result);

        result = requestUtils.getStoredRequestIdFromExtRequestNode(extRequestNode);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedRequestId, result);
    }

    @Test
    public void testGetStoredImpId() {
        final String storedImpId = "stored-imp-id";

        String result = requestUtils.getStoredImpId(null);
        Assert.assertNull(result);

        result = requestUtils.getStoredImpIdFromExtImp(null);
        Assert.assertNull(result);

        result = requestUtils.getStoredImpIdFromExtImpNode(null);
        Assert.assertNull(result);

        Imp imp = Imp.builder().build();

        result = requestUtils.getStoredImpId(imp);
        Assert.assertNull(result);

        ExtImp extImp = ExtImp.of(
                ExtImpPrebid.builder().build(), null
        );

        ObjectNode extImpNode =
                (ObjectNode) jsonUtils.valueToTree(extImp);

        imp = Imp.builder().ext(extImpNode).build();

        result = requestUtils.getStoredImpId(imp);
        Assert.assertNull(result);

        result = requestUtils.getStoredImpIdFromExtImp(extImp);
        Assert.assertNull(result);

        result = requestUtils.getStoredImpIdFromExtImpNode(extImpNode);
        Assert.assertNull(result);

        extImp = ExtImp.of(
                ExtImpPrebid.builder()
                        .storedrequest(
                                ExtStoredRequest.of(storedImpId)
                        )
                        .build(), null
        );

        extImpNode =
                (ObjectNode) jsonUtils.valueToTree(extImp);

        imp = Imp.builder().ext(extImpNode).build();

        result = requestUtils.getStoredImpId(imp);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedImpId, result);

        result = requestUtils.getStoredImpIdFromExtImp(extImp);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedImpId, result);

        result = requestUtils.getStoredImpIdFromExtImpNode(extImpNode);
        Assert.assertNotNull(result);
        Assert.assertEquals(storedImpId, result);
    }

    @Test
    public void testIsNonVastVideo() {
        boolean result = requestUtils.isNonVastVideo(null);
        Assert.assertFalse(result);

        final Imp emptyImp = Imp.builder()
                .id("1")
                .build();

        final Imp defaultImp = emptyImp.toBuilder()
                .ext(createImpExtNode(null))
                .build();

        final Imp videoImp = defaultImp.toBuilder()
                .video(Video.builder().build())
                .build();

        final Imp gVastImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder
                        .responseType(VastResponseType.gvast)
                ))
                .build();

        final Imp waterfallImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder.responseType(VastResponseType.waterfall)))
                .build();

        result = requestUtils.isNonVastVideo(emptyImp);
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(null, null);
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(emptyImp, null);
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(defaultImp);
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(defaultImp, jsonUtils.getImprovedigitalPbsImpExt(defaultImp));
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(videoImp);
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(videoImp, jsonUtils.getImprovedigitalPbsImpExt(videoImp));
        Assert.assertFalse(result);

        result = requestUtils.isNonVastVideo(gVastImp);
        Assert.assertTrue(result);

        result = requestUtils.isNonVastVideo(gVastImp, jsonUtils.getImprovedigitalPbsImpExt(gVastImp));
        Assert.assertTrue(result);

        result = requestUtils.isNonVastVideo(waterfallImp);
        Assert.assertTrue(result);

        result = requestUtils.isNonVastVideo(waterfallImp, jsonUtils.getImprovedigitalPbsImpExt(waterfallImp));
        Assert.assertTrue(result);
    }

    @Test
    public void testHasNonVastVideo() {
        boolean result = requestUtils.hasNonVastVideo(null);
        Assert.assertFalse(result);

        BidRequest emptyRequest = createRequest(null);
        result = requestUtils.hasNonVastVideo(emptyRequest);
        Assert.assertFalse(result);

        Imp emptyImp = Imp.builder()
                .id("1")
                .build();

        BidRequest requestWithEmptyImp = emptyRequest.toBuilder()
                .imp(List.of(emptyImp))
                .build();

        result = requestUtils.hasNonVastVideo(requestWithEmptyImp);
        Assert.assertFalse(result);

        final Imp defaultImp = emptyImp.toBuilder()
                .ext(createImpExtNode(null))
                .build();

        BidRequest requestWithDefaultImp = emptyRequest.toBuilder()
                .imp(List.of(defaultImp))
                .build();

        result = requestUtils.hasNonVastVideo(requestWithDefaultImp);
        Assert.assertFalse(result);

        final Imp videoImp = defaultImp.toBuilder()
                .video(Video.builder().build())
                .build();

        BidRequest requestWithVideoImp = emptyRequest.toBuilder()
                .imp(List.of(videoImp))
                .build();

        result = requestUtils.hasNonVastVideo(requestWithVideoImp);
        Assert.assertFalse(result);

        final Imp gVastImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder
                        .responseType(VastResponseType.gvast)
                ))
                .build();

        BidRequest requestWithGVastImp = emptyRequest.toBuilder()
                .imp(List.of(gVastImp))
                .build();

        result = requestUtils.hasNonVastVideo(requestWithGVastImp);
        Assert.assertTrue(result);

        final Imp waterfallImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder.responseType(VastResponseType.waterfall)))
                .build();

        BidRequest requestWithWaterfallImp = emptyRequest.toBuilder()
                .imp(List.of(waterfallImp))
                .build();

        result = requestUtils.hasNonVastVideo(requestWithWaterfallImp);
        Assert.assertTrue(result);
    }

    @Test
    public void testExtractBidderInfoAndGetImprovePlacementId() {
        // Test extractBidderInfo
        JsonNode bidderInfoNode = requestUtils.extractBidderInfo(null, null, null);
        Assert.assertTrue(bidderInfoNode.isMissingNode());

        final Imp emptyImp = Imp.builder()
                .id("1")
                .build();

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, null, null);
        Assert.assertTrue(bidderInfoNode.isMissingNode());

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, "bidder", null);
        Assert.assertTrue(bidderInfoNode.isMissingNode());

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, "bidder", "invalid-path");
        Assert.assertTrue(bidderInfoNode.isMissingNode());

        final Imp impWithBidder = emptyImp.toBuilder()
                .ext(createImpExtNode(null, true))
                .build();

        bidderInfoNode = requestUtils.extractBidderInfo(impWithBidder, "unknown", "invalid-path");
        Assert.assertTrue(bidderInfoNode.isMissingNode());

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> requestUtils.extractBidderInfo(
                        impWithBidder, "improvedigital", "invalid-json-ptr"
                ))
                .withMessageStartingWith("Invalid input: JSON Pointer expression must start with '/'");

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "improvedigital", "/invalid-property"
        );

        Assert.assertTrue(bidderInfoNode.isMissingNode());

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "improvedigital", "/placementId"
        );

        Assert.assertFalse(bidderInfoNode.isMissingNode());
        Assert.assertTrue(bidderInfoNode.isInt());
        Assert.assertEquals(20220325, bidderInfoNode.asInt());

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "pubmatic", "/publisherId"
        );

        Assert.assertFalse(bidderInfoNode.isMissingNode());
        Assert.assertTrue(bidderInfoNode.isTextual());
        Assert.assertEquals("156946", bidderInfoNode.asText());

        // Test getImprovePlacementId
        Integer placementId = requestUtils.getImprovePlacementId(null);
        Assert.assertNull(placementId);

        placementId = requestUtils.getImprovePlacementId(emptyImp);
        Assert.assertNull(placementId);

        placementId = requestUtils.getImprovePlacementId(impWithBidder);
        Assert.assertNotNull(placementId);
        Assert.assertEquals(20220325, (int) placementId);
    }

    private BidRequest createRequest(Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> modifier) {
        BidRequest.BidRequestBuilder builder = BidRequest.builder()
                .id("1");
        if (modifier != null) {
            builder = modifier.apply(builder);
        }
        return builder.build();
    }

    private ObjectNode createImpExtNode(
            Consumer<ImprovedigitalPbsImpExt.ImprovedigitalPbsImpExtBuilder> configConsumer
    ) {
        return createImpExtNode(configConsumer, false);
    }

    private ObjectNode createImpExtNode(
            Consumer<ImprovedigitalPbsImpExt.ImprovedigitalPbsImpExtBuilder> configConsumer,
            boolean withBidder
    ) {
        ObjectNode bidder = null;
        if (withBidder) {
            try {
                bidder = mapper.readValue("{\n"
                        + "        \"improvedigital\": {\n"
                        + "          \"placementId\": 20220325\n"
                        + "        },\n"
                        + "        \"appnexus\": {\n"
                        + "          \"placement_id\": 24195404\n"
                        + "        },\n"
                        + "        \"pubmatic\": {\n"
                        + "          \"publisherId\": \"156946\",\n"
                        + "          \"adSlot\": \"EA_Sims-Mobile_Android_RewardedVideo\"\n"
                        + "        }\n"
                        + "      }", ObjectNode.class);
            } catch (JsonProcessingException ignored) {
            }
        }
        ObjectNode impExtNode = mapper.valueToTree(
                ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(bidder)
                                .build(),
                        null
                )
        );
        if (configConsumer != null) {
            ImprovedigitalPbsImpExt.ImprovedigitalPbsImpExtBuilder configBuilder
                    = ImprovedigitalPbsImpExt.builder();
            configConsumer.accept(configBuilder);
            ((ObjectNode) impExtNode.at("/prebid"))
                    .set("improvedigitalpbs", mapper.valueToTree(configBuilder.build()));
        }
        return impExtNode;
    }
}
