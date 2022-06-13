package com.improvedigital.prebid.server.hooks.v1;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import nl.altindag.log.LogCaptor;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class HooksTestBase extends VertxTest {

    protected static JsonMerger merger;
    protected static JsonUtils jsonUtils;
    protected static RequestUtils requestUtils;

    static {
        merger = new JsonMerger(jacksonMapper);
        jsonUtils = new JsonUtils(jacksonMapper);
        requestUtils = new RequestUtils(jsonUtils);
    }

    protected String resourceDir = null;
    protected Timeout timeout = createTimeout(10000L);

    protected Timeout createTimeout(long timeout) {
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory factory = new TimeoutFactory(clock);
        return factory.create(timeout);
    }

    protected String readResourceContent(String resourcePath) {
        Resource resource = new ClassPathResource(
                resourcePath,
                this.getClass().getClassLoader()
        );
        String content = null;
        if (resource.isFile()) {
            try {
                content = new String(Files.readAllBytes(resource.getFile().toPath()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return content;
    }

    protected String readResourceContentByRelativePath(String relativePath) {
        String resourceDir = StringUtils.defaultIfBlank(
                this.resourceDir,
                this.getClass().getPackageName().replaceAll("\\.", "/")
        );
        final String resourcePath = resourceDir + "/" + relativePath;
        return readResourceContent(resourcePath);
    }

    protected String readJsonResource(String jsonPath) {
        return readResourceContent(jsonPath + ".json");
    }

    protected String readJsonResourceByRelativePath(String relativePath) {
        return readResourceContentByRelativePath(relativePath + ".json");
    }

    protected String readStoredImpContent(String storedImpId) {
        return readJsonResourceByRelativePath("imps/" + storedImpId);
    }

    protected Imp getStoredImp(String storedImpId) {
        return getStoredImp(storedImpId, null);
    }

    protected Imp getStoredImp(String storedImpId, Function<Imp, Imp> modifier) {
        return getStoredObject(
                storedImpId,
                this::readStoredImpContent,
                this::impFromString,
                modifier
        );
    }

    protected String getStoredImpAsString(String storedImpId) {
        return getStoredImpAsString(storedImpId, null);
    }

    protected String getStoredImpAsString(String storedImpId, Function<Imp, Imp> modifier) {
        return getStoredObjectAsString(
                storedImpId,
                this::readStoredImpContent,
                this::impFromString,
                modifier
        );
    }

    protected String readStoredRequestContent(String storedRequestId) {
        return readJsonResourceByRelativePath("requests/" + storedRequestId);
    }

    protected BidRequest getStoredRequest(String storedRequestId) {
        return getStoredRequest(storedRequestId, null);
    }

    protected BidRequest getStoredRequest(String storedRequestId, Function<BidRequest, BidRequest> modifier) {
        return getStoredObject(
                storedRequestId,
                this::readStoredRequestContent,
                this::bidRequestFromString,
                modifier
        );
    }

    protected String getStoredRequestAsString(String storedRequestId) {
        return getStoredRequestAsString(
                storedRequestId,
                null
        );
    }

    protected String getStoredRequestAsString(String storedRequestId, Function<BidRequest, BidRequest> modifier) {
        return getStoredObjectAsString(
                storedRequestId,
                this::readStoredRequestContent,
                this::bidRequestFromString,
                modifier
        );
    }

    protected BidRequest bidRequestFromString(String content) {
        if (content != null) {
            return jacksonMapper.decodeValue(content, BidRequest.class);
        }
        return null;
    }

    protected Imp impFromString(String content) {
        if (content != null) {
            return jacksonMapper.decodeValue(content, Imp.class);
        }
        return null;
    }

    protected <T> T getStoredObject(
            String objectId, Function<String, String> contentRetriever,
            Function<String, T> typeConverter, Function<T, T> modifier
    ) {
        String content = contentRetriever.apply(objectId);
        if (StringUtils.isBlank(content)) {
            return null;
        }

        T storedObject = typeConverter.apply(content);
        if (storedObject == null) {
            return null;
        }

        if (modifier != null) {
            return modifier.apply(storedObject);
        }
        return storedObject;
    }

    protected <T> String getStoredObjectAsString(
            String objectId, Function<String, String> contentRetriever,
            Function<String, T> typeConverter, Function<T, T> modifier
    ) {
        if (modifier == null) {
            return contentRetriever.apply(objectId);
        }

        T storedObject = getStoredObject(objectId, contentRetriever, typeConverter, modifier);

        try {
            return mapper.writeValueAsString(storedObject);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected <PAYLOAD, Context extends InvocationContext> void executeHookAndValidateAsyncResult(
            Hook<PAYLOAD, Context> hook,
            PAYLOAD initialPayload,
            Context context,
            BiConsumer<PAYLOAD/*initialPayload*/,
                    AsyncResult<InvocationResult<PAYLOAD>>/*asyncResult*/> validator
    ) {
        Future<InvocationResult<PAYLOAD>> result = hook
                .call(
                        initialPayload,
                        context
                );
        Assertions.assertThat(result).isNotNull();
        result.onComplete(asyncResult -> validator.accept(
                initialPayload,
                asyncResult
            )
        );
    }

    protected <PAYLOAD, Context extends InvocationContext> void executeHookAndValidateInvocationResult(
            Hook<PAYLOAD, Context> hook,
            PAYLOAD payload,
            Context context,
            BiConsumer<
                    PAYLOAD/*initialPayload*/,
                    InvocationResult<PAYLOAD>/*invocationResult*/> validator
    ) {
        executeHookAndValidateAsyncResult(
                hook,
                payload,
                context,
                (initialPayload, asyncResult) -> {
                    Assertions.assertThat(asyncResult.succeeded()).isTrue();
                    InvocationResult<PAYLOAD> invocationResult = asyncResult.result();
                    Assertions.assertThat(invocationResult).isNotNull();
                    validator.accept(initialPayload, invocationResult);
                }
        );
    }

    protected <PAYLOAD, Context extends InvocationContext> void executeHookAndValidatePayloadUpdate(
            Hook<PAYLOAD, Context> hook,
            PAYLOAD payload,
            Context context,
            BiConsumer<
                    PAYLOAD/*initialPayload*/,
                    PAYLOAD/*updatedPayload*/> validator
    ) {
        executeHookAndValidateInvocationResult(
                hook,
                payload,
                context,
                (initialPayload, invocationResult) -> {
                    Assertions.assertThat(invocationResult.status())
                            .isEqualTo(InvocationStatus.success);
                    Assertions.assertThat(invocationResult.action())
                            .isEqualTo(InvocationAction.update);
                    PayloadUpdate<PAYLOAD> payloadUpdate = invocationResult.payloadUpdate();
                    Assertions.assertThat(payloadUpdate)
                            .isNotNull();
                    PAYLOAD updatedPayload = payloadUpdate.apply(initialPayload);
                    Assertions.assertThat(updatedPayload)
                            .isNotNull();
                    validator.accept(initialPayload, updatedPayload);
                }
        );
    }

    protected <PAYLOAD, Context extends InvocationContext> void executeHookAndValidateRejectedInvocationResult(
            Hook<PAYLOAD, Context> hook,
            PAYLOAD payload,
            Context context,
            BiConsumer<
                    PAYLOAD/*initialPayload*/,
                    InvocationResult<PAYLOAD>/*invocationResult*/> validator
    ) {
        executeHookAndValidateInvocationResult(
                hook,
                payload,
                context,
                (initialPayload, invocationResult) -> {
                    Assertions.assertThat(invocationResult.status())
                            .isEqualTo(InvocationStatus.success);
                    Assertions.assertThat(invocationResult.action())
                            .isEqualTo(InvocationAction.reject);
                    Assertions.assertThat(invocationResult.errors())
                            .isNotEmpty();
                    validator.accept(initialPayload, invocationResult);
                }
        );
    }

    protected boolean hasLogEventWith(LogCaptor logCaptor, String message) {
        return logCaptor.getLogEvents()
                .stream()
                .anyMatch(
                        logEvent -> StringUtils.contains(
                                logEvent.toString(),
                                message
                        )
                );
    }

    protected boolean hasLogEventWith(LogCaptor logCaptor, String message, Level level) {
        return logCaptor.getLogEvents()
                .stream()
                .anyMatch(
                        logEvent -> logEvent.getLevel().equals(level.toString())
                                && StringUtils.contains(
                                logEvent.toString(),
                                message
                        )
                );
    }

    protected BidRequest setStoredImpIds(
            BidRequest request, Map<String /*impId*/, String /*storedImpId*/> impIdToStoredIdMap
    ) {
        request.getImp().replaceAll(imp -> {
            if (impIdToStoredIdMap.containsKey(imp.getId())) {
                final String storedImpId = impIdToStoredIdMap.get(imp.getId());
                ExtImp extImp = ExtImp.of(ExtImpPrebid.builder()
                        .storedrequest(ExtStoredRequest.of(storedImpId))
                        .build(), null);
                ObjectNode extNode = mapper.valueToTree(extImp);
                return imp.toBuilder().ext(
                        jsonUtils.nonDestructiveMerge(imp.getExt(), extNode)
                ).build();
            }
            return imp;
        });
        return request;
    }

    protected Imp setImpConfigProperties(Imp imp, Consumer<ObjectNode> configSetter) {
        final ObjectNode impExt = jacksonMapper.mapper().valueToTree(
                ExtImp.of(ExtImpPrebid.builder().build(), null)
        );

        ObjectNode configNode = ((ObjectNode) impExt.at("/prebid"))
                .putObject("improvedigitalpbs");

        configSetter.accept(configNode);

        return imp.toBuilder()
                .ext(jsonUtils.nonDestructiveMerge(imp.getExt(), impExt))
                .build();
    }

    protected Imp setImpBidderProperties(Imp imp, String bidderName, Consumer<ObjectNode> setter) {
        final ObjectNode impExt = jacksonMapper.mapper().valueToTree(
                ExtImp.of(ExtImpPrebid.builder().build(), null)
        );
        ObjectNode bidderNode = ((ObjectNode) impExt.at("/prebid"))
                .putObject("bidder")
                .putObject(bidderName);

        setter.accept(bidderNode);

        return imp.toBuilder().ext(
                jsonUtils.nonDestructiveMerge(imp.getExt(), impExt)
        ).build();
    }
}
