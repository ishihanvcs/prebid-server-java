package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.utils.PbsEndpointInvoker;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.when;

public class CreatorContextTest extends UnitTestBase {

    private final BigDecimal bidFloorInEuro = BigDecimal.valueOf(1.5);
    private final BigDecimal bidFloorInEuro2 = BigDecimal.valueOf(3.5);
    private final BigDecimal usdToEuroConversionRate = BigDecimal.valueOf(1.07);
    private final BigDecimal bidFloorInUsd = bidFloorInEuro
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);
    private final BigDecimal bidFloorInUsd2 = bidFloorInEuro2
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);

    private BidRequest defaultBidRequest;
    private BidResponse emptyBidResponse;
    private CreatorContext defaultContext;
    private BidResponse defaultBidResponse;
    private Imp defaultImp;
    private CustomVastUtils customVastUtils;

    @Mock
    PbsEndpointInvoker pbsEndpointInvoker;
    @Mock
    CurrencyConversionService currencyConversionService;
    @Mock
    GeoLocationService geoLocationService;
    @Mock
    Metrics metrics;
    @Mock
    CountryCodeMapper countryCodeMapper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        emptyBidResponse = BidResponse.builder().seatbid(new ArrayList<>()).build();
        defaultBidResponse = BidResponse.builder()
                .ext(ExtBidResponse
                        .builder()
                        .tmaxrequest(100L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null, null, null))
                        .build())
                .build();
        defaultImp = getStoredImp(defaultStoredImpId, i -> setImpConfigProperties(i, config -> {
            config.put("responseType", VastResponseType.gvast.name());
            config.putObject("waterfall").putArray("default");
        })).toBuilder().id("1").build();

        defaultBidRequest = BidRequest.builder().imp(new ArrayList<>(List.of(defaultImp))).build();

        customVastUtils = new CustomVastUtils(
                pbsEndpointInvoker, requestUtils, merger, currencyConversionService,
                macroProcessor, geoLocationService, metrics,
                countryCodeMapper, EXTERNAL_URL, GAM_NETWORK_CODE, PROTO_CACHE_HOST
        );

        defaultContext = creatorContext(defaultImp, null, List.of());
    }

    @Test
    public void testFromEmptyObjects() throws Exception {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> creatorContext(null, emptyBidResponse, null, List.of()));

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> creatorContext(defaultBidRequest, null, null, List.of()));

        assertThatNoException()
                .isThrownBy(() -> creatorContext(defaultBidRequest, emptyBidResponse, null, List.of()));

        // Test with empty BidRequest & BidResponse objects
        CreatorContext result = creatorContext(defaultBidRequest, emptyBidResponse, null, List.of());
        assertThat(result.getExtBidResponse()).isNull();
        assertThat(result.isDebug()).isFalse();
        assertThat(result.getGdpr()).isEmpty();
        assertThat(result.getGdprConsent()).isEmpty();
        assertThat(result.getAlpha3Country()).isBlank();
        assertThat(result.getIfa()).isNull();
        assertThat(result.getLmt()).isNull();
        assertThat(result.getOs()).isNull();
        assertThat(result.getCat()).isNull();
        assertThat(result.getBundle()).isNull();
        assertThat(result.getReferrer()).isNull();
        assertThat(result.getEncodedReferrer()).isNull();
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(result::getDomain);
        assertThat(result.isApp()).isFalse();
        assertThat(result.getResponseType()).isEqualTo(VastResponseType.gvast);
        assertThat(result.isGVast()).isTrue();
        assertThat(result.isPrioritizeImproveDeals()).isTrue();
        assertThat(result.getGamIdType()).isNull();
        assertThat(result.getWaterfall().get(0)).isEqualTo("gam");
        assertThat(result.getWaterfall(false)).isNotEmpty();
        assertThat(result.getWaterfall(false).get(0)).isEqualTo("gam");
        assertThat(result.getWaterfall(true)).isNotEmpty();
        assertThat(result.getWaterfall(true).size()).isEqualTo(2);
        assertThat(result.getWaterfall(true).get(0)).isEqualTo("gam_improve_deal");
        assertThat(result.getWaterfall(true).get(1)).isEqualTo("gam_no_hb");
    }

    private CreatorContext creatorContext(
            Imp imp, String alpha3Country, List<Bid> bids
    ) {
        final BidRequest bidRequest = defaultBidRequest.toBuilder()
                .imp(new ArrayList<>(List.of(imp)))
                .build();

        return creatorContext(bidRequest, emptyBidResponse, alpha3Country, bids);
    }

    private CreatorContext creatorContext(
            BidRequest bidRequest,
            BidResponse bidResponse,
            String alpha3Country,
            List<Bid> bids
    ) {
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);

        when(currencyConversionService.convertCurrency(
                bidFloorInEuro2, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd2);

        HooksModuleContext hooksModuleContext = customVastUtils.createModuleContext(bidRequest, alpha3Country)
                .with(bidResponse);

        CreatorContext creatorContext = CreatorContext.from(hooksModuleContext, jsonUtils);
        final Imp updatedImp = hooksModuleContext.getBidRequest().getImp().get(0);
        return creatorContext.with(updatedImp, bids, jsonUtils);
    }

    @Test
    public void testWaterfallAndFloorForAlpha3Country() {
        final String alpha3Country = "NLD";
        final String defaultKey = ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY;

        final Floor defaultFloor = Floor.of(bidFloorInUsd, "USD");
        final Floor countryFloor = Floor.of(bidFloorInEuro2, "EUR");
        final Map<String, Floor> floorsConfig = Map.of(
                defaultKey, defaultFloor,
                alpha3Country, countryFloor
        );
        final JsonNode floorsNode = mapper.valueToTree(floorsConfig);

        final List<String> defaultWaterfall = List.of("gam_first_look", "gam");
        final List<String> countryWaterfall = List.of("gam", "blah.com");
        final Map<String, List<String>> waterfallConfig = Map.of(
                defaultKey, defaultWaterfall,
                alpha3Country, countryWaterfall
        );

        final JsonNode waterfallsNode = mapper.valueToTree(waterfallConfig);

        final Imp impWithConfig = setImpConfigProperties(defaultImp, configNode -> {
            configNode.set("floors", floorsNode);
            configNode.set("waterfall", waterfallsNode);
        });

        CreatorContext context = creatorContext(impWithConfig, null, List.of());

        assertThat(context.isGVast()).isTrue();
        assertThat(context.getWaterfall())
                .isEqualTo(defaultWaterfall);
        assertThat(context.getBidfloor())
                .isEqualTo(bidFloorInUsd.doubleValue());

        context = creatorContext(impWithConfig, alpha3Country, List.of());
        assertThat(context.isGVast()).isTrue();
        assertThat(context.getWaterfall())
                .isEqualTo(countryWaterfall);
        assertThat(context.getBidfloor())
                .isEqualTo(bidFloorInUsd2.doubleValue());
    }

    @Test
    public void testFromValidObjects() throws Exception {
        BidRequest bidRequest = defaultBidRequest
                .toBuilder()
                .test(1)
                .build();
        CreatorContext result = creatorContext(bidRequest, defaultBidResponse, null, List.of());
        assertThat(result.getExtBidResponse()).isNotNull();
        assertThat(result.isDebug()).isTrue();
    }

    @Test
    public void testWithImpAndBids() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> defaultContext.with(null, List.of(), jsonUtils));
    }

    @Test
    public void testGetDomain() throws Exception {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(defaultContext::getDomain);
        CreatorContext contextWithValidReferrer = defaultContext
                .toBuilder()
                .referrer("https://www.subdomain.example.com/some/random/page")
                .build();
        String result = contextWithValidReferrer.getDomain();
        assertThat(result).isEqualTo("subdomain.example.com");

        contextWithValidReferrer = defaultContext
                .toBuilder()
                .referrer("https://www2.subdomain.example.com/some/random/page")
                .build();
        result = contextWithValidReferrer.getDomain();
        assertThat(result).isEqualTo("www2.subdomain.example.com");

        contextWithValidReferrer = defaultContext
                .toBuilder()
                .referrer("www.subdomain.example.com/some/random/page")
                .build();
        result = contextWithValidReferrer.getDomain();
        assertThat(result).isEqualTo("subdomain.example.com");

        contextWithValidReferrer = defaultContext
                .toBuilder()
                .referrer("www2.subdomain.example.com/some/random/page")
                .build();
        result = contextWithValidReferrer.getDomain();
        assertThat(result).isEqualTo("www2.subdomain.example.com");
    }

    @Test
    public void testGetWaterfall() {
        assertThat(defaultContext.getResponseType()).isEqualTo(VastResponseType.gvast);
        assertThat(defaultContext.isGVast()).isTrue();
        List<String> result = defaultContext.getWaterfall();
        assertThat(result).isEqualTo(List.of("gam"));
        result = defaultContext.getWaterfall(true);
        assertThat(result).isEqualTo(List.of("gam_improve_deal", "gam_no_hb"));

        CreatorContext context = defaultContext
                .toBuilder()
                .waterfall(List.of("a", "b", "c"))
                .build();

        assertThat(context.getResponseType()).isEqualTo(VastResponseType.gvast);
        assertThat(context.isGVast()).isTrue();
        result = context.getWaterfall();
        assertThat(result).isEqualTo(List.of("a", "b", "c"));

        result = context.getWaterfall(true);
        assertThat(result).isEqualTo(List.of("gam_improve_deal", "a", "b", "c"));

        context = defaultContext
                .toBuilder()
                .responseType(VastResponseType.waterfall)
                .build();

        assertThat(context.isGVast()).isFalse();
        result = context.getWaterfall();
        assertThat(result).isEmpty();

        result = context.getWaterfall(true);
        assertThat(result).isEmpty();
    }

    @Test
    public void testGetGamIdType() {
        String result = defaultContext.getGamIdType();
        assertThat(result).isNull();

        CreatorContext context = defaultContext
                .toBuilder()
                .os("ios") // lower case
                .build();
        result = context.getGamIdType();
        assertThat(result).isEqualTo("idfa");

        context = defaultContext
                .toBuilder()
                .os("IoS") // lower case
                .build();
        result = context.getGamIdType();
        assertThat(result).isEqualTo("idfa");

        context = defaultContext
                .toBuilder()
                .os("android") // lower case
                .build();
        result = context.getGamIdType();
        assertThat(result).isEqualTo("adid");

        context = defaultContext
                .toBuilder()
                .os("AnDrOiD") // lower case
                .build();
        result = context.getGamIdType();
        assertThat(result).isEqualTo("adid");

        context = defaultContext
                .toBuilder()
                .os("unknown") // lower case
                .build();
        result = context.getGamIdType();
        assertThat(result).isNull();
    }

    @Test
    public void testIsApp() {
        boolean result = defaultContext.isApp();
        assertThat(result).isFalse();
        result = defaultContext.toBuilder()
                .bundle("my-awesome-game")
                .build().isApp();
        assertThat(result).isTrue();
    }
}

