package com.improvedigital.prebid.server.hooks.v1;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import nl.altindag.log.LogCaptor;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class HooksTestBase {

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final JacksonMapper mapper = new JacksonMapper(objectMapper);
    protected final JsonUtils jsonUtils = new JsonUtils(mapper);
    protected final RequestUtils requestUtils = new RequestUtils(jsonUtils);
    protected final JsonMerger merger = new JsonMerger(mapper);

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
            return mapper.decodeValue(content, BidRequest.class);
        }
        return null;
    }

    protected Imp impFromString(String content) {
        if (content != null) {
            return mapper.decodeValue(content, Imp.class);
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
            return objectMapper.writeValueAsString(storedObject);
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
        Assert.assertNotNull(result);
        result.onComplete(asyncResult -> {
            validator.accept(initialPayload, asyncResult);
        });
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
                    Assert.assertTrue(asyncResult.succeeded());
                    InvocationResult<PAYLOAD> invocationResult = asyncResult.result();
                    Assert.assertNotNull(invocationResult);
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
                    Assert.assertEquals(invocationResult.status(), InvocationStatus.success);
                    Assert.assertEquals(invocationResult.action(), InvocationAction.update);
                    PayloadUpdate<PAYLOAD> payloadUpdate = invocationResult.payloadUpdate();
                    Assert.assertNotNull(payloadUpdate);
                    PAYLOAD updatedPayload = payloadUpdate.apply(initialPayload);
                    Assert.assertNotNull(updatedPayload);
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
                    Assert.assertEquals(invocationResult.status(), InvocationStatus.success);
                    Assert.assertEquals(invocationResult.action(), InvocationAction.reject);
                    Assert.assertFalse(invocationResult.errors().isEmpty());
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
}
