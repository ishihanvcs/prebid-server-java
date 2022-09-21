package com.improvedigital.prebid.server.it;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.customvast.model.Floor;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.specification.RequestSpecification;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.it.IntegrationTest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.ObjectUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("checkstyle:hideutilityclassconstructor")
@SpringBootApplication(
        scanBasePackages = {
                "org.prebid.server",
                "com.improvedigital.prebid.server"
        }
)
@TestPropertySource(properties = {
        "settings.filesystem.stored-imps-dir=src/test/resources/com/improvedigital/prebid/server/it/storedimps",
        "settings.filesystem.settings-filename=src/test/resources/com/improvedigital/prebid/server/it/settings.yaml",
        "settings.targeting.truncate-attr-chars=20",
})
public class ImprovedigitalIntegrationTest extends IntegrationTest {

    @Autowired
    protected BidderCatalog bidderCatalog;

    protected static final String IT_TEST_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.60 Safari/537.36";
    protected static final String IT_TEST_IP = "193.168.244.1";
    protected static final String IT_TEST_DOMAIN = "pbs.improvedigital.com";
    protected static final String IT_TEST_MAIN_DOMAIN = "improvedigital.com";
    protected static final String IT_TEST_CACHE_URL = "http://localhost:8090/cache";

    protected static final String GAM_NETWORK_CODE = "1015413";

    protected static final ObjectMapper BID_REQUEST_MAPPER = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    protected static final ObjectMapper BID_RESPONSE_MAPPER = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    protected String jsonFromFileWithMacro(String file, Map<String, String> macrosInFileContent)
            throws IOException {
        String fileContent = mapper.writeValueAsString(
                mapper.readTree(this.getClass().getResourceAsStream(file))
        );

        // Replace all occurrences of <key>s by it's <value> of map.
        if (macrosInFileContent != null) {
            return macrosInFileContent.entrySet().stream()
                    .map(m -> (Function<String, String>) s -> s.replace(m.getKey(), m.getValue()))
                    .reduce(Function.identity(), Function::andThen)
                    .apply(fileContent);
        }

        return fileContent;
    }

    protected static RequestSpecification specWithPBSHeader(int port) {
        return given(spec(port))
                .header("Referer", "http://" + IT_TEST_DOMAIN)
                .header("X-Forwarded-For", IT_TEST_IP)
                .header("User-Agent", IT_TEST_USER_AGENT)
                .header("Origin", "http://" + IT_TEST_DOMAIN);
    }

