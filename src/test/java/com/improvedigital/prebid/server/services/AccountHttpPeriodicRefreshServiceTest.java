package com.improvedigital.prebid.server.services;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.settings.proto.response.HttpAccountRefreshResponse;
import com.improvedigital.prebid.server.utils.ReflectionUtils;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import nl.altindag.log.LogCaptor;
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
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AccountHttpPeriodicRefreshServiceTest extends UnitTestBase {

    private static final String ENDPOINT_URL = "https://config.prebid.com";

    private static final String NULL_ACCOUNT_CONFIG = null;
    private static final String DEFAULT_ACCOUNT_CONFIG = """
            {
              "auction": {
                "price-floors": {
                  "enabled": true,
                  "fetch": {
                    "enabled": false,
                    "timeout-ms": 5000,
                    "max-rules": 0,
                    "max-file-size-kb": 200,
                    "max-age-sec": 86400,
                    "period-sec": 3600
                  },
                  "enforce-floors-rate": 100,
                  "adjust-for-bid-adjustment": true,
                  "enforce-deal-floors": true,
                  "use-dynamic-data": true
                }
              }
            }""";

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

    private final String accountId1 = "id1";
    private final Account account1 = Account.empty(accountId1);
    private final String accountId2 = "id2";
    private final Account account2 = Account.empty(accountId2);
    private final Account mergedAccount2 = merger.merge(
            account2,
            jacksonMapper.decodeValue(DEFAULT_ACCOUNT_CONFIG, Account.class),
            Account.class
    );

    private final ObjectNode deletedNode = mapper.createObjectNode()
            .put("deleted", true);

    private final Map<String, ObjectNode> initialResponseData = singletonMap(
            accountId1, mapper.valueToTree(account1)
    );

    private final Map<String, ObjectNode> refreshResponseData = new HashMap<>() {{
            put(accountId1, deletedNode);
            put(accountId2, mapper.valueToTree(account2));
        }};
    private final Map<String, Account> mergedAccountsAfterRefresh = new HashMap<>() {{
            put(accountId2, mergedAccount2);
        }};
    private Map<String, Account> accountCache;

    private final LogCaptor logCaptor = LogCaptor.forClass(AccountHttpPeriodicRefreshService.class);

    @Before
    public void setUp() throws Exception {
        HttpClientResponse initialResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(
                        HttpAccountRefreshResponse.of(initialResponseData)
                )
        );

        HttpClientResponse refreshResponse = HttpClientResponse.of(200, null,
                mapper.writeValueAsString(
                        HttpAccountRefreshResponse.of(
                                this.refreshResponseData
                        )
                )
        );

        given(httpClient.get(matches("[?&]accounts=true$"), any(), anyLong()))
                .willReturn(Future.succeededFuture(initialResponse));

        given(httpClient.get(matches("[?&]accounts=true&last-modified="), any(), anyLong()))
                .willReturn(Future.succeededFuture(refreshResponse));

        cachingApplicationSettings = new CachingApplicationSettings(
                delegate, cache, ampCache, videoCache, metrics, 1000, 100
        );

        accountCache = ReflectionUtils.getPrivateProperty(
                "accountCache", cachingApplicationSettings, CachingApplicationSettings.class
        );

        logCaptor.setLogLevelToDebug();
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
        verify(httpClient, times(2))
                .get(startsWith(ENDPOINT_URL + "?accounts=true"), any(), anyLong());
        verify(httpClient, times(1))
                .get(startsWith(ENDPOINT_URL + "?accounts=true&last-modified="), any(), anyLong());

        assertThat(service.getLastUpdateTime()).isNotNull();
        assertThat(accountCache.size()).isEqualTo(1);
        assertThat(accountCache.get(accountId1)).isNull();
        assertThat(accountCache.get(accountId2)).isEqualTo(account2);

        assertLogMessages();
    }

    private void assertLogMessages() {
        String message = String.format(
                "Account with id=%s has been saved in cache successfully.",
                accountId1
        );
        assertThat(hasLogEventWith(logCaptor, message, Level.DEBUG)).isTrue();

        message = String.format(
                "Successfully %s %d accounts with ids: %s.", "cached",
                initialResponseData.size(),
                initialResponseData.keySet()
        );
        assertThat(hasLogEventWith(logCaptor, message, Level.INFO)).isTrue();

        message = String.format(
                "Account with id=%s is deleted and hence removed from cache.",
                accountId1
        );
        assertThat(hasLogEventWith(logCaptor, message, Level.DEBUG)).isTrue();

        message = String.format(
                "Account with id=%s has been saved in cache successfully.",
                accountId2
        );
        assertThat(hasLogEventWith(logCaptor, message, Level.DEBUG)).isTrue();

        message = String.format(
                "Successfully %s %d accounts with ids: %s.", "updated",
                refreshResponseData.size(),
                refreshResponseData.keySet()
        );
        assertThat(hasLogEventWith(logCaptor, message, Level.INFO)).isTrue();
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
        assertThat(accountCache.size()).isEqualTo(mergedAccountsAfterRefresh.size());
        assertThat(accountCache.get(accountId2)).isEqualTo(mergedAccount2);

        assertLogMessages();
    }

    @Test
    public void shouldNotCallDelegateMethodAfterPeriodicRefresh() {
        given(delegate.getAccountById(eq(accountId1), eq(timeout))).willReturn(Future.succeededFuture(account1));
        given(delegate.getAccountById(eq(accountId2), eq(timeout))).willReturn(Future.succeededFuture(account2));

        cachingApplicationSettings.getAccountById(accountId1, timeout)
                .onComplete(asyncResult -> {
                    verify(delegate, times(1)).getAccountById(any(), any()); // one call
                    assertThat(asyncResult.succeeded()).isTrue();
                    assertThat(asyncResult.result()).isEqualTo(account1);
                });

        accountCache.remove(accountId1); // reset accountCache
        shouldUpdateAccountCacheAfterPeriodicUpdate(); // update accountCache with periodic refresher

        cachingApplicationSettings.getAccountById(accountId2, timeout)
                .onComplete(asyncResult -> {
                    verify(delegate, times(1)).getAccountById(any(), any()); // no new call
                    assertThat(asyncResult.succeeded()).isTrue();
                    assertThat(asyncResult.result()).isEqualTo(account2);
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
