package com.improvedigital.prebid.server.it;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
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
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.TitleObject;
import com.improvedigital.prebid.server.customvast.model.Floor;
import com.improvedigital.prebid.server.it.transformers.BidResponseByImpidTransformer;
import com.improvedigital.prebid.server.it.transformers.BidResponseFunctionByImpidTransformer;
import com.improvedigital.prebid.server.it.transformers.CacheGetByUuidTransformer;
import com.improvedigital.prebid.server.it.transformers.CacheSetByContentTransformer;
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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.ObjectUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource({
        "/com/improvedigital/prebid/server/it/config/improvedigital-it-application.properties",
        "/com/improvedigital/prebid/server/it/config/test-application-improvedigital-hooks.properties"
})
public class ImprovedigitalIntegrationTest extends VertxTest {

    @Autowired
    protected BidderCatalog bidderCatalog;

    protected final String defaultAccountId = "empty-account";
    protected static final String IT_TEST_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.60 Safari/537.36";
    protected static final String IT_TEST_IP = "193.168.244.1";
    protected static final String IT_TEST_DOMAIN = "pbs.improvedigital.com";
    protected static final String IT_TEST_MAIN_DOMAIN = "improvedigital.com";
    protected static final String IT_TEST_CACHE_URL = "http://localhost:8090/cache";

    protected static final String GAM_NETWORK_CODE = "1015413";

    // The exchange rate is hard coded (src/test/resources/com/improvedigital/prebid/server/it/currency/latest.json).
    private static final double IT_TEST_USD_TO_EUR_RATE = 0.8777319407;

    protected static final ObjectMapper BID_REQUEST_MAPPER = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    protected static final ObjectMapper BID_RESPONSE_MAPPER = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final int WIREMOCK_PORT = 8090;

    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(options()
            .port(WIREMOCK_PORT)
            .gzipDisabled(true)
            .jettyStopTimeout(5000L)
            .extensions(
                    BidResponseByImpidTransformer.class,
                    BidResponseFunctionByImpidTransformer.class,
                    CacheSetByContentTransformer.class,
                    CacheGetByUuidTransformer.class
            ));

