package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.UnitTestBase;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class CreatorContextTest extends UnitTestBase {

    private BidRequest emptyBidRequest;
    private BidResponse emptyBidResponse;
    private CreatorContext defaultContext;
    private BidResponse defaultBidResponse;
    private Imp defaultImp;

    @Before
    public void setUp() {
        emptyBidRequest = BidRequest.builder().build();
        emptyBidResponse = BidResponse.builder().build();
        defaultBidResponse = BidResponse.builder()
                .ext(ExtBidResponse
                        .builder()
                        .tmaxrequest(100L)
                        .prebid(ExtBidResponsePrebid.of(1000L, null))
                        .build())
                .build();
        defaultImp = getStoredImp(defaultStoredImpId, i -> setImpConfigProperties(i, config -> {
            config.put("responseType", VastResponseType.gvast.name());
            config.putObject("waterfall").putArray("default");
        })).toBuilder().id("1").build();
        defaultContext = CreatorContext.from(emptyBidRequest, emptyBidResponse, jsonUtils)
                .with(defaultImp, List.of(), jsonUtils);
    }

    @Test
    public void testFromEmptyObjects() throws Exception {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CreatorContext.from(null, emptyBidResponse, jsonUtils));

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CreatorContext.from(emptyBidRequest, null, jsonUtils));

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CreatorContext.from(emptyBidRequest, emptyBidResponse, null));

        assertThatNoException()
                .isThrownBy(() -> CreatorContext.from(emptyBidRequest, emptyBidResponse, jsonUtils));

        // Test with empty BidRequest & BidResponse objects
        CreatorContext result = CreatorContext.from(emptyBidRequest, emptyBidResponse, jsonUtils);
        assertThat(result.getExtBidResponse()).isNull();
        assertThat(result.isDebug()).isFalse();
        assertThat(result.getGdpr()).isNull();
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

    @Test
    public void testWaterfallAndFloorForAlpha3Country() {
        final String alpha3Country = "NLD";
        final String defaultKey = ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY;

        final Floor defaultFloor = Floor.of(BigDecimal.valueOf(1), "USD");
        final Floor countryFloor = Floor.of(BigDecimal.valueOf(2), "EUR");
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

        final BidRequest bidRequest = emptyBidRequest.toBuilder()
                .imp(List.of(impWithConfig))
                .build();
        final BidResponse bidResponse = defaultBidResponse;

        CreatorContext context = CreatorContext.from(bidRequest, bidResponse, jsonUtils)
                .with(impWithConfig, List.of(), jsonUtils);

        assertThat(context.isGVast()).isTrue();
        assertThat(context.getWaterfall())
                .isEqualTo(defaultWaterfall);
        assertThat(context.getBidfloor())
                .isEqualTo(defaultFloor.getBidFloor().doubleValue());

        context = context.with(alpha3Country).with(impWithConfig, List.of(), jsonUtils);
        assertThat(context.isGVast()).isTrue();
        assertThat(context.getWaterfall())
                .isEqualTo(countryWaterfall);
        assertThat(context.getBidfloor())
                .isEqualTo(countryFloor.getBidFloor().doubleValue());
    }

    @Test
    public void testFromValidObjects() throws Exception {
        BidRequest bidRequest = emptyBidRequest
                .toBuilder()
                .test(1)
                .build();
        CreatorContext result = CreatorContext.from(
                bidRequest,
                defaultBidResponse,
                jsonUtils
        );
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

