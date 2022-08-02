package com.improvedigital.prebid.server;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
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

public abstract class UnitTestBase extends VertxTest {

    protected static final String EXTERNAL_URL = "https://pbs-proto.360polaris.biz";
    protected static final String GAM_NETWORK_CODE = "1015413";
    protected static final String PROTO_CACHE_HOST = "euw-pbc-proto.360polaris.biz";
    protected static final String PROTO_CACHE_URL = String.format("https://%s/cache", PROTO_CACHE_HOST);
    protected static final String PRODUCTION_CACHE_HOST = "euw-pbc.360yield.com";
    protected static final String PRODUCTION_CACHE_URL = String.format("https://%s/cache", PRODUCTION_CACHE_HOST);

    protected static JsonMerger merger;
    protected static JsonUtils jsonUtils;
    protected static RequestUtils requestUtils;
    protected static XmlMapper xmlMapper;
    protected static MacroProcessor macroProcessor;

    static {
        merger = new JsonMerger(jacksonMapper);
        jsonUtils = new JsonUtils(jacksonMapper);
        requestUtils = new RequestUtils(jsonUtils);
        xmlMapper = new XmlMapper();
        macroProcessor = new MacroProcessor();
    }

    protected String resourceDir = null;
    protected String impsDir = "imps";
    protected String requestsDir = "requests";
    protected String responsesDir = "responses";

    protected final String defaultRequestId = "minimal";
    protected final String defaultResponseId = "video-vast";
    protected final String defaultStoredImpId = "video-gvast";
    protected final String defaultStoredRequestId = "stored-request";

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
        String resourceDir = normalizeDir(
                StringUtils.defaultIfBlank(
                    this.resourceDir,
                    UnitTestBase.class.getPackageName().replaceAll("\\.", "/") + "/shared"
                )
        );
        final String resourcePath = resourceDir + StringUtils.removeStart(relativePath, "/");
        return readResourceContent(resourcePath);
    }

    protected String readJsonResource(String jsonPath) {
        return readResourceContent(jsonPath + ".json");
    }

    protected String readJsonResourceByRelativePath(String relativePath) {
        return readResourceContentByRelativePath(relativePath + ".json");
    }

    private String normalizeDir(String dirPath) {
        final String separator = "/";
        return StringUtils.stripToEmpty(
                StringUtils.removeEnd(dirPath, separator)
        ) + separator;
    }

    protected String readStoredImpContent(String storedImpId) {
        return readJsonResourceByRelativePath(normalizeDir(impsDir) + storedImpId);
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

    protected Imp impFromString(String content) {
        if (content != null) {
            return jacksonMapper.decodeValue(content, Imp.class);
        }
        return null;
    }

    protected String readStoredRequestContent(String storedRequestId) {
        return readJsonResourceByRelativePath(normalizeDir(requestsDir) + storedRequestId);
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

    protected String readStoredResponseContent(String storedResponseId) {
        return readJsonResourceByRelativePath(normalizeDir(responsesDir) + storedResponseId);
    }

    protected BidResponse getStoredResponse(String storedResponseId) {
        return getStoredResponse(storedResponseId, null);
    }

    protected BidResponse getStoredResponse(String storedResponseId, Function<BidResponse, BidResponse> modifier) {
        return getStoredObject(
                storedResponseId,
                this::readStoredResponseContent,
                this::bidResponseFromString,
                modifier
        );
    }

    protected String getStoredResponseAsString(String storedResponseId) {
        return getStoredResponseAsString(
                storedResponseId,
                null
        );
    }

    protected String getStoredResponseAsString(String storedResponseId, Function<BidResponse, BidResponse> modifier) {
        return getStoredObjectAsString(
                storedResponseId,
                this::readStoredResponseContent,
                this::bidResponseFromString,
                modifier
        );
    }

    protected BidResponse bidResponseFromString(String content) {
        if (content != null) {
            return jacksonMapper.decodeValue(content, BidResponse.class);
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

    protected <T> T deepCopy(T source, Class<T> clazz) {
        return jacksonMapper.decodeValue(
            jacksonMapper.encodeToBytes(source), clazz
        );
    }

    protected <T> T parseXml(String xml, Class<T> clazz) {
        try {
            return xmlMapper.readValue(xml, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