    private static RequestSpecification spec(int port) {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setConfig(RestAssuredConfig.config()
                        .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
                .build();
    }

    protected Set<String> getAllActiveBidders() {
        return bidderCatalog.names().stream()
                .filter(n -> bidderCatalog.isActive(n))
                .collect(Collectors.toSet());
    }

    protected String getAuctionBidRequestBanner(String uniqueId, AuctionBidRequestBannerTestData bidRequestData) {
        // We do not want version, complete when no nodes are there.
        final ExtSource reqSourceExt = CollectionUtils.isEmpty(bidRequestData.schainNodes) ? null : ExtSource.of(
                ExtSourceSchain.of(
                        bidRequestData.schainVer,
                        bidRequestData.schainComplete,
                        bidRequestData.schainNodes,
                        null
                )
        );

        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(bidRequestData.impExt == null ? null : bidRequestData.impExt.get())
                        .banner(bidRequestData.bannerData == null ? null : Banner.builder()
                                .w(bidRequestData.bannerData.w)
                                .h(bidRequestData.bannerData.h)
                                .mimes(bidRequestData.bannerData.mimes)
                                .build())
                        .xNative(bidRequestData.nativeData == null ? null : Native.builder()
                                .request(toJsonString(mapper, bidRequestData.nativeData.request))
                                .ver(StringUtils.defaultString(bidRequestData.nativeData.ver, "1.2"))
                                .build())
                        .bidfloor(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloor))
                        .bidfloorcur(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloorCur))
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .publisher(Publisher.builder()
                                        .id(bidRequestData.publisherId)
                                        .build())
                                .build())
                        .cur(Arrays.asList(bidRequestData.currency))
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent(bidRequestData.gdprConsent)
                                        .build())
                                .build())
                        .source(Source.builder()
                                .tid("source_tid_" + uniqueId)
                                .ext(reqSourceExt)
                                .build())
                        .test(bidRequestData.test)
                        .build()
        );
    }

    protected String getSSPBidRequestBanner(String uniqueId, SSPBidRequestBannerTestData bidRequestData) {
        return getSSPBidRequestBanner(uniqueId, bidRequestData, null);
    }

    protected String getSSPBidRequestBanner(
            String uniqueId, SSPBidRequestBannerTestData bidRequestData, Function<BidRequest, BidRequest> reqModifier) {
        // We do not want version, complete when no nodes are there.
        final ExtSource reqSourceExt = CollectionUtils.isEmpty(bidRequestData.schainNodes) ? null : ExtSource.of(
                ExtSourceSchain.of(
                        bidRequestData.schainVer,
                        bidRequestData.schainComplete,
                        bidRequestData.schainNodes,
                        null
                )
        );

        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(bidRequestData.impExt == null ? null : bidRequestData.impExt.get())
                        .banner(bidRequestData.bannerData == null ? null : Banner.builder()
                                .w(bidRequestData.bannerData.w)
                                .h(bidRequestData.bannerData.h)
                                .mimes(bidRequestData.bannerData.mimes)
                                .build())
                        .xNative(bidRequestData.nativeData == null ? null : Native.builder()
                                .request(toJsonString(mapper, bidRequestData.nativeData.request))
                                .ver(StringUtils.defaultString(bidRequestData.nativeData.ver, "1.2"))
                                .build())
                        .bidfloor(ObjectUtil.getIfNotNullOrDefault(
                                bidRequestData.floor, Floor::getBidFloor, () -> BigDecimal.ZERO
                        ))
                        .bidfloorcur(ObjectUtil.getIfNotNullOrDefault(
                                bidRequestData.floor, Floor::getBidFloorCur, () -> bidRequestData.currency
                        ))
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .domain(IT_TEST_DOMAIN)
                                .page("http://" + IT_TEST_DOMAIN)
                                .publisher(Publisher.builder()
                                        .id(bidRequestData.publisherId)
                                        .domain(IT_TEST_MAIN_DOMAIN)
                                        .build())
                                .ext(ExtSite.of(0, null))
                                .build())
                        .device(Device.builder()
                                .ua(IT_TEST_USER_AGENT)
                                .ip(IT_TEST_IP)
                                .build())
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent(bidRequestData.gdprConsent)
                                        .build())
                                .build())
                        .at(1)
                        .tmax(5000L)
                        .cur(Arrays.asList(bidRequestData.currency))
                        .regs(Regs.of(null, ExtRegs.of(0, null)))
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .pbs(ExtRequestPrebidPbs.of("/openrtb2/auction"))
                                .server(ExtRequestPrebidServer.of(
                                        "http://localhost:8080", 1, "local"
                                ))
                                .build()))
                        .source(Source.builder()
                                .tid("source_tid_" + uniqueId)
                                .ext(reqSourceExt)
                                .build())
                        .build(),
                reqModifier
        );
    }

    protected String getAuctionBidRequestVideo(String uniqueId, AuctionBidRequestVideoTestData bidRequestData) {
        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(bidRequestData.impExt == null ? null : bidRequestData.impExt.get())
                        .video(Video.builder()
                                .protocols(bidRequestData.getVideoProtocols(2))
                                .w(640)
                                .h(480)
                                .mimes(Arrays.asList("video/mp4"))
                                .minduration(1)
                                .maxduration(60)
                                .linearity(1)
                                .placement(5)
                                .build())
                        .bidfloor(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloor))
                        .bidfloorcur(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloorCur))
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .cur(Arrays.asList(bidRequestData.currency))
                        .site(Site.builder()
                                .cat(bidRequestData.siteIABCategories)
                                .build())
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent(bidRequestData.gdprConsent)
                                        .build())
                                .build())
                        .build()
        );
    }

    protected String getSSPBidRequestVideo(String uniqueId, SSPBidRequestVideoTestData bidRequestData) {
        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(bidRequestData.impExt == null ? null : bidRequestData.impExt.get())
                        .video(Video.builder()
                                .protocols(bidRequestData.getVideoProtocols(2))
                                .w(640)
                                .h(480)
                                .mimes(Arrays.asList("video/mp4"))
                                .minduration(1)
                                .maxduration(60)
                                .linearity(1)
                                .placement(5)
                                .build())
                        .bidfloor(ObjectUtil.getIfNotNullOrDefault(
                                bidRequestData.floor, Floor::getBidFloor, () -> BigDecimal.ZERO
                        ))
                        .bidfloorcur(ObjectUtil.getIfNotNullOrDefault(
                                bidRequestData.floor, Floor::getBidFloorCur, () -> bidRequestData.currency
                        ))
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .domain(IT_TEST_DOMAIN)
                                .page("http://" + IT_TEST_DOMAIN)
                                .publisher(Publisher.builder()
                                        .domain(IT_TEST_MAIN_DOMAIN)
                                        .build())
                                .ext(ExtSite.of(0, null))
                                .cat(bidRequestData.siteIABCategories)
                                .build())
                        .device(Device.builder()
                                .ua(IT_TEST_USER_AGENT)
                                .ip(IT_TEST_IP)
                                .build())
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent(bidRequestData.gdprConsent)
                                        .build())
                                .build())
                        .at(1)
                        .tmax(5000L)
                        .cur(Arrays.asList(bidRequestData.currency))
                        .regs(Regs.of(null, ExtRegs.of(0, null)))
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .pbs(ExtRequestPrebidPbs.of("/openrtb2/auction"))
                                .server(ExtRequestPrebidServer.of(
                                        "http://localhost:8080", 1, "local"
                                ))
                                .targeting(bidRequestData.extRequestTargeting)
                                .cache(bidRequestData.extRequestPrebidCache).build()))
                        .build()
        );
    }

    protected String getBidRequestWeb(
            String uniqueId, Function<Imp, Imp> impModifier, Function<BidRequest, BidRequest>... bidModifiers
    ) {
        Imp imp = Imp.builder()
                .id("imp_id_" + uniqueId)
                .build();

        if (impModifier != null) {
            imp = impModifier.apply(imp);
        }

        BidRequest bidRequest = BidRequest.builder()
                .id("request_id_" + uniqueId)
                .source(Source.builder()
                        .tid("source_tid_" + uniqueId)
                        .build())
                .tmax(5000L)
                .regs(Regs.of(null, ExtRegs.of(0, null)))
                .imp(Arrays.asList(imp))
                .build();

        if (bidModifiers != null) {
            for (Function<BidRequest, BidRequest> m : bidModifiers) {
                if (m != null) {
                    bidRequest = m.apply(bidRequest);
                }
            }
        }

        return toJsonString(BID_REQUEST_MAPPER, bidRequest);
    }

    protected String getBidResponse(
            String bidderName,
            String uniqueId,
            String currency,
            BidResponseTestData... data
    ) {
        BidResponse bidResponse = BidResponse.builder()
                .id("request_id_" + uniqueId) /* request id is tied to the bid request. */
                .cur(currency)
                .seatbid(Arrays.asList(SeatBid.builder()
                        .bid(IntStream.range(0, data.length).mapToObj(i ->
                                Bid.builder()
                                        .id("bid_id_" + bidderName + "_" + i + "_" + uniqueId)
                                        .impid("imp_id_" + uniqueId) /* imp id is tied to the bid request. */
                                        .price(BigDecimal.valueOf(data[i].price).setScale(2, RoundingMode.HALF_EVEN))
                                        .adm(data[i].adm)
                                        .cid("campaign_id_" + i + "_" + uniqueId)
                                        .adid("ad_id_" + i + "_" + uniqueId)
                                        .crid("creative_id_" + i + "_" + uniqueId)
                                        .ext(data[i].bidExt == null ? null : data[i].bidExt.get())
                                        .build()
                        ).collect(Collectors.toList()))
                        .build()
                ))
                .build();
        return toJsonString(BID_RESPONSE_MAPPER, bidResponse);
    }

    protected String toJsonString(Object obj) {
        return toJsonString(mapper, obj);
    }

    protected String toJsonString(ObjectMapper mapper, Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            fail("Not expecting any exception while converting to json string but got: " + e.getMessage());
            return null;
        }
    }

    /**
     * This class contains full list of fields that we will use for IT test case.
     * Some test case may set subset of the fields.
     */
    @Builder(toBuilder = true)
    public static class AuctionBidRequestBannerTestData {
        String currency;

        AuctionBidRequestImpExt impExt;

        BannerTestParam bannerData;

        NativeTestParam nativeData;

        String gdprConsent;

        String schainVer;

        Integer schainComplete;

        List<ExtRequestPrebidSchainSchainNode> schainNodes;

        Integer test;

        String publisherId;

        Floor floor;
    }

    /**
     * This class contains full list of fields that we will use for IT test case.
     * Some test case may set subset of the fields.
     */
    @Builder(toBuilder = true)
    public static class SSPBidRequestBannerTestData {
        String currency;

        SSPBidRequestImpExt impExt;

        BannerTestParam bannerData;

        NativeTestParam nativeData;

        String gdprConsent;

        String schainVer;

        Integer schainComplete;

        List<ExtRequestPrebidSchainSchainNode> schainNodes;

        String publisherId;

        Floor floor;
    }

    /**
     * This class contains full list of fields that we will use for IT test case.
     * Some test case may set subset of the fields.
     */
    @Builder(toBuilder = true)
    public static class AuctionBidRequestVideoTestData {
        String currency;

        AuctionBidRequestImpExt impExt;

        List<Integer> videoProtocols;

        List<String> siteIABCategories;

        String gdprConsent;

        Floor floor;

        @JsonIgnore
        public List<Integer> getVideoProtocols(int defaultProtocol) {
            return CollectionUtils.isEmpty(videoProtocols) ? Arrays.asList(defaultProtocol) : videoProtocols;
        }
    }

    /**
     * This class contains full list of fields that we will use for IT test case.
     * Some test case may set subset of the fields.
     */
    @Builder(toBuilder = true)
    public static class SSPBidRequestVideoTestData {
        String currency;

        SSPBidRequestImpExt impExt;

        ExtRequestTargeting extRequestTargeting;

        ExtRequestPrebidCache extRequestPrebidCache;

        List<Integer> videoProtocols;

        List<String> siteIABCategories;

        String gdprConsent;

        Floor floor;

        @JsonIgnore
        public List<Integer> getVideoProtocols(int defaultProtocol) {
            return CollectionUtils.isEmpty(videoProtocols) ? Arrays.asList(defaultProtocol) : videoProtocols;
        }
    }

    /**
     * This class contains full list of fields for bid response that we will use for IT test case.
     * Some test case may set subset of the fields.
     */
    @Builder(toBuilder = true)
    public static class BidResponseTestData {
        double price;

        String adm;

        BidResponseBidExt bidExt;
    }

    @Builder(toBuilder = true)
    public static class NativeTestParam {
        Request request;
        String ver;
    }

    @Builder(toBuilder = true)
    public static class BannerTestParam {
        int w;
        int h;
        List<String> mimes;
    }

    /**
     * Java implementation for the json:
     * <pre>
     *     "ext": {
     *         "prebid": {
     *             "storedrequest": {
     *                 "id": "...."
     *             }
     *         },
     *         "bidder": {
     *             "placementId": ....
     *         }
     *     }
     * </pre>
     */
    public static class SSPBidRequestImpExt {
        private ObjectNode impExt;

        public SSPBidRequestImpExt() {
            this.impExt = BID_REQUEST_MAPPER.valueToTree(
                    ExtImp.of(ExtImpPrebid.builder().build(), null)
            );
        }

        public ObjectNode get() {
            return impExt;
        }

        public SSPBidRequestImpExt putStoredRequest(String storedRequestId) {
            if (storedRequestId != null) {
                ((ObjectNode) impExt.at("/prebid"))
                        .putObject("storedrequest")
                        .put("id", storedRequestId);
            }
            return this;
        }

        public SSPBidRequestImpExt putBidder() {
            impExt.putObject("bidder");
            return this;
        }

        public SSPBidRequestImpExt putBidderKeyValue(String key, String value) {
            if (value != null) {
                ((ObjectNode) impExt.at("/bidder")).put(key, value);
            }
            return this;
        }

        public SSPBidRequestImpExt putBidderKeyValue(String key, Integer value) {
            if (value != null) {
                ((ObjectNode) impExt.at("/bidder")).put(key, value);
            }
            return this;
        }

        public SSPBidRequestImpExt putBidderKeyValue(String key, Map<String, ?> values) {
            if (values != null) {
                ((ObjectNode) impExt.at("/bidder")).putIfAbsent(key, BID_REQUEST_MAPPER.valueToTree(values));
            }
            return this;
        }
    }

    /**
     * Java implementation for the json:
     * <pre>
     *     "ext": {
     *         "prebid": {
     *             "storedrequest": {
     *                 "id": "...."
     *             }
     *            "improvedigitalpbs": {
     *                 "....": "...."
     *            }
     *         },
     *         "generic": {
     *            "exampleProperty": "examplePropertyValue"
     *         },
     *         "improvedigital": {
     *            "placementId": ...
     *         }
     *     }
     * </pre>
     */
    public static class AuctionBidRequestImpExt {
        private ObjectNode impExt;

        public AuctionBidRequestImpExt() {
            this.impExt = BID_REQUEST_MAPPER.valueToTree(
                    ExtImp.of(ExtImpPrebid.builder().build(), null)
            );
        }

        public ObjectNode get() {
            return impExt;
        }

        public AuctionBidRequestImpExt putStoredRequest(String storedRequestId) {
            if (storedRequestId != null) {
                ((ObjectNode) impExt.at("/prebid"))
                        .putObject("storedrequest")
                        .put("id", storedRequestId);
            }
            return this;
        }

        public AuctionBidRequestImpExt putImprovedigitalPbs() {
            ((ObjectNode) impExt.at("/prebid")).putObject("improvedigitalpbs");
            return this;
        }

        public AuctionBidRequestImpExt putImprovedigitalPbsKeyValue(String key, String value) {
            if (value != null) {
                ((ObjectNode) impExt.at("/prebid/improvedigitalpbs")).put(key, value);
            }
            return this;
        }

        public AuctionBidRequestImpExt putImprovedigitalPbsKeyValue(String key, List<String> values) {
            if (values != null) {
                ((ObjectNode) impExt.at("/prebid/improvedigitalpbs")).putIfAbsent(
                        key, BID_REQUEST_MAPPER.valueToTree(values)
                );
            }
            return this;
        }

        public AuctionBidRequestImpExt putImprovedigitalPbsKeyValue(String key, Map<String, ?> values) {
            if (values != null) {
                ((ObjectNode) impExt.at("/prebid/improvedigitalpbs")).putIfAbsent(
                        key, BID_REQUEST_MAPPER.valueToTree(values)
                );
            }
            return this;
        }

        public AuctionBidRequestImpExt putImprovedigitalPbsAt(String pathAt, String key, Map<String, ?> values) {
            if (values != null) {
                ((ObjectNode) impExt.at("/prebid/improvedigitalpbs/" + pathAt)).putIfAbsent(
                        key, BID_REQUEST_MAPPER.valueToTree(values)
                );
            }
            return this;
        }

        public AuctionBidRequestImpExt putBidder(String bidderName) {
            impExt.putObject(bidderName);
            return this;
        }

        public AuctionBidRequestImpExt putBidderKeyValue(String bidderName, String key, String value) {
            if (value != null) {
                ((ObjectNode) impExt.at("/" + bidderName)).put(key, value);
            }
            return this;
        }

        public AuctionBidRequestImpExt putBidderKeyValue(String bidderName, String key, Integer value) {
            if (value != null) {
                ((ObjectNode) impExt.at("/" + bidderName)).put(key, value);
            }
            return this;
        }

        public AuctionBidRequestImpExt putBidderKeyValue(String bidderName, String key, Map<String, ?> values) {
            if (values != null) {
                ((ObjectNode) impExt.at("/" + bidderName)).putIfAbsent(key, BID_REQUEST_MAPPER.valueToTree(values));
            }
            return this;
        }
    }

    /**
     * Java implementation for the json:
     * <pre>
     *     "ext": {
     *         "improvedigital": {
     *             "buying_type": "classic",
     *             "line_item_id": 202206170
     *         }
     *     }
     * </pre>
     */
    public static class BidResponseBidExt {
        private ObjectNode bidExt;

        public BidResponseBidExt() {
            this.bidExt = BID_REQUEST_MAPPER.valueToTree(
                    ImprovedigitalBidExt.of(ImprovedigitalBidExtImprovedigital.builder().build())
            );
        }

        public ObjectNode get() {
            return bidExt;
        }

        public BidResponseBidExt putBuyingType(String buyingType) {
            if (buyingType != null) {
                ((ObjectNode) bidExt.at("/improvedigital")).put("buying_type", buyingType);
            }
            return this;
        }

        public BidResponseBidExt putLineItemId(Integer lineItemId) {
            if (lineItemId != null) {
                ((ObjectNode) bidExt.at("/improvedigital")).put("line_item_id", lineItemId);
            }
            return this;
        }
    }

    protected String getCacheIdRandom() {
        return UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "_");
    }

    protected String createCacheRequest(
            String requestId,
            String... cacheContents
    ) throws IOException {
        List<String> cachePutObjects = Arrays.stream(cacheContents).map(content -> "{"
                + "  \"aid\": \"" + requestId + "\","
                + "  \"type\": \"xml\","
                + "  \"value\": \"" + content + "\""
                + "}"
        ).collect(Collectors.toList());

        return "{"
                + "\"puts\": ["
                + String.join(",", cachePutObjects)
                + "]"
                + "}";
    }

    protected String createCacheResponse(String... cacheIds) {
        List<String> cachePutObjects = Arrays.stream(cacheIds).map(cacheId -> "{"
                + "  \"uuid\": \"" + cacheId + "\""
                + "}"
        ).collect(Collectors.toList());

        return "{"
                + "\"responses\": ["
                + String.join(",", cachePutObjects)
                + "]"
                + "}";
    }

    protected void assertCachedContentFromCacheId(String uniqueId, String expectedCachedContent) throws IOException {
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=" + uniqueId, expectedCachedContent);
    }

    protected void assertCachedContent(String cacheUrl, String expectedCachedContent) throws IOException {
        assertThat(IOUtils.toString(
                HttpClientBuilder.create().build().execute(
                        new HttpGet(cacheUrl)
                ).getEntity().getContent(), "UTF-8"
        )).isEqualTo(expectedCachedContent);
    }

    protected String createResourceFile(String resourceFilePathFromSlash, String fileContent) throws IOException {
        Path cacheResponseFile = Paths.get(this.getClass().getResource("/").getPath() + resourceFilePathFromSlash);
        if (!Files.exists(cacheResponseFile)) {
            Files.createFile(cacheResponseFile);
        }
        Files.writeString(cacheResponseFile, fileContent, StandardOpenOption.WRITE);

        return "/" + resourceFilePathFromSlash;
    }

    protected Map<String, List<String>> splitQuery(String queryParam) {
        return Arrays.stream(queryParam.split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleImmutableEntry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
                );
    }

    protected AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        try {
            final String[] idx = it.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(
                    URLDecoder.decode(idx[0], "UTF-8"),
                    URLDecoder.decode(idx.length > 1 ? idx[1] : "", "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void assertQuerySingleValue(List<String> paramValues, String expectedValue) {
        assertThat(paramValues.size()).isEqualTo(1);
        assertThat(paramValues.get(0)).isEqualTo(expectedValue);
    }

    protected static String getVastXmlInline(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>";
    }

    protected String getVastXmlInlineWithMultipleAds(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-1" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-1" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-1" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 2</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-2" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:30</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-2" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-2" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>";
    }

    protected String getVastXmlWrapper(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>";
    }

    protected String getVastXmlWrapperWithMultipleAds(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-1" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-2" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>";
    }

    protected void assertBidCountIsOne(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isOne();

        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isOne();
    }

    protected void assertBidCountIsZero(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length()).isZero();
    }

    protected void assertBidCountIsOneOrMore(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isGreaterThanOrEqualTo(1);

        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isGreaterThanOrEqualTo(1);
    }

    @NotNull
    protected String getAdm(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("adm");
    }

    protected void assertBidExtPrebidType(
            JSONObject responseJson, int seatBidIndex, int bidIndex, String expectedExtPrebidType
    ) throws JSONException {
        assertThat(getBidExtPrebidType(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedExtPrebidType);
    }

    @NotNull
    protected String getBidExtPrebidType(
            JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBidExtPrebid(responseJson, seatBidIndex, bidIndex)
                .getString("type");
    }

    @NotNull
    protected JSONObject getBidExtPrebid(
            JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getJSONObject("ext")
                .getJSONObject("prebid");
    }

    protected void assertBidIdExists(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        assertThat(getBidId(responseJson, seatBidIndex, bidIndex)).isNotEmpty();
    }

    @NotNull
    protected String getBidId(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("id");
    }

    protected void assertBidImpId(
            JSONObject responseJson, int seatBidIndex, int bidIndex, String expectedImpId) throws JSONException {
        assertThat(getBidImpId(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedImpId);
    }

    @NotNull
    protected String getBidImpId(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getString("impid");
    }

    protected void assertBidPrice(
            JSONObject responseJson, int seatBidIndex, int bidIndex, double expectedBidPrice) throws JSONException {
        assertThat(getBidPrice(responseJson, seatBidIndex, bidIndex)).isEqualTo(
                new BigDecimal(expectedBidPrice).setScale(4, RoundingMode.HALF_EVEN).doubleValue()
        );
    }

    @NotNull
    protected Double getBidPrice(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getDouble("price");
    }

    @NotNull
    protected JSONObject getBid(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getSeatbid(responseJson, seatBidIndex)
                .getJSONArray("bid").getJSONObject(bidIndex);
    }

    protected void assertSeat(JSONObject responseJson, int seatBidIndex, String expectedSeat) throws JSONException {
        assertThat(getSeat(responseJson, seatBidIndex)).isEqualTo(expectedSeat);
    }

    @NotNull
    protected String getSeat(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return getSeatbid(responseJson, seatBidIndex)
                .getString("seat");
    }

    @NotNull
    protected JSONObject getSeatbid(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return responseJson
                .getJSONArray("seatbid").getJSONObject(seatBidIndex);
    }

    protected void assertCurrency(JSONObject responseJson, String expectedCurrency) throws JSONException {
        assertThat(responseJson.getString("cur")).isEqualTo(expectedCurrency);
    }

    protected void assertNoExtErrors(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONObject("ext").has("errors")).isFalse();
    }
}
