package com.improvedigital.prebid.server.customvast;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import com.improvedigital.prebid.server.customvast.model.Floor;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import io.vertx.core.Future;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class CustomVastUtilsTest extends UnitTestBase {

    private final String impId = "video";
    private final String validIp = "163.175.196.37";
    private final GeoInfo validIpToGeoInfo = GeoInfo.builder()
            .vendor("maxmind")
            .country("nl")
            .build();
    private final String invalidIp = "invalid_ip";
    private final GeoInfo invalidIpToGeoInfo = GeoInfo.builder()
            .vendor("maxmind")
            .country(null)
            .build();
    private final String countryAlpha2 = "nl";
    private final String countryAlpha3 = "NLD";
    private final String invalidCountry = "invalid_country";

    private final BigDecimal bidFloorInEuro = BigDecimal.valueOf(1.5);
    private final BigDecimal bidFloorInEuro2 = BigDecimal.valueOf(3.5);
    private final BigDecimal usdToEuroConversionRate = BigDecimal.valueOf(1.07);
    private final BigDecimal bidFloorInUsd = convertEuroToUsd(bidFloorInEuro);
    private final BigDecimal bidFloorInUsd2 = convertEuroToUsd(bidFloorInEuro2);

    private final Map<String, Floor> floorsEmpty = Map.of();

    private final Map<String, Floor> floorsWithDefaultAndUSD = Map.of(
            ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, Floor.of(
                    bidFloorInUsd, ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
            )
    );

    private final Map<String, Floor> floorsWithCountryAndUSD = Map.of(
            ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, Floor.of(
                    bidFloorInUsd, ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
            ),
            countryAlpha3, Floor.of(
                    bidFloorInUsd2, ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
            )
    );

    private final Map<String, Floor> floorsWithDefaultAndEUR = Map.of(
            ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, Floor.of(
                    bidFloorInEuro, "EUR"
            )
    );

    private final Map<String, Floor> floorsWithCountryAndEur = Map.of(
            ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, Floor.of(
                    bidFloorInEuro, "EUR"
            ),
            countryAlpha3, Floor.of(
                    bidFloorInEuro2, "EUR"
            )
    );

    @Mock
    CurrencyConversionService currencyConversionService;
    @Mock
    GeoLocationService geoLocationService;
    @Mock
    Metrics metrics;
    @Mock
    CountryCodeMapper countryCodeMapper;

    private CustomVastUtils customVastUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doNothing().when(metrics).updateGeoLocationMetric(true);
        customVastUtils = new CustomVastUtils(
                requestUtils, merger, currencyConversionService,
                macroProcessor, geoLocationService, metrics,
                countryCodeMapper, EXTERNAL_URL, GAM_NETWORK_CODE, PROTO_CACHE_HOST
        );
    }

    // @Test
    public void testCreateBidFromCustomVast() throws Exception {
        Bid result = customVastUtils.createBidFromCustomVast(null, null);
        Assert.assertEquals(null, result);
    }

    @Test
    public void testResolveCountryAndCreateModuleContextWithValidCountry() throws Exception {
        final Device deviceWithValidCountry = Device.builder()
                .geo(Geo.builder()
                        .country(countryAlpha3)
                        .build())
                .build();
        BidRequest bidRequest = getBidRequestWithDeviceAndFloorsConfig(null, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsEmpty, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndUSD, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndUSD, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndUSD.get(countryAlpha3)
            );
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndEUR, deviceWithValidCountry);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndEUR.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndEur, deviceWithValidCountry);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro2, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd2);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndEur.get(countryAlpha3)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd2);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro2), eq(bidRequest), eq("EUR"), eq("USD"));

        verifyNoInteractions(geoLocationService, countryCodeMapper);
    }

    @Test
    public void testResolveCountryAndCreateModuleContextWithInvalidCountry() throws Exception {
        final Device deviceWithValidCountry = Device.builder()
                .geo(Geo.builder()
                        .country(invalidCountry)
                        .build())
                .build();
        BidRequest bidRequest = getBidRequestWithDeviceAndFloorsConfig(null, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsEmpty, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndUSD, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndUSD, deviceWithValidCountry);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndEUR, deviceWithValidCountry);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndEUR.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndEur, deviceWithValidCountry);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(invalidCountry);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndEur.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        verifyNoInteractions(geoLocationService, countryCodeMapper);
    }

    @Test
    public void testResolveCountryAndCreateModuleContextWithValidIp() throws Exception {
        when(geoLocationService.lookup(validIp, timeout)).thenReturn(Future.succeededFuture(validIpToGeoInfo));
        when(countryCodeMapper.mapToAlpha3(countryAlpha2)).thenReturn(countryAlpha3);

        final Device device = Device.builder()
                .ip(validIp)
                .build();
        // Test # 1
        BidRequest bidRequest = getBidRequestWithDeviceAndFloorsConfig(null, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        // Test # 2
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsEmpty, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        // Test # 3
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndUSD, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        // Test # 4
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndUSD, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndUSD.get(countryAlpha3)
            );
        });

        // Test # 5
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndEUR, device);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndEUR.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        // Test # 6
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndEur, device);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro2, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd2);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isEqualTo(countryAlpha3);
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndEur.get(countryAlpha3)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd2);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro2), eq(bidRequest), eq("EUR"), eq("USD"));

        verify(geoLocationService, times(6)).lookup(validIp, timeout);
        verify(countryCodeMapper, times(6)).mapToAlpha3(countryAlpha2);
    }

    @Test
    public void testResolveCountryAndCreateModuleContextWithInvalidIp() throws Exception {
        when(geoLocationService.lookup(invalidIp, timeout))
                .thenReturn(Future.succeededFuture(invalidIpToGeoInfo));

        final Device device = Device.builder()
                .ip(invalidIp)
                .build();
        // Test # 1
        BidRequest bidRequest = getBidRequestWithDeviceAndFloorsConfig(null, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        // Test # 2
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsEmpty, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isNull();
        });

        // Test # 3
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndUSD, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        // Test # 4
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndUSD, device);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndUSD.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
        });

        // Test # 5
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithDefaultAndEUR, device);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD")
        ).thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithDefaultAndEUR.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        // Test # 6
        bidRequest = getBidRequestWithDeviceAndFloorsConfig(floorsWithCountryAndEur, device);
        when(currencyConversionService.convertCurrency(bidFloorInEuro, bidRequest, "EUR", "USD"))
                .thenReturn(bidFloorInUsd);
        executeAndValidateResolveCountryAndCreateModuleContext(bidRequest, (context, effectiveFloors) -> {
            assertThat(context.getAlpha3Country()).isNull();
            assertThat(effectiveFloors.get(impId)).isEqualTo(
                    floorsWithCountryAndEur.get(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY)
            );
            final Imp updatedImp = context.getBidRequest().getImp().get(0);
            assertThat(updatedImp.getBidfloor()).isEqualTo(bidFloorInUsd);
            assertThat(updatedImp.getBidfloorcur()).isEqualTo("USD");
        });
        verify(currencyConversionService)
                .convertCurrency(eq(bidFloorInEuro), eq(bidRequest), eq("EUR"), eq("USD"));

        verify(geoLocationService, times(6)).lookup(invalidIp, timeout);
        verifyNoInteractions(countryCodeMapper);
    }

    private void executeAndValidateResolveCountryAndCreateModuleContext(
            BidRequest bidRequest,
            BiConsumer<HooksModuleContext, Map<String, Floor>> validator
    ) {
        Future<HooksModuleContext> result = customVastUtils
                .resolveCountryAndCreateModuleContext(bidRequest, timeout);
        assertThat(result).isNotNull();
        result.onComplete(asyncResult -> {
            assertThat(asyncResult.succeeded()).isTrue();
            HooksModuleContext context = asyncResult.result();
            assertThat(context.getImpIdToEffectiveFloor().size()).isEqualTo(1);
            if (validator != null) {
                validator.accept(context, context.getImpIdToEffectiveFloor());
            }
        });
    }

    private BidRequest getBidRequestWithDeviceAndFloorsConfig(Map<String, Floor> floorsConfig, Device device) {
        return getStoredRequest(defaultRequestId, bidRequest1 -> {
            Imp imp = getStoredImp(defaultStoredImpId)
                    .toBuilder()
                    .id(impId).build();

            imp = setImpConfigProperties(imp, configNode -> {
                JsonNode floorNode = mapper.valueToTree(floorsConfig);
                configNode.set("floors", floorNode);
            });

            return bidRequest1.toBuilder()
                    .imp(
                        new ArrayList<>(List.of(imp))
                    ).device(device)
                    .build();
        });
    }

    // @Test
    public void testCreateModuleContext() throws Exception {
        HooksModuleContext result = customVastUtils.createModuleContext(null, "alpha3Country");
        Assert.assertEquals(null, result);
    }

    // @Test
    public void testResolveGamAdUnit() throws Exception {
        String result = customVastUtils.resolveGamAdUnit(null, 0);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testGetCachedBidUrls() throws Exception {
        List<String> result = customVastUtils.getCachedBidUrls(List.of(null), true);
        Assert.assertEquals(List.of("String"), result);
    }

    @Test
    public void testResolveFloorBucketPriceWithEuro() {
        String bidFloorCur = "EUR";

        String result = customVastUtils.resolveGamFloorPrice(0.01, bidFloorCur, null);
        assertThat(result).isEqualTo("0.01");

        result = customVastUtils.resolveGamFloorPrice(0.012, bidFloorCur, null);
        assertThat(result).isEqualTo("0.01");

        result = customVastUtils.resolveGamFloorPrice(0.016, bidFloorCur, null);
        assertThat(result).isEqualTo("0.02");

        result = customVastUtils.resolveGamFloorPrice(0.092, bidFloorCur, null);
        assertThat(result).isEqualTo("0.09");

        result = customVastUtils.resolveGamFloorPrice(0.099, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        result = customVastUtils.resolveGamFloorPrice(0.1, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        result = customVastUtils.resolveGamFloorPrice(0.12, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        result = customVastUtils.resolveGamFloorPrice(0.13, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        result = customVastUtils.resolveGamFloorPrice(0.15, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        result = customVastUtils.resolveGamFloorPrice(0.16, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        result = customVastUtils.resolveGamFloorPrice(2.96, bidFloorCur, null);
        assertThat(result).isEqualTo("2.95");

        result = customVastUtils.resolveGamFloorPrice(2.99, bidFloorCur, null);
        assertThat(result).isEqualTo("3");

        result = customVastUtils.resolveGamFloorPrice(3.00, bidFloorCur, null);
        assertThat(result).isEqualTo("3");

        result = customVastUtils.resolveGamFloorPrice(3.09, bidFloorCur, null);
        assertThat(result).isEqualTo("3.1");

        result = customVastUtils.resolveGamFloorPrice(9.99, bidFloorCur, null);
        assertThat(result).isEqualTo("10");

        result = customVastUtils.resolveGamFloorPrice(11.991, bidFloorCur, null);
        assertThat(result).isEqualTo("11.99");

        result = customVastUtils.resolveGamFloorPrice(11.996, bidFloorCur, null);
        assertThat(result).isEqualTo("12");
    }

    @Test
    public void testResolveFloorBucketPriceWithUSD() {
        String bidFloorCur = "USD";
        double bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.01, null);
        String result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.01");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.012, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.01");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.016, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.02");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.092, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.09");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.099, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.1, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.12, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.1");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.13, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.15, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(0.16, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("0.15");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(2.96, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("2.95");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(2.99, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("3");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(3.00, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("3");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(3.09, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("3.1");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(9.99, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("10");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(11.991, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("11.99");

        bidFloorInUsd = convertEuroToUsdAndStubMockedCurrencyConversionService(12.00, null);
        result = customVastUtils.resolveGamFloorPrice(bidFloorInUsd, bidFloorCur, null);
        assertThat(result).isEqualTo("12");
    }

    // @Test
    public void testFormatPrebidGamKeyValueString() throws Exception {
        String result = customVastUtils.formatPrebidGamKeyValueString(List.of(null), true);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testGetCustParams() throws Exception {
        String result = customVastUtils.getCustParams(null, null);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testGetRedirect() throws Exception {
        String result = customVastUtils.getRedirect("bidder", "gdpr", "gdprConsent", "userIdParamName");
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testReplaceMacros() throws Exception {
        String result = customVastUtils.replaceMacros("tag", null);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testBuildGamVastTagUrl() throws Exception {
        String result = customVastUtils.buildGamVastTagUrl(null, null);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testResolveGamOutputFromOrtb() throws Exception {
        String result = CustomVastUtils.resolveGamOutputFromOrtb(List.of(Integer.valueOf(0)));
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testCustomVastToXml() throws Exception {
        String result = CustomVastUtils.customVastToXml(null);
        Assert.assertEquals("replaceMeWithExpectedResult", result);
    }

    // @Test
    public void testCustomVastFromXml() throws Exception {
        CustomVast result = CustomVastUtils.customVastFromXml("xml");
        Assert.assertEquals(null, result);
    }

    private BigDecimal convertEuroToUsd(BigDecimal priceInEuro) {
        return priceInEuro.multiply(usdToEuroConversionRate)
                .setScale(3, RoundingMode.HALF_EVEN);
    }

    private double convertEuroToUsdAndStubMockedCurrencyConversionService(double priceInEuro, BidRequest bidRequest) {
        BigDecimal from = BigDecimal.valueOf(priceInEuro);
        double priceInUsd = convertEuroToUsd(BigDecimal.valueOf(priceInEuro)).doubleValue();
        when(currencyConversionService.convertCurrency(BigDecimal.valueOf(priceInUsd), bidRequest, "USD", "EUR"))
                .thenReturn(from);
        return priceInUsd;
    }
}
