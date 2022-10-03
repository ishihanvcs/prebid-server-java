package com.improvedigital.prebid.server.services;

import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.utils.ReflectionUtils;
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
import org.prebid.server.execution.Timeout;
import org.prebid.server.floors.PriceFloorsConfigResolver;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.proto.response.HttpAccountsResponse;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AccountHttpPeriodicRefreshServiceTest extends UnitTestBase {

    private static final String ENDPOINT_URL = "http://config.prebid.com";

    private static final String NULL_ACCOUNT_CONFIG = null;
    private static final String DEFAULT_ACCOUNT_CONFIG = "{\n"
            + "  \"auction\": {\n"
            + "    \"price-floors\": {\n"
            + "      \"enabled\": true,\n"
            + "      \"fetch\": {\n"
            + "        \"enabled\": false,\n"
            + "        \"timeout-ms\": 5000,\n"
            + "        \"max-rules\": 0,\n"
            + "        \"max-file-size-kb\": 200,\n"
            + "        \"max-age-sec\": 86400,\n"
            + "        \"period-sec\": 3600\n"
            + "      },\n"
            + "      \"enforce-floors-rate\": 100,\n"
            + "      \"adjust-for-bid-adjustment\": true,\n"
            + "      \"enforce-deal-floors\": true,\n"
            + "      \"use-dynamic-data\": true\n"
            + "    }\n"
            + "  }\n"
            + "}";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    ApplicationSettings delegate;

    @Mock
    SettingsCache cache;

    @Mock
    SettingsCache ampCache;

    @Mock
    SettingsCache videoCache;

    @Mock
    Metrics metrics;

    @Mock
    Timeout timeout;

    // @Mock
    private CachingApplicationSettings cachingApplicationSettings;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Vertx vertx;

    private final String accountId = "id1";
    private final Account emptyAccount = Account.empty(accountId);
    private final Account mergedAccount = merger.merge(
            emptyAccount,
            jacksonMapper.decodeValue(DEFAULT_ACCOUNT_CONFIG, Account.class),
            Account.class
    );
    private final Map<String, Account> expectedAccounts = singletonMap(accountId, emptyAccount);
    private final Map<String, Account> expectedMergedAccounts = singletonMap(accountId, mergedAccount);
    private Map<String, Account> accountCache;

    @Before
    public void setUp() throws Exception {
        HttpClientResponse updatedResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(
                        HttpAccountsResponse.of(expectedAccounts)
                )
        );

        given(httpClient.get(matches("[?&]accounts=true"), any(), anyLong()))
                .willReturn(Future.succeededFuture(updatedResponse));

        cachingApplicationSettings = new CachingApplicationSettings(
                delegate, cache, ampCache, videoCache, metrics, 1000, 100
        );

        accountCache = ReflectionUtils.getPrivateProperty(
                "accountCache", cachingApplicationSettings, CachingApplicationSettings.class
        );
    }

    @Test
    public void creationShouldFailOnInvalidUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> createAndInitService(
                cachingApplicationSettings, "invalid_url",
                1, 1, 1,
                vertx, httpClient, metrics
        ));
    }

    @Test
    public void initializeShouldNotMakeAnyRequestIfRefreshPeriodIsNegative() {
        // when
        createAndInitService(
                cachingApplicationSettings, ENDPOINT_URL,
                -1, 2000, 5000,
                vertx, httpClient, metrics
        );

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(httpClient, never()).get(anyString(), anyLong());
    }

    @Test
    public void initializeShouldNotMakeAnyRequestIfRefreshPeriodIsGreaterThanCacheTtl() {
        // when
        createAndInitService(
                cachingApplicationSettings, ENDPOINT_URL,
                1000, 2000, 100,
                vertx, httpClient, metrics
        );

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
        createAndInitService(
                cachingApplicationSettings, urlWithParam, 1000, 2000, 5000,
                vertx, httpClient, metrics
        );

        // then
        verify(httpClient, atLeast(2))
                .get(startsWith(urlWithParam + "&accounts=true"), any(), anyLong());
        verify(httpClient, atLeastOnce())
                .get(startsWith(urlWithParam + "&accounts=true&last-modified="), any(), anyLong());
    }

    @Test
    public void shouldUpdateAccountCacheAfterPeriodicUpdate() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        AccountHttpPeriodicRefreshService service = createService(
                cachingApplicationSettings, ENDPOINT_URL,
                1000, 2000, 5000, vertx, httpClient,
                NULL_ACCOUNT_CONFIG, metrics
        );

        assertThat(service.getLastUpdateTime()).isNull();
        assertThat(accountCache.isEmpty()).isTrue();

        service.initialize();

        // then
        verify(httpClient, atLeast(2))
                .get(startsWith(ENDPOINT_URL + "?accounts=true"), any(), anyLong());
        verify(httpClient, atLeastOnce())
                .get(startsWith(ENDPOINT_URL + "?accounts=true&last-modified="), any(), anyLong());

        assertThat(service.getLastUpdateTime()).isNotNull();
        assertThat(accountCache.size()).isEqualTo(expectedAccounts.size());
        assertThat(accountCache.get(accountId)).isEqualTo(emptyAccount);

        // TODO: verify log entries generated for successful saving of accounts using LogCaptor
    }

    @Test
    public void shouldUpdateAccountCacheAfterPeriodicUpdateWithMergedAccount() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        AccountHttpPeriodicRefreshService service = createService(
                cachingApplicationSettings, ENDPOINT_URL,
                1000, 2000, 5000, vertx, httpClient,
                DEFAULT_ACCOUNT_CONFIG, metrics
        );

        assertThat(service.getLastUpdateTime()).isNull();
        assertThat(accountCache.isEmpty()).isTrue();

        service.initialize();

        // then
        verify(httpClient, atLeast(2))
                .get(startsWith(ENDPOINT_URL + "?accounts=true"), any(), anyLong());
        verify(httpClient, atLeastOnce())
                .get(startsWith(ENDPOINT_URL + "?accounts=true&last-modified="), any(), anyLong());

        assertThat(service.getLastUpdateTime()).isNotNull();
        assertThat(accountCache.size()).isEqualTo(expectedMergedAccounts.size());
        assertThat(accountCache.get(accountId)).isEqualTo(mergedAccount);

        // TODO: verify log entries generated for successful saving of accounts using LogCaptor
    }

    @Test
    public void shouldNotCallDelegateMethodAfterPeriodicRefresh() {
        given(delegate.getAccountById(any(), any())).willReturn(Future.succeededFuture(emptyAccount));

        cachingApplicationSettings.getAccountById(accountId, timeout)
                .onComplete(asyncResult -> {
                    verify(delegate, times(1)).getAccountById(any(), any()); // one call
                    assertThat(asyncResult.succeeded()).isTrue();
                    assertThat(asyncResult.result()).isEqualTo(emptyAccount);
                });

        accountCache.remove(accountId); // reset accountCache
        shouldUpdateAccountCacheAfterPeriodicUpdate(); // update accountCache with periodic refresher

        cachingApplicationSettings.getAccountById(accountId, timeout)
                .onComplete(asyncResult -> {
                    verify(delegate, times(1)).getAccountById(any(), any()); // no new call
                    assertThat(asyncResult.succeeded()).isTrue();
                    assertThat(asyncResult.result()).isEqualTo(emptyAccount);
                });

    }

    @After
    public void tearDown() throws Exception {
    }

    private static AccountHttpPeriodicRefreshService createService(
            CachingApplicationSettings cachingApplicationSettings, String url, long refreshPeriod,
            long timeout, long cacheTtl, Vertx vertx, HttpClient httpClient,
            String accountConfig, Metrics metrics
    ) {
        PriceFloorsConfigResolver priceFloorsConfigResolver = new PriceFloorsConfigResolver(
                accountConfig, metrics, jacksonMapper
        );
        return new AccountHttpPeriodicRefreshService(
                cachingApplicationSettings, url, refreshPeriod,
                timeout, cacheTtl, vertx, httpClient,
                accountConfig, priceFloorsConfigResolver,
                merger, jacksonMapper
        );
    }

    private static void createAndInitService(
            CachingApplicationSettings cachingApplicationSettings, String url, long refreshPeriod,
            long timeout, long cacheTtl, Vertx vertx, HttpClient httpClient,
            Metrics metrics
    ) {
        final AccountHttpPeriodicRefreshService service = createService(
                cachingApplicationSettings, url, refreshPeriod, timeout,
                cacheTtl, vertx, httpClient, NULL_ACCOUNT_CONFIG, metrics
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
