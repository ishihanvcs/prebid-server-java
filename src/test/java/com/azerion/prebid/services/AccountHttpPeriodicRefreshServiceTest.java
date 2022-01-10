package com.azerion.prebid.services;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AccountHttpPeriodicRefreshServiceTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://config.prebid.com";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CachingApplicationSettings cachingApplicationSettings;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Vertx vertx;

    private final Map<String, Account> expectedAccounts = singletonMap("id1", Account.empty("id1"));

    @Before
    public void setUp() throws Exception {
        HttpClientResponse updatedResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(
                        HttpAccountsResponse.of(expectedAccounts)
                )
        );

        given(httpClient.get(matches("[?&]accounts=true&last-modified="), any(), anyLong()))
                .willReturn(Future.succeededFuture(updatedResponse));
    }

    @Test
    public void creationShouldFailOnInvalidUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> createAndInitService(cachingApplicationSettings,
                "invalid_url", 1, 1, 1, vertx, httpClient));
    }

    @Test
    public void initializeShouldNotMakeAnyRequestIfRefreshPeriodIsNegative() {
        // when
        createAndInitService(cachingApplicationSettings, ENDPOINT_URL,
                -1, 2000, 5000, vertx, httpClient);

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(httpClient, never()).get(anyString(), anyLong());
    }

    @Test
    public void initializeShouldNotMakeAnyRequestIfRefreshPeriodIsGreaterThanCacheTtl() {
        // when
        createAndInitService(cachingApplicationSettings, ENDPOINT_URL,
                1000, 2000, 100, vertx, httpClient);

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(httpClient, never()).get(anyString(), anyLong());
    }

    @Test
    public void shouldModifyEndpointUrlCorrectlyIfUrlHasParameters() {
        final String urlWithParam = ENDPOINT_URL + "?param=value";
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(cachingApplicationSettings, urlWithParam,
                1000, 2000, 5000, vertx, httpClient);

        // then
        verify(httpClient).get(startsWith(urlWithParam + "&accounts=true&last-modified="), any(), anyLong());
    }

    @Test
    public void shouldCallInvalidateAfterUpdate() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(cachingApplicationSettings, ENDPOINT_URL,
                1000, 2000, 5000, vertx, httpClient);

        // then
        verify(cachingApplicationSettings, times(expectedAccounts.entrySet().size()))
                .invalidateAccountCache(anyString());
    }

    @After
    public void tearDown() throws Exception {
    }

    private static void createAndInitService(CachingApplicationSettings cachingApplicationSettings,
                                             String url, long refreshPeriod, long timeout, long cacheTtl,
                                             Vertx vertx, HttpClient httpClient) {
        final AccountHttpPeriodicRefreshService service = new AccountHttpPeriodicRefreshService(
                cachingApplicationSettings, url, refreshPeriod, timeout, cacheTtl, vertx, httpClient, jacksonMapper
        );
        service.initialize();
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T... objects) {
        return inv -> {
            // invoking handler right away passing mock to it
            for (T obj : objects) {
                ((Handler<T>) inv.getArgument(1)).handle(obj);
            }
            return 0L;
        };
    }
}
