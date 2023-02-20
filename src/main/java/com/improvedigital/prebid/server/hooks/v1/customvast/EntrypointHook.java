package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.User;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.LogMessage;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;

import java.net.HttpCookie;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class EntrypointHook implements org.prebid.server.hooks.v1.entrypoint.EntrypointHook {

    private static final Logger logger = LoggerFactory.getLogger(EntrypointHook.class);
    private final SettingsLoader settingsLoader;
    private final JsonUtils jsonUtils;
    private final JacksonMapper mapper;
    private final RequestUtils requestUtils;
    private final JsonMerger merger;

    public EntrypointHook(
            SettingsLoader settingsLoader,
            RequestUtils requestUtils,
            JsonMerger merger
    ) {
        this.settingsLoader = Objects.requireNonNull(settingsLoader);
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.merger = Objects.requireNonNull(merger);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
        this.mapper = Objects.requireNonNull(jsonUtils.getMapper());
    }

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext
    ) {
        final BidRequest originalBidRequest = jsonUtils.parseBidRequest(entrypointPayload.body());
        final HooksModuleContext moduleContext = HooksModuleContext.from(entrypointPayload.headers().get("cookie"));

        return enrichBidRequest(
                originalBidRequest,
                moduleContext,
                invocationContext.timeout()
        ).map(bidRequest -> {
            final EntrypointPayload updatedPayload;
            if (originalBidRequest != bidRequest) {
                final String updatedBody = mapper.encodeToString(bidRequest);
                updatedPayload = EntrypointPayloadImpl.of(
                        entrypointPayload.queryParams(),
                        entrypointPayload.headers(),
                        updatedBody
                );
            } else {
                updatedPayload = entrypointPayload;
            }
            return InvocationResultImpl.succeeded(
                    payload -> updatedPayload, moduleContext
            );
        });
    }

    private Future<BidRequest> enrichBidRequest(
            BidRequest originalBidRequest, HooksModuleContext moduleContext, Timeout timeout
    ) {
        final BidRequest bidRequest = copyImproveUserIdFromCookieIfAvailable(
                originalBidRequest, moduleContext.getCookieHeader()
        );

        final Future<BidRequest> defaultReturn = Future.succeededFuture(bidRequest);

        final boolean hasAccountId = StringUtils.isNotBlank(
                requestUtils.getParentAccountId(bidRequest)
        );

        final boolean hasStoredRequest = StringUtils.isNotBlank(
                requestUtils.getStoredRequestId(bidRequest)
        );

        if (!hasAccountId || !hasStoredRequest) {
            final Map<Imp, String> impToStoredRequestId = bidRequest.getImp().stream()
                    .map(imp -> Tuple2.of(imp, requestUtils.getStoredImpId(imp)))
                    .filter(t -> StringUtils.isNotBlank(t.getRight()))
                    .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight));

            return settingsLoader
                    .getStoredImpsSafely(
                            new HashSet<>(impToStoredRequestId.values()), timeout
                    ).map(storedImps -> {
                        BidRequest updatedBidRequest = bidRequest;
                        String accountId = null;
                        String requestId = null;
                        boolean hasMismatchedAccount = false;
                        boolean hasMismatchedRequest = false;

                        for (final Imp imp : bidRequest.getImp()) {
                            final String storedRequestId = impToStoredRequestId.get(imp);
                            final Imp storedImp = storedRequestId != null
                                    ? storedImps.get(storedRequestId)
                                    : null;
                            final ImprovedigitalPbsImpExt pbsImpExt
                                    = mergeImprovedigitalPbsImpExt(imp, storedImp);
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
                                    LogMessage.from(bidRequest).withMessage(
                                            "accountId mismatched in imp[].prebid.improvedigitalpbs"
                                    )
                            );
                        }

                        if (!hasMismatchedRequest) {
                            updatedBidRequest = setRequestId(updatedBidRequest, requestId);
                        } else {
                            logger.warn(
                                    LogMessage.from(bidRequest)
                                            .withMessage(
                                                    "requestId mismatched in imp[].prebid.improvedigitalpbs"
                                            )
                            );
                        }
                        return updatedBidRequest;

                    }).recover(t -> {
                        logger.error(
                                LogMessage.from(bidRequest)
                                        .with(t)
                        );
                        return defaultReturn;
                    });
        }
        return defaultReturn;
    }

    private BidRequest copyImproveUserIdFromCookieIfAvailable(
            BidRequest bidRequest, String cookieHeader
    ) {
        try {
            List<HttpCookie> cookies = HttpCookie.parse(cookieHeader);
            HttpCookie tuuidCookie = cookies.stream()
                    .filter(cookie -> cookie.getName().equals("tuuid"))
                    .findAny().orElse(null);
            if (tuuidCookie == null || StringUtils.isBlank(tuuidCookie.getValue())) {
                return bidRequest;
            }
            final User mergedUser = merger.merge(
                    bidRequest.getUser(),
                    User.builder()
                            .ext(ExtUser.builder()
                                    .prebid(ExtUserPrebid.of(Map.of(
                                                    RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME,
                                                    tuuidCookie.getValue()
                                            ))
                                    ).build()
                            ).build(),
                    User.class
            );

            bidRequest = bidRequest.toBuilder()
                    .user(mergedUser)
                    .build();
        } catch (Exception ignored) { } // cookie parsing error should not disrupt other logic
        return bidRequest;
    }

    private ImprovedigitalPbsImpExt mergeImprovedigitalPbsImpExt(Imp imp, Imp storedImp) {
        ImprovedigitalPbsImpExt ext1 = jsonUtils.getImprovedigitalPbsImpExt(imp);
        ImprovedigitalPbsImpExt ext2 = jsonUtils.getImprovedigitalPbsImpExt(storedImp);
        return merger.merge(ext1, ext2, ImprovedigitalPbsImpExt.class);
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
                                    bidRequest.getSite().getPublisher(),
                                    publisherWithParentAccount,
                                    Publisher.class
                            )
                    ).build()
            );
        } else {
            requestBuilder.app(
                    bidRequest.getApp().toBuilder().publisher(
                            merger.merge(
                                    bidRequest.getApp().getPublisher(),
                                    publisherWithParentAccount,
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
                        merger.merge(
                                extRequest,
                                bidRequest.getExt(),
                                ExtRequest.class
                        )
                )
                .build();
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-entrypoint";
    }
}
