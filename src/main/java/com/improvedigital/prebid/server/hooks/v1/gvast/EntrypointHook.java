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
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
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

        final boolean hasAccountId = StringUtils.isNotBlank(
                requestUtils.getParentAccountId(originalBidRequest)
        );

        final boolean hasStoredRequest = StringUtils.isNotBlank(
                requestUtils.getStoredRequestId(originalBidRequest.getExt())
        );

        if (!hasAccountId || !hasStoredRequest) {
            final Map<Imp, String> impToStoredRequestId = originalBidRequest.getImp().stream()
                    .map(this::getImpToStoredRequestIdTuple)
                    .filter(t -> StringUtils.isNotBlank(t.getRight()))
                    .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

            // let core logic for auction handle errors in later phase of hook execution
            return settingsLoader.getStoredImpsSafely(
                    new HashSet<>(impToStoredRequestId.values()), invocationContext.timeout())
                    .compose(storedImps -> {
                        BidRequest updatedBidRequest = originalBidRequest;
                        String accountId = null;
                        String requestId = null;
                        boolean hasMismatchedAccount = false;
                        boolean hasMismatchedRequest = false;

                        for (final Imp imp : originalBidRequest.getImp()) {
                            final String storedRequestId = impToStoredRequestId.get(imp);
                            final Imp storedImp = storedRequestId != null ? storedImps.get(storedRequestId) : null;
                            final ImprovedigitalPbsImpExt pbsImpExt = mergeImprovedigitalPbsImpExt(imp, storedImp);
                            if (pbsImpExt != null) {
                                if (!hasAccountId && pbsImpExt.getAccountId() != null) {
                                    if (accountId == null) {
                                        accountId = pbsImpExt.getAccountId();
                                    } else if (!accountId.equals(pbsImpExt.getAccountId())) {
                                        hasMismatchedAccount = true;
                                    }
                                }

                                if (!hasStoredRequest && pbsImpExt.getRequestId() != null) {
                                    if (requestId == null) {
                                        requestId = pbsImpExt.getRequestId();
                                    } else if (!requestId.equals(pbsImpExt.getRequestId())) {
                                        hasMismatchedRequest = true;
                                    }
                                }
                            }
                        }

                        if (!hasMismatchedAccount) {
                            updatedBidRequest = setParentAccountId(updatedBidRequest, accountId);
                        } else {
                            logger.warn(
                                    entrypointPayload,
                                    new InvalidRequestException(
                                            "accountId mismatched in imp[].prebid.improvedigitalpbs"
                                    )
                            );
                        }

                        if (!hasMismatchedRequest) {
                            updatedBidRequest = setRequestId(updatedBidRequest, requestId);
                        } else {
                            logger.warn(
                                    entrypointPayload,
                                    new InvalidRequestException(
                                            "requestId mismatched in imp[].prebid.improvedigitalpbs"
                                    )
                            );
                        }

                        String updatedBody = entrypointPayload.body();
                        try {
                            updatedBody = mapper.writeValueAsString(updatedBidRequest);
                        } catch (JsonProcessingException e) {
                            logger.warn(entrypointPayload, e);
                        }
                        final String finalUpdatedBody = updatedBody;
                        return Future.succeededFuture(InvocationResultImpl.succeeded(
                                payload -> EntrypointPayloadImpl.of(
                                        entrypointPayload.queryParams(),
                                        entrypointPayload.headers(),
                                        finalUpdatedBody
                                )));
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

    private BidRequest setParentAccountId(BidRequest bidRequest, String accountId) {
        if (accountId == null) {
            return bidRequest;
        }
        BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        final Publisher publisherWithParentAccount = Publisher.builder().ext(
                ExtPublisher.of(
                        ExtPublisherPrebid.of(accountId)
                )
        ).build();
        if (bidRequest.getSite() != null) {
            requestBuilder.site(
                    bidRequest.getSite().toBuilder().publisher(
                            merger.merge(
                                    publisherWithParentAccount,
                                    bidRequest.getSite().getPublisher(),
                                    Publisher.class
                            )
                    ).build()
            );
        } else {
            requestBuilder.app(
                    bidRequest.getApp().toBuilder().publisher(
                            merger.merge(
                                    publisherWithParentAccount,
                                    bidRequest.getApp().getPublisher(),
                                    Publisher.class
                            )
                    ).build()
            ).build();
        }
        return requestBuilder.build();
    }

    private BidRequest setRequestId(BidRequest bidRequest, String requestId) {
        if (requestId == null) {
            return bidRequest;
        }

        ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of(requestId))
                        .build()
        );

        return bidRequest.toBuilder()
                .ext(
                        merger.merge(extRequest, bidRequest.getExt(), ExtRequest.class)
                )
        .build();
    }

    @Override
    public String code() {
        return "improvedigital-gvast-hooks-entrypoint";
    }
}
