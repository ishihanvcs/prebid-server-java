package com.improvedigital.prebid.server.customvast.requestfactory;

import com.improvedigital.prebid.server.customvast.model.CustParams;
import com.improvedigital.prebid.server.customvast.model.GVastHandlerParams;
import com.improvedigital.prebid.server.customvast.resolvers.GVastHandlerParamsResolver;
import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class GVastHandlerParamsResolverTest extends VertxTest {

    private static final String DEFAULT_ABSOLUTE_URI = "http://example.com/gvast";

    @Mock
    private Logger logger;

    @Mock
    private ConditionalLogger conditionalLogger;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private GVastHandlerParamsResolver target;

    private GdprConfig gdprConfig;

    @Before
    public void setUp() {
        gdprConfig = GdprConfig.builder().enabled(true).defaultValue("1").build();
        target = new GVastHandlerParamsResolver(gdprConfig);
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

    private GVastHandlerParams.GVastHandlerParamsBuilder emptyParamsBuilder() {
        return GVastHandlerParams.builder()
            .gdpr(gdprConfig.getDefaultValue())
            .gdprConsent("")
            .cat(new ArrayList<>())
            .referrer(DEFAULT_ABSOLUTE_URI)
            .custParams(new CustParams());
    }
}