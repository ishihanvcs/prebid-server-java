package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;

import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

    private static final Logger logger = LoggerFactory.getLogger(EntrypointHook.class);
    private final SettingsLoader settingsLoader;
    private final JsonUtils jsonUtils;
    private final ObjectMapper mapper;
    private final RequestUtils requestUtils;
    private final JsonMerger merger;

    public EntrypointHook(
            SettingsLoader settingsLoader,
            JsonUtils jsonUtils,
            RequestUtils requestUtils,
            JsonMerger merger
    ) {
        this.settingsLoader = settingsLoader;
        this.jsonUtils = jsonUtils;
        this.mapper = jsonUtils.getObjectMapper();
        this.requestUtils = requestUtils;
        this.merger = merger;
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext) {

        final BidRequest originalBidRequest = jsonUtils.parseBidRequest(entrypointPayload.body());

        final boolean hasAccountId = StringUtils.isNotBlank(requestUtils.getAccountId(originalBidRequest));
        final boolean hasStoredRequest = StringUtils.isNotBlank(
                requestUtils.getStoredRequestId(originalBidRequest.getExt())
        );

        if (!hasAccountId || !hasStoredRequest) {
            final Map<Imp, String> impToStoredRequestId = originalBidRequest.getImp().stream()
                    .map(this::getImpToStoredRequestIdTuple)
                    .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

            return settingsLoader.getStoredImps(
                    new HashSet<>(impToStoredRequestId.values()), invocationContext.timeout())
                    .compose(storedImps -> {
                        BidRequest updatedBidRequest = originalBidRequest;
                        String accountId = null;
                        String requestId = null;
                        for (final Imp imp : originalBidRequest.getImp()) {
                            final String storedRequestId = impToStoredRequestId.get(imp);
                            final Imp storedImp = storedRequestId != null ? storedImps.get(storedRequestId) : null;
                            final ImprovedigitalPbsImpExt pbsImpExt = mergeImprovedigitalPbsImpExt(imp, storedImp);
                            if (pbsImpExt != null) {
                                if (pbsImpExt.getAccountId() != null) {
                                    if (accountId == null) {
                                        accountId = pbsImpExt.getAccountId();
                                    } else if (!accountId.equals(pbsImpExt.getAccountId())) {
                                        return Future.succeededFuture(
                                                InvocationResultImpl.rejected(
                                                        "accountId mismatched in imp[].prebid.improvedigitalpbs"
                                                )
                                        );
                                    }
                                }

                                if (pbsImpExt.getRequestId() != null) {
                                    if (requestId == null) {
                                        requestId = pbsImpExt.getRequestId();
                                    } else if (!requestId.equals(pbsImpExt.getRequestId())) {
                                        return Future.succeededFuture(
                                                InvocationResultImpl.rejected(
                                                        "requestId mismatched in imp[].prebid.improvedigitalpbs"
                                                )
                                        );
                                    }
                                }
                            }
                        }

                        if (!hasAccountId) {
                            updatedBidRequest = setAccountId(updatedBidRequest, accountId);
                        }

                        if (!hasStoredRequest) {
                            updatedBidRequest = setRequestId(updatedBidRequest, requestId);
                        }

                        try {
                            final String updatedBody = mapper.writeValueAsString(updatedBidRequest);
                            return Future.succeededFuture(InvocationResultImpl.succeeded(
                                    payload -> EntrypointPayloadImpl.of(
                                            entrypointPayload.queryParams(),
                                            entrypointPayload.headers(),
                                            updatedBody
                                    )));
                        } catch (JsonProcessingException e) {
                            throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
                        }
                    }, t -> Future.succeededFuture(InvocationResultImpl.rejected(t.getMessage())));
        }

        return Future.succeededFuture(
                InvocationResultImpl.succeeded(
                    payload -> entrypointPayload
                )
        );

    }

    private ImprovedigitalPbsImpExt mergeImprovedigitalPbsImpExt(Imp imp, Imp storedImp) {
        ImprovedigitalPbsImpExt ext1 = jsonUtils.getImprovedigitalPbsImpExt(imp);
        ImprovedigitalPbsImpExt ext2 = jsonUtils.getImprovedigitalPbsImpExt(storedImp);

        return merger.merge(ext1, ext2, ImprovedigitalPbsImpExt.class);
    }

    private Tuple2<Imp, String> getImpToStoredRequestIdTuple(Imp imp) {
        String storedImpId = null;
        if (imp != null && imp.getExt().isObject()) {
            try {
                ExtImp extImp = mapper.treeToValue(imp.getExt(), ExtImp.class);
                storedImpId = requestUtils.getStoredImpId(extImp);
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException(String.format("Error decoding imp.ext: %s", e.getMessage()));
            }
        }
        return Tuple2.of(imp, storedImpId);
    }

    private BidRequest setAccountId(BidRequest bidRequest, String accountId) {
        if (accountId != null) {
            BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
            if (bidRequest.getSite() != null) {
                requestBuilder.site(
                        bidRequest.getSite().toBuilder().publisher(
                                ObjectUtils.defaultIfNull(bidRequest.getSite()
                                                .getPublisher(), Publisher.builder().build())
                                .toBuilder().id(accountId).build()
                        ).build()
                );
            } else {
                requestBuilder.app(
                        bidRequest.getApp().toBuilder().publisher(
                                ObjectUtils.defaultIfNull(bidRequest.getApp()
                                                .getPublisher(), Publisher.builder().build())
                                        .toBuilder().id(accountId).build()
                        ).build()
                ).build();
            }
            return requestBuilder.build();
        }
        throw new InvalidRequestException("accountId not defined in bidRequest or any of the imps");
    }

    private BidRequest setRequestId(BidRequest bidRequest, String requestId) {
        if (requestId == null) {
            return bidRequest;
        }

        return bidRequest.toBuilder()
                .ext(ExtRequest.of(
                    ObjectUtils.defaultIfNull(
                            bidRequest.getExt().getPrebid(),
                            ExtRequestPrebid.builder().build()
                    ).toBuilder()
                    .storedrequest(ExtStoredRequest.of(requestId))
                    .build()
                ))
        .build();
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-entrypoint";
    }
}
