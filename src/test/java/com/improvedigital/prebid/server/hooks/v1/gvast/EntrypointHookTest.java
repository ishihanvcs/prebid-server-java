package com.improvedigital.prebid.server.hooks.v1.gvast;

import ch.qos.logback.classic.Level;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import io.vertx.core.Future;
import nl.altindag.log.LogCaptor;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

public class EntrypointHookTest extends UnitTestBase {

    @Mock
    SettingsLoader settingsLoader;

    EntrypointHook hook;

    private final String defaultAccountId = "2018";
    private final String defaultStoredRequestId = "stored-request";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        hook = new EntrypointHook(
                settingsLoader,
                requestUtils,
                merger
        );
    }

    @Test
    public void shouldNotModifyBidRequestWhenAccountIdAndRequestIdMisMatchedInImps() {
        final String storedImpId = "video-basic";
        final String bidRequestId = "minimal";
        final Set<String> storedImpIds = Set.of();

        final Imp imp1 = getStoredImp(storedImpId, imp -> setImpConfigProperties(imp, configNode -> {
            configNode.put("accountId", "1");
            configNode.put("requestId", "1");
        }).toBuilder().id("1").build());

        final Imp imp2 = getStoredImp(storedImpId, imp -> setImpConfigProperties(imp, configNode -> {
            configNode.put("accountId", "2");
            configNode.put("requestId", "2");
        }).toBuilder().id("2").build());

        when(settingsLoader.getStoredImpsSafely(storedImpIds, timeout)).thenReturn(
                createSucceededFutureWithImps(
                        storedImpIds
                )
        );

        final LogCaptor logCaptor = LogCaptor.forClass(this.hook.getClass());

        executeHookAndValidateBidRequest(
                createEntrypointPayload(
                        bidRequestId,
                        bidRequest -> bidRequest.toBuilder().imp(List.of(imp1, imp2)).build()
                ),
                timeout,
                (originalBidRequest, updatedBidRequest) -> {
                    assertThat(originalBidRequest)
                            .describedAs("bidRequest should not be modified")
                            .isEqualTo(updatedBidRequest);
                    assertThat(logCaptor.getWarnLogs())
                            .isNotEmpty();

                    assertThat(logCaptor.getWarnLogs().size())
                            .isEqualTo(2);

                    assertThat(
                            hasLogEventWith(logCaptor,
                                    "accountId mismatched in imp[].prebid.improvedigitalpbs",
                                    Level.WARN
                            )
                    ).isTrue();

                    assertThat(
                            hasLogEventWith(logCaptor,
                                    "requestId mismatched in imp[].prebid.improvedigitalpbs",
                                    Level.WARN
                            )
                    ).isTrue();
                }
        );
    }

    @Test
    public void shouldNotModifyBidRequestWhenParentAccountAndRequestIdIsEmpty() {
        final String storedImpId = "video-basic";
        final String bidRequestId = "minimal";
        final Set<String> storedImpIds = Set.of(storedImpId);
        final Map<String, String> impToStoredIdMap = new HashMap<>() {{
                put("1", storedImpId);
            }};

        when(settingsLoader.getStoredImpsSafely(storedImpIds, timeout)).thenReturn(
                createSucceededFutureWithImps(
                        storedImpIds
                )
        );

        executeHookAndValidateBidRequest(
                createEntrypointPayload(
                        bidRequestId,
                        bidRequest -> setStoredImpIds(bidRequest, impToStoredIdMap)
                ),
                timeout,
                (originalBidRequest, updatedBidRequest) -> assertThat(originalBidRequest)
                        .describedAs("bidRequest should not be modified")
                        .isEqualTo(updatedBidRequest)
        );
    }

    @Test
    public void shouldSetAccountIdAndRequestIdFromImpConfig() {
        final String storedImpId = "video-basic";
        final String bidRequestId = "minimal";
        final Set<String> storedImpIds = Set.of(storedImpId);
        final Map<String, String> impToStoredIdMap = new HashMap<>() {{
                put("1", storedImpId);
            }};

        when(settingsLoader.getStoredImpsSafely(storedImpIds, timeout)).thenReturn(
                createSucceededFutureWithImps(
                        storedImpIds,
                        imp -> setImpConfigProperties(imp, configNode -> {
                            configNode.put("accountId", defaultAccountId);
                            configNode.put("requestId", defaultStoredRequestId);
                        })
                )
        );

        executeHookAndValidateBidRequest(
                createEntrypointPayload(
                        bidRequestId,
                        bidRequest -> setStoredImpIds(bidRequest, impToStoredIdMap)
                ),
                timeout,
                (originalBidRequest, updatedBidRequest) -> {
                    assertThat(updatedBidRequest)
                            .isNotNull();
                    assertThat(updatedBidRequest.getImp())
                            .isNotNull();
                    assertThat(updatedBidRequest.getImp())
                            .isNotEmpty();
                    assertThat(updatedBidRequest.getImp().size())
                            .isEqualTo(1);
                    final String parentAccountId = updatedBidRequest.getSite()
                            .getPublisher().getExt().getPrebid().getParentAccount();
                    assertThat(parentAccountId)
                            .isNotNull();
                    assertThat(parentAccountId)
                            .isEqualTo(defaultAccountId);
                    final String storedRequestId = updatedBidRequest.getExt()
                            .getPrebid().getStoredrequest().getId();
                    assertThat(storedRequestId)
                            .isNotNull();
                    assertThat(storedRequestId)
                            .isEqualTo(defaultStoredRequestId);
                }
        );
    }

    @Test
    public void testCode() throws Exception {
        String result = hook.code();
        assertThat(result)
                .isEqualTo("improvedigital-gvast-hooks-entrypoint");
    }

    private void executeHookAndValidateBidRequest(
            EntrypointPayload payload,
            Timeout timeout,
            BiConsumer<BidRequest/*originalBidRequest*/, BidRequest/*updatedBidRequest*/> validator
    ) {
        executeHookAndValidatePayloadUpdate(
                hook,
                payload,
                createInvocationContext(timeout),
                (initialPayload, updatedPayload) -> {
                String updatedBody = updatedPayload.body();
                assertThat(updatedBody)
                        .isNotNull();
                BidRequest originalBidRequest = bidRequestFromString(initialPayload.body());
                BidRequest updatedBidRequest = bidRequestFromString(updatedBody);
                validator.accept(originalBidRequest, updatedBidRequest);
            }
        );
    }

    private Future<Map<String, Imp>> createSucceededFutureWithImps(Set<String> storedImpIds) {
        return createSucceededFutureWithImps(storedImpIds, null);
    }

    private Future<Map<String, Imp>> createSucceededFutureWithImps(
            Set<String> storedImpIds, Function<Imp, Imp> impModifier
    ) {
        final Map<String, Imp> imps = new HashedMap<>();
        for (final String storedImpId: storedImpIds) {
            Imp imp = getStoredImp(storedImpId, impModifier);
            if (imp != null) {
                imps.put(storedImpId, imp);
            }
        }
        return Future.succeededFuture(imps);
    }

    private EntrypointPayload createEntrypointPayload(String requestId) {
        return createEntrypointPayload(requestId, null);
    }

    private EntrypointPayload createEntrypointPayload(
            String requestId, Function<BidRequest, BidRequest> requestModifier
    ) {
        String requestBody = getStoredRequestAsString(requestId, requestModifier);
        return EntrypointPayloadImpl.of(
                CaseInsensitiveMultiMap.empty(),
                CaseInsensitiveMultiMap.empty(),
                requestBody
        );
    }

    private InvocationContext createInvocationContext(Timeout timeout) {
        return InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction);
    }
}