    @BeforeClass
    public static void setUp() throws IOException {
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom(
                        "/com/improvedigital/prebid/server/it/storedrequests/test-periodic-refresh.json"
                ))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom(
                        "/com/improvedigital/prebid/server/it/currency/latest.json"
                ))));
    }

    protected static RequestSpecification specWithPBSHeader(int port) {
        return specWithPBSHeader(port, "2.5");
    }

    protected static RequestSpecification specWithPBSHeader(int port, String rtbVersion) {
        return given(spec(port))
                .header("Referer", "http://" + IT_TEST_DOMAIN)
                .header("X-Forwarded-For", IT_TEST_IP)
                .header("User-Agent", IT_TEST_USER_AGENT)
                .header("x-openrtb-version", rtbVersion)
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

    protected static String jsonFrom(String file) throws IOException {
        return mapper.writeValueAsString(mapper.readTree(
                ImprovedigitalIntegrationTest.class.getResourceAsStream(file)
        ));
    }

    protected Set<String> getAllActiveBidders() {
        return bidderCatalog.names().stream()
                .filter(n -> bidderCatalog.isActive(n))
                .collect(Collectors.toSet());
    }

    protected String getAuctionBidRequest(String uniqueId, AuctionBidRequestTestData bidRequestData) {
        return getAuctionBidRequest(uniqueId, bidRequestData, null);
    }

    protected String getAuctionBidRequest(
            String uniqueId,
            AuctionBidRequestTestData bidRequestData,
            Function<BidRequest, BidRequest> bidReqModifier
    ) {
        final String publisherId = StringUtils.defaultIfBlank(bidRequestData.publisherId, defaultAccountId);

        BidRequest bidRequest = BidRequest.builder()
                .id("request_id_" + uniqueId)
                .imp(bidRequestData.imps.stream()
                        .map(anImp -> Imp.builder()
                                .id(anImp.impData.id)
                                .ext(anImp.impExt.get())
                                .banner(anImp.impData.bannerData == null ? null : Banner.builder()
                                        .w(anImp.impData.bannerData.w)
                                        .h(anImp.impData.bannerData.h)
                                        .mimes(anImp.impData.bannerData.mimes)
                                        .build())
                                .xNative(anImp.impData.nativeData == null ? null : Native.builder()
                                        .request(toJsonString(mapper, anImp.impData.nativeData.request))
                                        .ver(StringUtils.defaultString(anImp.impData.nativeData.ver, "1.2"))
                                        .build())
                                .video(anImp.impData.videoData == null ? null : Video.builder()
                                        .protocols(anImp.impData.videoData.getVideoProtocols(2))
                                        .w(anImp.impData.videoData.w)
                                        .h(anImp.impData.videoData.h)
                                        .mimes(anImp.impData.videoData.mimes)
                                        .minduration(1)
                                        .maxduration(60)
                                        .linearity(1)
                                        .placement(5)
                                        .build())
                                .bidfloor(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloor))
                                .bidfloorcur(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloorCur))
                                .build())
                        .collect(Collectors.toList()))
                .tmax(5000L)
                .regs(Regs.builder()
                        .gdpr(0)
                        .ext(ExtRegs.of(0, null))
                        .build())
                .cur(List.of(bidRequestData.currency))
                .site(Site.builder()
                        .publisher(Publisher.builder()
                                .id(publisherId)
                                .build())
                        .cat(bidRequestData.siteIABCategories)
                        .build())
                .user(User.builder()
                        .consent(bidRequestData.gdprConsent)
                        .ext(ExtUser.builder()
                                .consent(bidRequestData.gdprConsent)
                                .build())
                        .build())
                .source(Source.builder()
                        .tid("source_tid_" + uniqueId)
                        .ext(bidRequestData.schain == null ? null : ExtSource.of(bidRequestData.schain))
                        .build())
                .test(bidRequestData.test)
                .build();

        if (bidReqModifier != null) {
            bidRequest = bidReqModifier.apply(bidRequest);
        }

        return toJsonString(BID_REQUEST_MAPPER, bidRequest);
    }

    protected String getSSPBidRequest(String uniqueId, SSPBidRequestTestData bidRequestData) {
        return getSSPBidRequest(uniqueId, bidRequestData, null);
    }

    protected String getSSPBidRequest(
            String uniqueId,
            SSPBidRequestTestData bidRequestData,
            Function<BidRequest, BidRequest> bidReqModifier
    ) {
        final String publisherId = StringUtils.defaultIfBlank(bidRequestData.publisherId, defaultAccountId);

        BidRequest bidRequest = BidRequest.builder()
                .id("request_id_" + uniqueId)
                .imp(bidRequestData.imps.stream()
                        .map(anImp -> Imp.builder()
                                .id(anImp.impData.id)
                                .ext(anImp.impExt.get())
                                .banner(anImp.impData.bannerData == null ? null : Banner.builder()
                                        .w(anImp.impData.bannerData.w)
                                        .h(anImp.impData.bannerData.h)
                                        .mimes(anImp.impData.bannerData.mimes)
                                        .build())
                                .xNative(anImp.impData.nativeData == null ? null : Native.builder()
                                        .request(toJsonString(mapper, anImp.impData.nativeData.request))
                                        .ver(StringUtils.defaultString(anImp.impData.nativeData.ver, "1.2"))
                                        .build())
                                .video(anImp.impData.videoData == null ? null : Video.builder()
                                        .protocols(anImp.impData.videoData.getVideoProtocols(2))
                                        .w(anImp.impData.videoData.w)
                                        .h(anImp.impData.videoData.h)
                                        .mimes(anImp.impData.videoData.mimes)
                                        .minduration(1)
                                        .maxduration(60)
                                        .linearity(1)
                                        .placement(5)
                                        .build())
                                .bidfloor(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloor))
                                .bidfloorcur(ObjectUtil.getIfNotNull(bidRequestData.floor, Floor::getBidFloorCur))
                                .build())
                        .collect(Collectors.toList()))
                .site(Site.builder()
                        .domain(IT_TEST_DOMAIN)
                        .page("http://" + IT_TEST_DOMAIN)
                        .publisher(Publisher.builder()
                                .id(publisherId)
                                .domain(IT_TEST_MAIN_DOMAIN)
                                .build())
                        .cat(bidRequestData.siteIABCategories)
                        .ext(ExtSite.of(0, null))
                        .build())
                .device(Device.builder()
                        .ua(IT_TEST_USER_AGENT)
                        .ip(IT_TEST_IP)
                        .build())
                .user(User.builder()
                        //.consent(bidRequestData.gdprConsent) /* If SSP supports RTB 2.6, then it is available. */
                        .ext(ExtUser.builder()
                                .consent(bidRequestData.gdprConsent)
                                .build())
                        .build())
                .at(1)
                .tmax(5000L)
                .cur(List.of(bidRequestData.currency))
                .regs(Regs.builder()
                        //.gdpr(0) /* If SSP supports RTB 2.6, then it is available. */
                        .ext(ExtRegs.of(0, null))
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .pbs(ExtRequestPrebidPbs.of("/openrtb2/auction"))
                        .server(ExtRequestPrebidServer.of(
                                "http://localhost:8080", 1, "local"
                        ))
                        .channel(bidRequestData.channel)
                        .targeting(bidRequestData.extRequestTargeting)
                        .cache(bidRequestData.extRequestPrebidCache)
                        .floors(PriceFloorRules.builder()
                                .enabled(true)
                                .fetchStatus(publisherId != null ? FetchStatus.none : FetchStatus.error)
                                .location(PriceFloorLocation.noData)
                                .skipped(false)
                                .build())
                        .build()))
                .source(Source.builder()
                        .tid("source_tid_" + uniqueId)
                        .ext(bidRequestData.schain == null ? null : ExtSource.of(bidRequestData.schain))
                        .build())
                .test(bidRequestData.test)
                .build();

        if (bidReqModifier != null) {
            bidRequest = bidReqModifier.apply(bidRequest);
        }

        return toJsonString(BID_REQUEST_MAPPER, bidRequest);
    }

    protected String getSSPBidResponse(
            String bidderName,
            String uniqueId,
            String currency,
            BidResponseTestData... data
    ) {
        return getSSPBidResponse(
                bidderName, uniqueId, currency, Arrays.stream(data).collect(Collectors.toList())
        );
    }

    protected String getSSPBidResponse(
            String bidderName,
            String uniqueId,
            String currency,
            List<BidResponseTestData> data
    ) {
        final AtomicInteger bidIndex = new AtomicInteger(0);
        BidResponse bidResponse = BidResponse.builder()
                .id("request_id_" + uniqueId) /* request id is tied to the bid request. See above. */
                .cur(currency)
                .seatbid(List.of(SeatBid.builder()
                        .bid(data == null ? List.of() : data.stream()
                                .filter(Objects::nonNull)
                                .map(d -> toBid(bidIndex.getAndIncrement(), bidderName, d))
                                .collect(Collectors.toList()))
                        .build()
                ))
                .build();

        return toJsonString(BID_RESPONSE_MAPPER, bidResponse);
    }

    private Bid toBid(int bidIndex, String bidderName, BidResponseTestData d) {
        return Bid.builder()
                .id("b_" + bidIndex + "_" + bidderName + "_" + d.impId)
                .impid(d.impId)
                .price(BigDecimal.valueOf(d.price).setScale(2, RoundingMode.HALF_EVEN))
                .adm(d.adm)
                .cid("cid_" + bidIndex + "_" + d.impId)
                .adid("adid_" + bidIndex + "_" + d.impId)
                .crid("crid_" + bidIndex + "_" + d.impId)
                .ext(d.bidExt == null ? null : d.bidExt.get())
                .build();
    }

    protected double eurToUsd(double eurValue) {
        return BigDecimal.valueOf(eurValue / IT_TEST_USD_TO_EUR_RATE)
                .setScale(3 /* In PBS core, reverse conversion is at this scale. */, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    protected BigDecimal usdToEur(double usdValue) {
        return BigDecimal.valueOf(usdValue * IT_TEST_USD_TO_EUR_RATE)
                .setScale(3 /* In PBS core, normal conversion is at this scale. */, RoundingMode.HALF_EVEN);
    }

    protected double multiplyBid(double bid, double multiplier) {
        return BigDecimal.valueOf(bid).multiply(BigDecimal.valueOf(multiplier)).doubleValue();
    }

    protected String toMoneyFormat(double value) {
        return String.format("%.2f", value);
    }

    protected Request createNativeRequest(String nativeVersion, int titleLen, int wMin, int hMin, int dataLen) {
        return Request.builder()
                .context(1)
                .plcmttype(4)
                .plcmtcnt(1)
                .ver(nativeVersion)
                .assets(List.of(
                        com.iab.openrtb.request.Asset.builder()
                                .id(1)
                                .required(1)
                                .title(com.iab.openrtb.request.TitleObject.builder()
                                        .len(titleLen)
                                        .build())
                                .build(),
                        com.iab.openrtb.request.Asset.builder()
                                .id(2)
                                .required(1)
                                .img(com.iab.openrtb.request.ImageObject.builder()
                                        .type(3)
                                        .wmin(wMin)
                                        .hmin(hMin)
                                        .mimes(List.of("image/jpg", "image/jpeg", "image/png"))
                                        .build())
                                .build(),
                        com.iab.openrtb.request.Asset.builder()
                                .id(3)
                                .required(1)
                                .data(com.iab.openrtb.request.DataObject.builder()
                                        .type(2)
                                        .len(dataLen)
                                        .build())
                                .build()
                ))
                .build();
    }

    protected com.iab.openrtb.response.Response createNativeResponse(
            int w, int h, List<EventTracker> nativeEventTrackers, List<String> impTrackers
    ) {
        return com.iab.openrtb.response.Response.builder()
                .assets(List.of(
                        Asset.builder()
                                .id(1)
                                .title(TitleObject.builder()
                                        .text("Response title for native request")
                                        .build())
                                .build(),
                        Asset.builder()
                                .id(2)
                                .img(ImageObject.builder()
                                        .type(3)
                                        .url("http://cdn.pbs.com/creative-1.jpg")
                                        .w(w)
                                        .h(h)
                                        .build())
                                .build(),
                        Asset.builder()
                                .id(3)
                                .data(DataObject.builder()
                                        .type(2)
                                        .value("Response description for native request")
                                        .build())
                                .build()
                ))
                .link(Link.of("http://integrationtest.pbs.com/click?pp=${AUCTION_PRICE}", null, null, null))
                .eventtrackers(nativeEventTrackers)
                .imptrackers(impTrackers)
                .build();
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

    protected String getCustomTrackerUrl(String bidderName, String cpmInUsd, String pid) {
        return "https://it.pbs.com/ssp_bids?bidder=" + bidderName + "&cpm=" + cpmInUsd + "&pid=" + pid;
    }

    protected String getCustomTrackerPixel(String bidderName, String cpmInUsd, String pid) {
        return "<img src=\"" + getCustomTrackerUrl(bidderName, cpmInUsd, pid) + "\">";
    }

    @Builder(toBuilder = true)
    public static class AuctionBidRequestTestData {
        String currency;

        List<AuctionBidRequestImpTestData> imps;

        List<String> siteIABCategories;

        String gdprConsent;

        SupplyChain schain;

        Integer test;

        String publisherId;

        Floor floor;
    }

    @Builder(toBuilder = true)
    public static class AuctionBidRequestImpTestData {
        AuctionBidRequestImpExt impExt;

        SingleImpTestData impData;

        String publisherId;

        Floor floor;
    }

    @Builder(toBuilder = true)
    public static class SSPBidRequestTestData {
        String currency;

        List<SSPBidRequestImpTestData> imps;

        String publisherId;

        Floor floor;

        List<String> siteIABCategories;

        String gdprConsent;

        SupplyChain schain;

        ExtRequestPrebidChannel channel;

        ExtRequestTargeting extRequestTargeting;

        ExtRequestPrebidCache extRequestPrebidCache;

        Integer test;
    }

    @Builder(toBuilder = true)
    public static class SSPBidRequestImpTestData {
        SSPBidRequestImpExt impExt;

        SingleImpTestData impData;
    }

    @Builder(toBuilder = true)
    public static class SingleImpTestData {
        String id; /* Optional. If not set, test case will generate one. */

        BannerTestParam bannerData;

        NativeTestParam nativeData;

        VideoTestParam videoData;

        Floor floor;
    }

    @Builder(toBuilder = true)
    public static class BidResponseTestData {
        String impId;

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

        @JsonIgnore
        public static BannerTestParam getDefault() {
            return BannerTestParam.builder()
                    .w(300)
                    .h(250)
                    .mimes(List.of("image/jpg", "image/jpeg", "image/png"))
                    .build();
        }
    }

    @Builder(toBuilder = true)
    public static class VideoTestParam {
        int w;
        int h;
        List<String> mimes;
        List<Integer> protocols;

        @JsonIgnore
        public List<Integer> getVideoProtocols(int defaultProtocol) {
            if (CollectionUtils.isEmpty(protocols)) {
                return List.of(defaultProtocol);
            }

            return protocols;
        }

        @JsonIgnore
        public static VideoTestParam getDefault() {
            return VideoTestParam.builder()
                    .w(640)
                    .h(480)
                    .mimes(List.of("video/mp4"))
                    .build();
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
            this(true);
        }

        public SSPBidRequestImpExt(boolean initWithPrebid) {
            this.impExt = BID_REQUEST_MAPPER.valueToTree(
                    initWithPrebid ? ExtImp.of(ExtImpPrebid.builder().build(), null) : ExtImp.of(null, null)
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
            this.bidExt = BID_REQUEST_MAPPER.getNodeFactory().objectNode();
        }

        public ObjectNode get() {
            return bidExt;
        }

        public BidResponseBidExt putImprovedigitalBidExt(String buyingType, Integer lineItemId) {
            bidExt.putIfAbsent("improvedigital", BID_REQUEST_MAPPER.valueToTree(
                    ImprovedigitalBidExtImprovedigital.builder().build()
            ));
            if (buyingType != null) {
                ((ObjectNode) bidExt.at("/improvedigital")).put("buying_type", buyingType);
            }
            if (lineItemId != null) {
                ((ObjectNode) bidExt.at("/improvedigital")).put("line_item_id", lineItemId);
            }
            return this;
        }

        public BidResponseBidExt putSmarthubBidExt(String mediaType) {
            bidExt.put("mediaType", mediaType);
            return this;
        }

        public BidResponseBidExt putSalunamediaBidExt(String mediaType) {
            bidExt.put("mediaType", mediaType);
            return this;
        }

        public BidResponseBidExt putEvolutionBidExt(String mediaType) {
            bidExt.put("mediaType", mediaType);
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
        return createCacheRequest(requestId, Arrays.stream(cacheContents).collect(Collectors.toList()));
    }

    protected String createCacheRequest(
            String requestId,
            List<String> cacheContents
    ) throws IOException {
        List<String> cachePutObjects = cacheContents.stream().map(content -> "{"
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

    protected void assertBidCount(
            JSONObject responseJson, int expectedSeatbidCount, int... expectedBidCountsForSeats
    ) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isEqualTo(expectedSeatbidCount);
        assertThat(expectedSeatbidCount).isEqualTo(expectedBidCountsForSeats.length);

        for (int i = 0; i < expectedSeatbidCount; i++) {
            assertThat(responseJson.getJSONArray("seatbid").getJSONObject(i).getJSONArray("bid").length())
                    .isEqualTo(expectedBidCountsForSeats[i]);
        }
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
        assertThat(getBidPrice(responseJson, seatBidIndex, bidIndex)).isEqualTo(expectedBidPrice);
    }

    @NotNull
    protected Double getBidPrice(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getBid(responseJson, seatBidIndex, bidIndex)
                .getDouble("price");
    }

    @NotNull
    protected JSONObject getBid(JSONObject responseJson, int seatBidIndex, int bidIndex) throws JSONException {
        return getSeatBid(responseJson, seatBidIndex)
                .getJSONArray("bid").getJSONObject(bidIndex);
    }

    protected void assertSeat(JSONObject responseJson, int seatBidIndex, String expectedSeat) throws JSONException {
        assertThat(getSeat(responseJson, seatBidIndex)).isEqualTo(expectedSeat);
    }

    @NotNull
    protected String getSeat(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return getSeatBid(responseJson, seatBidIndex)
                .getString("seat");
    }

    @NotNull
    protected JSONObject getSeatBid(JSONObject responseJson, int seatBidIndex) throws JSONException {
        return responseJson
                .getJSONArray("seatbid").getJSONObject(seatBidIndex);
    }

    protected int getSeatBidIndex(JSONObject responseJson, String seatName) throws JSONException {
        final JSONArray seatBidArray = responseJson
                .getJSONArray("seatbid");
        for (int i = 0; i < seatBidArray.length(); i++) {
            JSONObject seatBid = seatBidArray.getJSONObject(i);
            if (seatBid.getString("seat").equals(seatName)) {
                return i;
            }
        }
        return -1;
    }

    protected void assertCurrency(JSONObject responseJson, String expectedCurrency) throws JSONException {
        assertThat(responseJson.getString("cur")).isEqualTo(expectedCurrency);
    }

    protected void assertNoExtErrors(JSONObject responseJson) throws JSONException {
        assertThat(responseJson.getJSONObject("ext").has("errors")).isFalse();
    }
}
