package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.hooks.v1.HooksTestBase;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import io.vertx.core.Future;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.mockito.Mockito.when;

public class EntrypointHookTest extends HooksTestBase {

    @Mock
    SettingsLoader settingsLoader;

    private final JsonMerger merger = new JsonMerger(mapper);

    EntrypointHook entrypointHook;
    Timeout timeout;

    private final String defaultAccountId = "2018";
    private final String defaultStoredRequestId = "stored-request";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        entrypointHook = new EntrypointHook(
                settingsLoader,
                jsonUtils,
                requestUtils,
                merger
        );
        timeout = createTimeout(1000L);
    }

    @Test
    public void shouldSetAccountIdAndRequestIdFromImpConfig() throws Exception {
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
                    Assert.assertNotNull(updatedBidRequest);
                    Assert.assertNotNull(updatedBidRequest.getImp());
                    Assert.assertEquals(updatedBidRequest.getImp().size(), 1);
                    final String parentAccountId = updatedBidRequest.getSite()
                            .getPublisher().getExt().getPrebid().getParentAccount();
                    Assert.assertNotNull(
                            parentAccountId
                    );
                    Assert.assertEquals(parentAccountId, defaultAccountId);
                    final String storedRequestId = updatedBidRequest.getExt()
                            .getPrebid().getStoredrequest().getId();
                    Assert.assertNotNull(
                            storedRequestId
                    );
                    Assert.assertEquals(storedRequestId, defaultStoredRequestId);
                }
        );
    }

    @Test
    public void testCode() throws Exception {
        String result = entrypointHook.code();
        Assert.assertEquals("improvedigital-gvast-hooks-entrypoint", result);
    }

    private void executeHookAndValidateBidRequest(
            EntrypointPayload payload,
            Timeout timeout,
            BiConsumer<BidRequest/*originalBidRequest*/, BidRequest/*updatedBidRequest*/> validator
    ) {
        executeHookAndValidatePayload(
                entrypointHook,
                payload,
                createInvocationContext(timeout),
                (initialPayload, updatedPayload) -> {
                String updatedBody = updatedPayload.body();
                Assert.assertNotNull(updatedBody);
                BidRequest originalBidRequest = bidRequestFromString(initialPayload.body());
                BidRequest updatedBidRequest = bidRequestFromString(updatedBody);
                validator.accept(originalBidRequest, updatedBidRequest);
            }
        );
    }

    private BidRequest setStoredImpIds(
            BidRequest request, Map<String /*impId*/, String /*storedImpId*/> impIdToStoredIdMap
    ) {
        request.getImp().replaceAll(imp -> {
            if (impIdToStoredIdMap.containsKey(imp.getId())) {
                final String storedImpId = impIdToStoredIdMap.get(imp.getId());
                ExtImp extImp = ExtImp.of(ExtImpPrebid.builder()
                        .storedrequest(ExtStoredRequest.of(storedImpId))
                        .build(), null);
                ObjectNode extNode = objectMapper.valueToTree(extImp);
                return imp.toBuilder().ext(
                        merger.merge(extNode, imp.getExt(), ObjectNode.class)
                ).build();
            }
            return imp;
        });
        return request;
    }

    private Imp setImpConfigProperties(Imp imp, Consumer<ObjectNode> configSetter) {
        final ObjectNode impExt = mapper.mapper().valueToTree(
                ExtImp.of(ExtImpPrebid.builder().build(), null)
        );
        ObjectNode configNode = ((ObjectNode) impExt.at("/prebid"))
                .putObject("improvedigitalpbs");

        configSetter.accept(configNode);

        return imp.toBuilder().ext(
                merger.merge(impExt, imp.getExt(), ObjectNode.class)
        ).build();
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
