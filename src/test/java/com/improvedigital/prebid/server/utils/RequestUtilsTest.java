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
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
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

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final JacksonMapper mapper = new JacksonMapper(objectMapper);
    protected final JsonMerger merger = new JsonMerger(mapper);
    protected final JsonUtils jsonUtils = new JsonUtils(mapper);
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
        Assertions.assertThat(result).isNull();

        result = requestUtils.getAccountId(siteRequestWithoutPublisher);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getAccountId(siteRequestWithoutParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(accountId);
        Assertions.assertThat(result).isEqualTo(publisherId);

        result = requestUtils.getAccountId(siteRequestWithParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(publisherId);
        Assertions.assertThat(result).isEqualTo(accountId);

        result = requestUtils.getAccountId(appRequestWithoutPublisher);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getAccountId(appRequestWithoutParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(accountId);
        Assertions.assertThat(result).isEqualTo(publisherId);

        result = requestUtils.getAccountId(appRequestWithParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(publisherId);
        Assertions.assertThat(result).isEqualTo(accountId);
    }

    @Test
    public void testGetParentAccountId() {
        String result = requestUtils.getParentAccountId(null);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getParentAccountId(siteRequestWithoutPublisher);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getParentAccountId(siteRequestWithoutParentAccountId);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getParentAccountId(siteRequestWithParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(publisherId);
        Assertions.assertThat(result).isEqualTo(accountId);

        result = requestUtils.getParentAccountId(appRequestWithoutPublisher);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getParentAccountId(appRequestWithoutParentAccountId);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getParentAccountId(appRequestWithParentAccountId);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isNotEqualTo(publisherId);
        Assertions.assertThat(result).isEqualTo(accountId);
    }

    @Test
    public void testGetStoredRequestId() {
        final String storedRequestId = "stored-request-id";

        String result = requestUtils.getStoredRequestId(null);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredRequestIdFromExtRequest(null);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredRequestIdFromExtRequestNode(null);
        Assertions.assertThat(result).isNull();

        BidRequest bidRequest = BidRequest.builder().build();

        result = requestUtils.getStoredRequestId(bidRequest);
        Assertions.assertThat(result).isNull();

        ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .build()
        );

        ObjectNode extRequestNode =
                (ObjectNode) jsonUtils.valueToTree(extRequest);

        bidRequest = BidRequest.builder().ext(extRequest).build();

        result = requestUtils.getStoredRequestId(bidRequest);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredRequestIdFromExtRequest(extRequest);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredRequestIdFromExtRequestNode(extRequestNode);
        Assertions.assertThat(result).isNull();

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
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedRequestId);

        result = requestUtils.getStoredRequestIdFromExtRequest(extRequest);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedRequestId);

        result = requestUtils.getStoredRequestIdFromExtRequestNode(extRequestNode);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedRequestId);
    }

    @Test
    public void testGetStoredImpId() {
        final String storedImpId = "stored-imp-id";

        String result = requestUtils.getStoredImpId(null);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredImpIdFromExtImp(null);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredImpIdFromExtImpNode(null);
        Assertions.assertThat(result).isNull();

        Imp imp = Imp.builder().build();

        result = requestUtils.getStoredImpId(imp);
        Assertions.assertThat(result).isNull();

        ExtImp extImp = ExtImp.of(
                ExtImpPrebid.builder().build(), null
        );

        ObjectNode extImpNode =
                (ObjectNode) jsonUtils.valueToTree(extImp);

        imp = Imp.builder().ext(extImpNode).build();

        result = requestUtils.getStoredImpId(imp);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredImpIdFromExtImp(extImp);
        Assertions.assertThat(result).isNull();

        result = requestUtils.getStoredImpIdFromExtImpNode(extImpNode);
        Assertions.assertThat(result).isNull();

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
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedImpId);

        result = requestUtils.getStoredImpIdFromExtImp(extImp);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedImpId);

        result = requestUtils.getStoredImpIdFromExtImpNode(extImpNode);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(storedImpId);
    }

    @Test
    public void testIsCustomVastVideo() {
        boolean result = requestUtils.isCustomVastVideo(null);
        Assertions.assertThat(result).isFalse();

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

        result = requestUtils.isCustomVastVideo(emptyImp);
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(null, null);
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(emptyImp, null);
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(defaultImp);
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(defaultImp, jsonUtils.getImprovedigitalPbsImpExt(defaultImp));
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(videoImp);
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(videoImp, jsonUtils.getImprovedigitalPbsImpExt(videoImp));
        Assertions.assertThat(result).isFalse();

        result = requestUtils.isCustomVastVideo(gVastImp);
        Assertions.assertThat(result).isTrue();

        result = requestUtils.isCustomVastVideo(gVastImp, jsonUtils.getImprovedigitalPbsImpExt(gVastImp));
        Assertions.assertThat(result).isTrue();

        result = requestUtils.isCustomVastVideo(waterfallImp);
        Assertions.assertThat(result).isTrue();

        result = requestUtils.isCustomVastVideo(waterfallImp, jsonUtils.getImprovedigitalPbsImpExt(waterfallImp));
        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testHasCustomVastVideo() {
        boolean result = requestUtils.hasCustomVastVideo(null);
        Assertions.assertThat(result).isFalse();

        BidRequest emptyRequest = createRequest(null);
        result = requestUtils.hasCustomVastVideo(emptyRequest);
        Assertions.assertThat(result).isFalse();

        Imp emptyImp = Imp.builder()
                .id("1")
                .build();

        BidRequest requestWithEmptyImp = emptyRequest.toBuilder()
                .imp(List.of(emptyImp))
                .build();

        result = requestUtils.hasCustomVastVideo(requestWithEmptyImp);
        Assertions.assertThat(result).isFalse();

        final Imp defaultImp = emptyImp.toBuilder()
                .ext(createImpExtNode(null))
                .build();

        BidRequest requestWithDefaultImp = emptyRequest.toBuilder()
                .imp(List.of(defaultImp))
                .build();

        result = requestUtils.hasCustomVastVideo(requestWithDefaultImp);
        Assertions.assertThat(result).isFalse();

        final Imp videoImp = defaultImp.toBuilder()
                .video(Video.builder().build())
                .build();

        BidRequest requestWithVideoImp = emptyRequest.toBuilder()
                .imp(List.of(videoImp))
                .build();

        result = requestUtils.hasCustomVastVideo(requestWithVideoImp);
        Assertions.assertThat(result).isFalse();

        final Imp gVastImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder
                        .responseType(VastResponseType.gvast)
                ))
                .build();

        BidRequest requestWithGVastImp = emptyRequest.toBuilder()
                .imp(List.of(gVastImp))
                .build();

        result = requestUtils.hasCustomVastVideo(requestWithGVastImp);
        Assertions.assertThat(result).isTrue();

        final Imp waterfallImp = videoImp.toBuilder()
                .ext(createImpExtNode(builder -> builder.responseType(VastResponseType.waterfall)))
                .build();

        BidRequest requestWithWaterfallImp = emptyRequest.toBuilder()
                .imp(List.of(waterfallImp))
                .build();

        result = requestUtils.hasCustomVastVideo(requestWithWaterfallImp);
        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testExtractBidderInfoAndGetImprovePlacementId() {
        // Test extractBidderInfo
        JsonNode bidderInfoNode = requestUtils.extractBidderInfo(null, null, null);
        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        final Imp emptyImp = Imp.builder()
                .id("1")
                .build();

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, null, null);
        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, "bidder", null);
        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        bidderInfoNode = requestUtils.extractBidderInfo(emptyImp, "bidder", "invalid-path");
        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        final Imp impWithBidder = emptyImp.toBuilder()
                .ext(createImpExtNode(null, true))
                .build();

        bidderInfoNode = requestUtils.extractBidderInfo(impWithBidder, "unknown", "invalid-path");
        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "improvedigital", "/invalid-property"
        );

        Assertions.assertThat(bidderInfoNode.isMissingNode()).isTrue();

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "improvedigital", "/placementId"
        );

        Assertions.assertThat(bidderInfoNode.isMissingNode()).isFalse();
        Assertions.assertThat(bidderInfoNode.isInt()).isTrue();
        Assertions.assertThat(bidderInfoNode.asInt()).isEqualTo(20220325);

        bidderInfoNode = requestUtils.extractBidderInfo(
                impWithBidder, "pubmatic", "/publisherId"
        );

        Assertions.assertThat(bidderInfoNode.isMissingNode()).isFalse();
        Assertions.assertThat(bidderInfoNode.isTextual()).isTrue();
        Assertions.assertThat(bidderInfoNode.asText()).isEqualTo("156946");

        // Test getImprovePlacementId
        Integer placementId = requestUtils.getImprovedigitalPlacementId(null);
        Assertions.assertThat(placementId).isNull();

        placementId = requestUtils.getImprovedigitalPlacementId(emptyImp);
        Assertions.assertThat(placementId).isNull();

        placementId = requestUtils.getImprovedigitalPlacementId(impWithBidder);
        Assertions.assertThat(placementId).isNotNull();
        Assertions.assertThat(placementId).isEqualTo(20220325);
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
                bidder = objectMapper.readValue("{\n"
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
        ObjectNode impExtNode = objectMapper.valueToTree(
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
                    .set("improvedigitalpbs", objectMapper.valueToTree(configBuilder.build()));
        }
        return impExtNode;
    }
}
