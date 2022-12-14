package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExtGam;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.settings.model.ImprovedigitalPbsAccountExt;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.LogMessage;
import com.improvedigital.prebid.server.utils.LogUtils;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);
    private final RequestUtils requestUtils;
    private final CustomVastUtils customVastUtils;
    private final SettingsLoader settingsLoader;
    private final JsonUtils jsonUtils;

    public ProcessedAuctionRequestHook(
            SettingsLoader settingsLoader,
            RequestUtils requestUtils,
            CustomVastUtils customVastUtils
    ) {
        this.settingsLoader = Objects.requireNonNull(settingsLoader);
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.jsonUtils = this.requestUtils.getJsonUtils();
        this.customVastUtils = Objects.requireNonNull(customVastUtils);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest originalBidRequest = auctionRequestPayload.bidRequest();
        final Object moduleContext = invocationContext.moduleContext();
        final Timeout timeout = invocationContext.timeout();
        return settingsLoader.getAccountFuture(originalBidRequest, timeout).otherwiseEmpty().compose(account -> {
            try {
                validateRequestForImprovePlacement(originalBidRequest, account);
                if (moduleContext == null) {
                    return customVastUtils.resolveCountryAndCreateModuleContext(originalBidRequest, timeout)
                            .map(context -> InvocationResultImpl.succeeded(
                                    payload -> AuctionRequestPayloadImpl.of(context.getBidRequest()), context
                            ));
                }
                return Future.succeededFuture(
                        InvocationResultImpl.succeeded(
                                payload -> AuctionRequestPayloadImpl.of(originalBidRequest), moduleContext
                        )
                );
            } catch (Throwable t) {
                LogMessage logMessage = LogMessage.from(originalBidRequest);
                if (t instanceof CustomVastHooksException) {
                    LogUtils.log(
                            logMessage.withLogCounterKey(logMessage.toString())
                                    .with(t)
                                    .withFrequency(1000),
                            logger::error
                    );
                } else {
                    logger.error(logMessage.with(t));
                }
                return Future.succeededFuture(InvocationResultImpl.rejected(t.getMessage()));
            }
        });
    }

    public void validateRequestForImprovePlacement(
            BidRequest bidRequest, Account account
    ) throws CustomVastHooksException {
        boolean isImprovePlacementRequired = Optional.ofNullable(account)
                .map(jsonUtils::getAccountExt)
                .map(ImprovedigitalPbsAccountExt::getRequireImprovePlacement)
                .orElse(true);

        final List<String> errors = new ArrayList<>();
        final String errorPrefix = "request.imp[%d] must be configured with improvedigital placementId";
        for (int i = 0; i < bidRequest.getImp().size(); i++) {
            final Imp imp = bidRequest.getImp().get(i);
            final Integer improvePlacementId = requestUtils.getImprovedigitalPlacementId(imp);
            if (improvePlacementId != null) {
                continue;
            }
            final ImprovedigitalPbsImpExt impExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
            if (!isImprovePlacementRequired) {
                if (!requestUtils.isCustomVastVideo(imp, impExt, VastResponseType.gvast)) {
                    continue;
                }

                final String gamAdUnit = ObjectUtils.defaultIfNull(
                        impExt.getImprovedigitalPbsImpExtGam(), ImprovedigitalPbsImpExtGam.DEFAULT
                ).getAdUnit();

                if (StringUtils.isBlank(gamAdUnit)) {
                    errors.add(
                            (errorPrefix + " or gam adUnit for gvast response").formatted(i)
                    );
                }
            } else {
                errors.add(
                        errorPrefix.formatted(i)
                );
            }
        }

        if (CollectionUtils.isNotEmpty(errors)) {
            throw new CustomVastHooksException(String.join("\n", errors));
        }
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-processed-auction-request";
    }
}
