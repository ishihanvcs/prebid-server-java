package com.improvedigital.prebid.server.customvast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.model.CreatorContext;
import com.improvedigital.prebid.server.customvast.model.CustParams;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import com.improvedigital.prebid.server.utils.RequestUtils;
import com.improvedigital.prebid.server.utils.ResponseUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class CustomVastCreatorTest extends UnitTestBase {

    private static final Integer IMPROVE_PLACEMENT_ID = 12345;
    private static final double IMPROVE_DIGITAL_DEAL_FLOOR = 1.5;
    private static final String CUST_PARAM_STR = "cust-param1=value1&cust-param2=value2";
    private static final String VIDEO_IMP_ID = "video";

    private BidRequest bidRequestVast;
    private BidRequest bidRequestGVast;
    private BidRequest bidRequestWaterfall;

    @Mock
    CurrencyConversionService currencyConversionService;
    @Mock
    GeoLocationService geoLocationService;
    @Mock
    Metrics metrics;
    @Mock
    CountryCodeMapper countryCodeMapper;

    private CustomVastUtils customVastUtils;
    private BidResponse bidResponseDefault;
    private CustomVastCreator customVastCreator;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        this.customVastUtils = new CustomVastUtils(
                requestUtils, merger, currencyConversionService, macroProcessor,
                geoLocationService, metrics, countryCodeMapper,
                EXTERNAL_URL, GAM_NETWORK_CODE, PROTO_CACHE_HOST
        );
        this.customVastCreator = new CustomVastCreator(customVastUtils);
        this.bidRequestVast = getBidRequest(VastResponseType.vast);
        this.bidRequestGVast = getBidRequest(VastResponseType.gvast);
        this.bidRequestWaterfall = getBidRequest(VastResponseType.waterfall);
        this.bidResponseDefault = getBidResponse();
    }

    @Test
    public void testConstructorParams() {
        assertThatNullPointerException().isThrownBy(() -> new CustomVastCreator(
                        null
                ));

        assertThatNoException().isThrownBy(() -> new CustomVastCreator(
                customVastUtils
        ));
    }

    @Test
    public void testGVastWithNoBid() throws Exception {
        final Imp imp = getVideoImpFromRequest(bidRequestGVast);
        CreatorContext context = CreatorContext.builder().build()
                .with(imp, List.of(), jsonUtils);
        createCustomVastAndValidateAds(
                context, 3, (id, wrapper) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithImproveDeal() {
        BidResponse bidResponse = bidResponseDefault;
        createCustomVastAndValidateAds(
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
        createCustomVastAndValidateAds(
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
        createCustomVastAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testGVastWithCustParams() {
        BidRequest bidRequest = getBidRequestWithCustParams(bidRequestGVast);
        BidResponse bidResponse = bidResponseDefault;
        createCustomVastAndValidateAds(
                bidRequest, bidResponse, 4, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithNoBid() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        bidResponse.getSeatbid().clear();
        createCustomVastAndValidateAds(
                bidRequestWaterfall, bidResponse, 3, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithImproveDeal() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        createCustomVastAndValidateAds(
                bidRequestWaterfall, bidResponse, 6, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithoutImproveDeal() throws Exception {
        BidResponse bidResponse = bidResponseDefault;
        removeImproveDealsFromTargeting(bidResponse);
        createCustomVastAndValidateAds(
                bidRequestWaterfall, bidResponse, 6, (id, wrapperNode) -> {
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
        createCustomVastAndValidateAds(
                bidRequest, bidResponse, 6, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testWaterfallWithCustParams() throws Exception {
        BidRequest bidRequest = getBidRequestWithCustParams(bidRequestWaterfall);
        BidResponse bidResponse = bidResponseDefault;
        createCustomVastAndValidateAds(
                bidRequest, bidResponse, 6, (id, wrapperNode) -> {
                    // TODO: test case specific validator will go here
                }
        );
    }

    @Test
    public void testCustomVastBuilder() throws IOException {
        CustomVast.DebugExtension debugExtension = CustomVast.DebugExtension.of(
                getStoredResponse(defaultResponseId).getExt()
        );

        CustomVast.Wrapper wrapper1 = CustomVast.Wrapper.builder()
                .fallbackOnNoAd(true)
                .vastAdTagURI("http://ad.google.com/ssp-1")
                .extension(debugExtension)
                .build();

        CustomVast.Wrapper wrapper2 = CustomVast.Wrapper.builder()
                .fallbackOnNoAd(true)
                .vastAdTagURI("http://ad.google.com/ssp-2")
                .extension(
                        CustomVast.WaterfallExtension.of(1)
                )
                .impressions(
                        List.of("imp-1")
                )
                .build();

        CustomVast.Wrapper wrapper3 = CustomVast.Wrapper.builder()
                .fallbackOnNoAd(true)
                .vastAdTagURI("http://ad.google.com/ssp-2")
                .extension(
                        CustomVast.WaterfallExtension.of(2)
                )
                .impressions(
                        List.of("imp-1", "imp2")
                )
                .build();

        CustomVast vast = CustomVast.builder()
                .ad(CustomVast.Ad.of(0, wrapper1))
                .ad(CustomVast.Ad.of(1, wrapper2))
                .ad(CustomVast.Ad.of(2, wrapper3))
                .build();

        String vastXml = CustomVastUtils.customVastToXml(vast);

        CustomVast parsed = CustomVastUtils.customVastFromXml(vastXml);
        assertThat(parsed).isNotNull();
        assertThat(parsed)
                .usingRecursiveComparison()
                .isEqualTo(vast);
    }

    private void removeImproveDealsFromTargeting(BidResponse bidResponse) {
        bidResponse.getSeatbid().forEach(seatBid -> {
            if (RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME.equals(seatBid.getSeat())) {
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

    /**
     * Creates a {@link CustomVastCreator}, invokes it's create method and validates each of the
     * Ad entries with custom validator function {@link Consumer<CustomVast.Wrapper>}.
     *
     * @param request {@link BidRequest}
     * @param response {@link BidResponse}
     * @param expectedAdCount int
     * @param validator {@link Consumer} that accepts id {@link String} and
     *                  Wrapper {@link CustomVast.Wrapper}
     *                  of an Ad {@link CustomVast.Ad}
     *                  as parameters and validates with custom validation logic
     */

    private void createCustomVastAndValidateAds(
            BidRequest request, BidResponse response,
            int expectedAdCount,
            BiConsumer<String, CustomVast.Wrapper> validator
    ) {
        assertThat(request).isNotNull();
        assertThat(response).isNotNull();
        Imp imp = getVideoImpFromRequest(request);
        assertThat(imp).isNotNull();
        CreatorContext context = CreatorContext.from(
                request, response, jsonUtils
        ).with(imp, ResponseUtils.getBidsForImp(response, imp), jsonUtils);

        CustomVast customVast = customVastCreator.create(context);
        assertThat(customVast).isNotNull();
        assertThat(customVast.getVersion()).isEqualTo("2.0");
        assertThat(customVast.getAds().size()).isEqualTo(expectedAdCount);
        for (int i = 0; i < expectedAdCount; i++) {
            CustomVast.Ad ad = customVast.getAds().get(i);
            assertThat(ad).isNotNull();
            Integer id = ad.getId();
            assertThat(id).isEqualTo(i);
            CustomVast.Wrapper wrapper = ad.getWrapper();
            assertThat(wrapper).isNotNull();
            if (i + 1 < expectedAdCount) {
                assertThat(wrapper.getFallbackOnNoAd()).isEqualTo(true);
            }
            assertThat(wrapper.getAdSystem()).isEqualTo("ImproveDigital PBS");

            if (context.isGVast() && !wrapper.getVastAdTagURI().startsWith(CustomVastUtils.GAM_VAST_URL_BASE)) {
                assertThat(wrapper.getImpressions()).isEmpty();
            } else {
                assertThat(wrapper.getImpressions()).isNotEmpty();
                assertThat(wrapper.getImpressions().size()).isEqualTo(4);
            }

            assertThat(wrapper.getExtensions().isEmpty()).isFalse();

            if (validator != null) {
                validator.accept(String.valueOf(id), wrapper);
            }
        }
    }

    /**
     * Creates a {@link CustomVastCreator}, invokes it's create method and validates each of the
     * Ad entries with custom validator function {@link Consumer<CustomVast.Wrapper>}.
     *
     * @param context {@link CreatorContext}
     * @param expectedAdCount int
     * @param validator a {@link Consumer} that accepts id {@link String} and
     *                  Wrapper {@link CustomVast.Wrapper}
     *                  of an Ad {@link CustomVast.Ad}
     *                  as parameters and validates with custom validation logic
     */

    private void createCustomVastAndValidateAds(
            CreatorContext context,
            int expectedAdCount,
            BiConsumer<String, CustomVast.Wrapper> validator
    ) {
        CustomVast customVast = customVastCreator.create(context);
        assertThat(customVast).isNotNull();
        assertThat(customVast.getVersion()).isEqualTo("2.0");
        assertThat(customVast.getAds().size()).isEqualTo(expectedAdCount);
        for (int i = 0; i < expectedAdCount; i++) {
            CustomVast.Ad ad = customVast.getAds().get(i);
            assertThat(ad).isNotNull();
            Integer id = ad.getId();
            assertThat(id).isEqualTo(i);
            CustomVast.Wrapper wrapper = ad.getWrapper();
            assertThat(wrapper).isNotNull();
            if (i + 1 < expectedAdCount) {
                assertThat(wrapper.getFallbackOnNoAd()).isEqualTo(true);
            }
            assertThat(wrapper.getAdSystem()).isEqualTo("ImproveDigital PBS");

            if (context.isGVast() && !wrapper.getVastAdTagURI().startsWith(CustomVastUtils.GAM_VAST_URL_BASE)) {
                assertThat(wrapper.getImpressions()).isEmpty();
            } else {
                assertThat(wrapper.getImpressions()).isNotEmpty();
                assertThat(wrapper.getImpressions().size()).isEqualTo(4);
            }

            assertThat(wrapper.getExtensions().isEmpty()).isFalse();

            if (validator != null) {
                validator.accept(String.valueOf(id), wrapper);
            }
        }
    }

    private Imp getVideoImpFromRequest(BidRequest bidRequestGVast) {
        return bidRequestGVast.getImp().stream()
                .filter(imp1 -> VIDEO_IMP_ID.equals(imp1.getId()))
                .findFirst().orElse(null);
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
                setImpBidderProperties(imp, RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME,
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
