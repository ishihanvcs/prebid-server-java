package com.improvedigital.prebid.server.customvast.requestfactory;

import com.improvedigital.prebid.server.customvast.model.CustParams;
import com.improvedigital.prebid.server.customvast.model.GVastHandlerParams;
import com.improvedigital.prebid.server.customvast.resolvers.GVastHandlerParamsResolver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GVastHandlerParamsResolverTest extends VertxTest {

    private static final String DEFAULT_ABSOLUTE_URI = "http://example.com/gvast";
    private static final String VALID_COUNTRY_ALPHA2_NL = "nl";
    private static final String VALID_COUNTRY_ALPHA2_BD = "bd";
    private static final String VALID_COUNTRY_ALPHA3_NLD = "NLD";
    private static final String VALID_COUNTRY_ALPHA3_BGD = "BGD";
    private static final String INVALID_COUNTRY_ALPHA2 = "xy";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private GVastHandlerParamsResolver target;

    private GdprConfig gdprConfig;

    @Mock
    private CountryCodeMapper countryCodeMapper;

    @Before
    public void setUp() {
        gdprConfig = GdprConfig.builder().enabled(true).defaultValue("1").build();
        target = new GVastHandlerParamsResolver(countryCodeMapper, gdprConfig);
    }

    @Test
    public void shouldFailWhenPlacementIdIsMissing() {
        HttpRequestContext httpRequest = emptyRequestBuilder().build();
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> target.resolve(httpRequest))
                .withMessage("'p' parameter is required");
    }

    @Test
    public void shouldResolveWithPParamOnly() {
        HttpRequestContext httpRequest = emptyRequestBuilder()
                .queryParams(
                    minQueryParamsBuilder().build()
                ).build();

        GVastHandlerParams result = target.resolve(httpRequest);
        GVastHandlerParams expected = emptyParamsBuilder()
                .impId("1")
                .build();

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        assertThat(result.getDomain()).isEqualTo(HttpUtil.getHostFromUrl(DEFAULT_ABSOLUTE_URI));
    }

    @Test
    public void shouldSetGdpr() {
        HttpRequestContext httpRequest = emptyRequestBuilder()
                .queryParams(
                    minQueryParamsBuilder()
                        .add("gdpr", "1")
                        .build()
                ).build();
        GVastHandlerParams result = target.resolve(httpRequest);
        GVastHandlerParams expected = emptyParamsBuilder()
                .impId("1")
                .gdpr("1")
                .build();

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    public void shouldConvertValidAlpha2Country() {
        when(countryCodeMapper.mapToAlpha3(VALID_COUNTRY_ALPHA2_NL)).thenReturn(VALID_COUNTRY_ALPHA3_NLD);
        HttpRequestContext httpRequest = emptyRequestBuilder()
                .queryParams(
                        minQueryParamsBuilder()
                                .add("country", VALID_COUNTRY_ALPHA2_NL)
                                .build()
                ).build();
        GVastHandlerParams result = target.resolve(httpRequest);
        verify(countryCodeMapper, times(1)).mapToAlpha3(any());
        GVastHandlerParams expected = emptyParamsBuilder()
                .impId("1")
                .alpha3Country(VALID_COUNTRY_ALPHA3_NLD)
                .build();

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    public void shouldIgnoreInvalidAlpha2Country() {
        when(countryCodeMapper.mapToAlpha3(INVALID_COUNTRY_ALPHA2)).thenReturn(null);
        HttpRequestContext httpRequest = emptyRequestBuilder()
                .queryParams(
                        minQueryParamsBuilder()
                                .add("country", INVALID_COUNTRY_ALPHA2)
                                .build()
                ).build();
        GVastHandlerParams result = target.resolve(httpRequest);
        verify(countryCodeMapper, times(1)).mapToAlpha3(any());
        GVastHandlerParams expected = emptyParamsBuilder()
                .impId("1")
                .alpha3Country(null)
                .build();

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    public void shouldPrioritizeAlpha3CountryIfFoundInQueryParams() {
        HttpRequestContext httpRequest = emptyRequestBuilder()
                .queryParams(
                        minQueryParamsBuilder()
                                .add("country", VALID_COUNTRY_ALPHA2_BD)
                                .add("country_alpha3", VALID_COUNTRY_ALPHA3_NLD)
                                .build()
                ).build();
        GVastHandlerParams result = target.resolve(httpRequest);
        verify(countryCodeMapper, never()).mapToAlpha3(any());
        GVastHandlerParams expected = emptyParamsBuilder()
                .impId("1")
                .alpha3Country(VALID_COUNTRY_ALPHA3_NLD)
                .build();

        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    public void shouldSetCustParams() {
        GVastHandlerParams result = target.resolve(emptyRequestBuilder()
                .queryParams(
                        minQueryParamsBuilder()
                                .add("cust_params", "tnl_pid=P%2017100600022&tnl_asset_id=game_preroll&fp=0.01")
                                .build()
                ).build());

        assertThat(result.getCustParams().size()).isEqualTo(3);

        assertThat(result.getCustParams().get("tnl_pid").size()).isEqualTo(1);
        assertThat(result.getCustParams().get("tnl_pid").contains("P 17100600022")).isTrue();

        assertThat(result.getCustParams().get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(result.getCustParams().get("tnl_asset_id").contains("game_preroll")).isTrue();

        assertThat(result.getCustParams().get("fp").size()).isEqualTo(1);
        assertThat(result.getCustParams().get("fp").contains("0.01")).isTrue();

        result = target.resolve(emptyRequestBuilder()
                .queryParams(
                        minQueryParamsBuilder()
                                .add("cust_params", "tnl_asset_id=game_preroll,abc")
                                .build()
                ).build());

        assertThat(result.getCustParams().size()).isEqualTo(1);
        assertThat(result.getCustParams().get("tnl_asset_id").size()).isEqualTo(2);
        assertThat(result.getCustParams().get("tnl_asset_id").contains("game_preroll")).isTrue();
        assertThat(result.getCustParams().get("tnl_asset_id").contains("abc")).isTrue();
    }

    private HttpRequestContext.HttpRequestContextBuilder emptyRequestBuilder() {
        return HttpRequestContext.builder()
            .queryParams(CaseInsensitiveMultiMap.empty())
            .headers(CaseInsensitiveMultiMap.empty())
            .absoluteUri(DEFAULT_ABSOLUTE_URI);
    }

    private CaseInsensitiveMultiMap.Builder minQueryParamsBuilder() {
        return CaseInsensitiveMultiMap.builder()
            .add("p", "1");
    }

    private GVastHandlerParams.GVastHandlerParamsBuilder<?, ?> emptyParamsBuilder() {
        return GVastHandlerParams.builder()
            .gdpr(gdprConfig.getDefaultValue())
            .gdprConsent("")
            .cat(new ArrayList<>())
            .referrer(DEFAULT_ABSOLUTE_URI)
            .custParams(new CustParams());
    }
}
