package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.CustParams;
import com.azerion.prebid.auction.model.GVastParams;
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

public class GVastParamsResolverTest extends VertxTest {

    private static final String DEFAULT_ABSOLUTE_URI = "http://example.com/gvast";

    @Mock
    private Logger logger;

    @Mock
    private ConditionalLogger conditionalLogger;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private GVastParamsResolver target;

    private GdprConfig gdprConfig;

    @Before
    public void setUp() {
        gdprConfig = GdprConfig.builder().enabled(true).defaultValue("1").build();
        target = new GVastParamsResolver(gdprConfig);
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

        GVastParams result = target.resolve(httpRequest);
        GVastParams expected = emptyParamsBuilder()
                .impId("1")
                .build();

        assertThat(result.equals(expected)).isTrue();
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
        GVastParams result = target.resolve(httpRequest);
        GVastParams expected = emptyParamsBuilder()
                .impId("1")
                .gdpr("1")
                .build();

        assertThat(result.equals(expected)).isTrue();
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

    private GVastParams.GVastParamsBuilder emptyParamsBuilder() {
        return GVastParams.builder()
            .gdpr(gdprConfig.getDefaultValue())
            .gdprConsentString("")
            .cat(new ArrayList<>())
            .referrer(DEFAULT_ABSOLUTE_URI)
            .custParams(new CustParams());
    }
}
