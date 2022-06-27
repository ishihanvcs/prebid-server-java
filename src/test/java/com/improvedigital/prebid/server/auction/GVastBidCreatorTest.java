package com.improvedigital.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.auction.model.CustParams;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import com.improvedigital.prebid.server.utils.GVastHookUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class GVastBidCreatorTest extends UnitTestBase {

    private static final String EXTERNAL_URL = "https://pbs-proto.360polaris.biz";
    private static final String GAM_NETWORK_CODE = "1015413";
    private static final String PROTO_CACHE_HOST = "euw-pbc-proto.360polaris.biz";
    private static final String PROTO_CACHE_URL = String.format("https://%s/cache", PROTO_CACHE_HOST);
    private static final String PRODUCTION_CACHE_HOST = "euw-pbc.360yield.com";
    private static final String PRODUCTION_CACHE_URL = String.format("https://%s/cache", PRODUCTION_CACHE_HOST);
    private static final Integer IMPROVE_PLACEMENT_ID = 12345;
    private static final double IMPROVE_DIGITAL_DEAL_FLOOR = 1.5;
    private static final String CUST_PARAM_STR = "cust-param1=value1&cust-param2=value2";
    private static final String VIDEO_IMP_ID = "video";

    static MacroProcessor macroProcessor = new MacroProcessor();
    private BidRequest bidRequestVast;
    private BidRequest bidRequestGVast;
    private BidRequest bidRequestWaterfall;

    @Mock
    CurrencyConversionService currencyConversionService;
    private GVastHookUtils gVastHookUtils;
    private BidResponse bidResponseDefault;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        this.gVastHookUtils = new GVastHookUtils(requestUtils, merger, currencyConversionService);
        this.resourceDir = "com/improvedigital/prebid/server/hooks/v1/gvast";
        this.bidRequestVast = getBidRequest(VastResponseType.vast);
        this.bidRequestGVast = getBidRequest(VastResponseType.gvast);
        this.bidRequestWaterfall = getBidRequest(VastResponseType.waterfall);
        this.bidResponseDefault = getBidResponse();
    }

    @Test
    public void testConstructorParams() {
        final BidRequest bidRequest = bidRequestVast;
        BidResponse bidResponse = getBidResponse();
        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                null
        ));

        assertThatNoException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                PROTO_CACHE_HOST
        ));
    }

    @Test
    public void testGVastWithNoBid() throws Exception {
        BidResponse bidResponse = bidResponseDefault.toBuilder().build();
        bidResponse.getSeatbid().clear();
        createGVastBidAndValidateAds(
                bidRequestGVast, bidResponse, 3, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithImproveDeal() {
        BidResponse bidResponse = bidResponseDefault;
        createGVastBidAndValidateAds(
                bidRequestGVast, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithoutImproveDeal() throws Exception {
        BidResponse bidResponse = bidResponseDefault.toBuilder().build();
        removeImproveDealsFromTargeting(bidResponse);
        // TODO: test case specific validator
        createGVastBidAndValidateAds(
                bidRequestGVast, bidResponse, 3, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithoutDebugMode() {
        BidRequest bidRequest = bidRequestGVast.toBuilder().test(0).build();
        BidResponse bidResponse = bidResponseDefault.toBuilder()
                .ext(bidResponseDefault.getExt().toBuilder()
                        .debug(null)
                        .build())
                .build();
        createGVastBidAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithCustParams() {
        BidRequest bidRequest = getBidRequestWithCustParams(bidRequestGVast);
        BidResponse bidResponse = bidResponseDefault;
        createGVastBidAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithNoBid() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        bidResponse.getSeatbid().clear();
        createGVastBidAndValidateAds(
                bidRequestWaterfall, bidResponse, 3, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithImproveDeal() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        createGVastBidAndValidateAds(
                bidRequestWaterfall, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithoutImproveDeal() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        removeImproveDealsFromTargeting(bidResponse);
        createGVastBidAndValidateAds(
                bidRequestWaterfall, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithoutDebugMode() throws Exception {
        BidRequest bidRequest = bidRequestWaterfall.toBuilder().test(0).build();
        BidResponse bidResponse = bidResponseDefault.toBuilder()
                .ext(bidResponseDefault.getExt().toBuilder()
                        .debug(null)
                        .build())
                .build();
        createGVastBidAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithCustParams() throws Exception {
        BidRequest bidRequest = getBidRequestWithCustParams(bidRequestWaterfall);
        BidResponse bidResponse = bidResponseDefault;
        createGVastBidAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    private void removeImproveDealsFromTargeting(BidResponse bidResponse) {
        bidResponse.getSeatbid().forEach(seatBid -> {
            if (RequestUtils.IMPROVE_BIDDER_NAME.equals(seatBid.getSeat())) {
                seatBid.getBid().forEach(bid -> {
                    if (bid.getExt() == null) {
                        return;
                    }
                    JsonNode targeting = bid.getExt().at("/prebid/targeting");
                    if (!targeting.isObject()) {
                        return;
                    }
                    ObjectNode targetingKvs = (ObjectNode) targeting;
                    if (targetingKvs.has("hb_deal_improvedigit")) {
                        targetingKvs.remove("hb_deal_improvedigit");
                    }
                });
            }
        });
    }

    private SeatBid createImproveSeatBid(BidResponse bidResponse, Imp imp) {
        SeatBid seatBid = gVastHookUtils.findOrCreateSeatBid(
                RequestUtils.IMPROVE_BIDDER_NAME,
                bidResponse, imp
        );
        return seatBid.toBuilder()
                .bid(gVastHookUtils.getBidsForImpId(seatBid, imp))
                .build();
    }

    /**
     * Creates a GVastBidCreator, invokes it's create method and validates each of the
     * Ad entries with custom validator function {@link Consumer<ObjectNode>}.
     *
     * @param request {@link BidRequest}
     * @param response {@link BidResponse}
     * @param validator a {@link Consumer} that accepts id {@link String} and Wrapper {@link ObjectNode} of an Ad
     *                  as parameters and validates with custom validation logic
     */
    private void createGVastBidAndValidateAds(
            BidRequest request, BidResponse response,
            int expectedAdCount,
            BiConsumer<String, ObjectNode> validator
    ) {
        assertThat(request).isNotNull();
        assertThat(response).isNotNull();
        GVastBidCreator gVastBidCreator = createGVastBidCreator(request, response);
        Imp imp = request.getImp().stream()
                .filter(imp1 -> VIDEO_IMP_ID.equals(imp1.getId()))
                .findFirst().orElse(null);
        assertThat(imp).isNotNull();
        SeatBid improveSeatBid = createImproveSeatBid(response, imp);
        Bid result = gVastBidCreator.create(imp, improveSeatBid, true);
        assertThat(result).isNotNull();
        ObjectNode parsedAdm = parseXml(result.getAdm(), ObjectNode.class);
        assertThat(parsedAdm).isNotNull();
        assertThat(parsedAdm.get("version").asText()).isEqualTo("2.0");
        assertThat(parsedAdm.get("Ad").isArray()).isTrue();
        assertThat(parsedAdm.get("Ad").size()).isEqualTo(expectedAdCount);
        for (int i = 0; i < expectedAdCount; i++) {
            assertThat(parsedAdm.get("Ad").get(i).isObject()).isTrue();
            ObjectNode adNode = (ObjectNode) parsedAdm.get("Ad").get(i);

            String id = adNode.get("id").asText();
            assertThat(id).isEqualTo(String.valueOf(i));

            assertThat(adNode.get("Wrapper").isObject()).isTrue();
            ObjectNode wrapperNode = (ObjectNode) adNode.get("Wrapper");
            if (i + 1 < expectedAdCount) {
                assertThat(wrapperNode.get("fallbackOnNoAd").asText()).isEqualTo("true");
            }
            assertThat(wrapperNode.get("AdSystem").asText()).isEqualTo("ImproveDigital PBS");
            if (wrapperNode.has("Impression")) {
                assertThat(wrapperNode.get("Impression").isArray()).isTrue();
                assertThat(wrapperNode.get("Impression").size()).isEqualTo(4);
            }

            assertThat(wrapperNode.at("/Extensions/Extension").isMissingNode()).isFalse();

            if (validator != null) {
                validator.accept(String.valueOf(id), wrapperNode);
            }
        }
    }

    private GVastBidCreator createGVastBidCreator(BidRequest bidRequest, BidResponse bidResponse) {
        return createGVastBidCreator(bidRequest, bidResponse, PROTO_CACHE_HOST);
    }

    private GVastBidCreator createGVastBidCreator(BidRequest bidRequest, BidResponse bidResponse, String cacheHost) {
        return new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                cacheHost
        );
    }

    private BidResponse getBidResponse() {
        return getBidResponse(null);
    }

    private BidResponse getBidResponse(Function<BidResponse, BidResponse> modifier) {
        return getStoredResponse(defaultResponseId, bidResponse -> {
            if (modifier != null) {
                bidResponse = modifier.apply(bidResponse);
            }
            return bidResponse;
        });
    }

    private BidRequest getBidRequestWithCustParams(BidRequest fromRequest) {
        CustParams custParams = new CustParams(CUST_PARAM_STR);
        BidRequest bidRequest = fromRequest.toBuilder().build();
        bidRequest.getImp().replaceAll(imp ->
                setImpBidderProperties(imp, RequestUtils.IMPROVE_BIDDER_NAME,
                        bidderNode ->
                                bidderNode.set("keyValues", mapper.valueToTree(custParams))
                )
        );
        return bidRequest;
    }

    private BidRequest getBidRequest(VastResponseType responseType) {
        return getStoredRequest(defaultRequestId, bidRequest -> {
            Imp imp = getStoredImp(defaultStoredImpId, imp1 ->
                    setImpConfigProperties(
                            imp1, configNode -> configNode.put("responseType", responseType.name())
                    )
            ).toBuilder().id(VIDEO_IMP_ID).build();
            bidRequest = bidRequest.toBuilder().imp(
                    new ArrayList<>(List.of(imp))
            ).build();
            return bidRequest;
        });
    }
}
