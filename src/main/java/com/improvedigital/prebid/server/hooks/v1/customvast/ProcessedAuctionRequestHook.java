package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
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
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
                final BidRequest bidRequest = validateRequestForImprovePlacement(originalBidRequest, account);
                if (moduleContext == null) {
                    return customVastUtils.resolveCountryAndCreateModuleContext(bidRequest, timeout)
                            .map(context -> InvocationResultImpl.succeeded(
                                    payload -> AuctionRequestPayloadImpl.of(context.getBidRequest()), context
                            ));
                }
                return Future.succeededFuture(
                        InvocationResultImpl.succeeded(
                                payload -> AuctionRequestPayloadImpl.of(bidRequest), moduleContext
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

    public BidRequest validateRequestForImprovePlacement(
            BidRequest bidRequest, Account account
    ) throws CustomVastHooksException {
        boolean isImprovePlacementRequired = Optional.ofNullable(account)
                .map(jsonUtils::getAccountExt)
                .map(ImprovedigitalPbsAccountExt::getRequireImprovePlacement)
                .orElse(true);
        if (!isImprovePlacementRequired) {
            return bidRequest;
        }
        List<Imp> impsWithImprovePlacementId = bidRequest.getImp()
                .stream()
                .filter(imp -> requestUtils.getImprovedigitalPlacementId(imp) != null)
                .collect(Collectors.toList());
        if (impsWithImprovePlacementId.isEmpty()) {
            throw new CustomVastHooksException(
                    "improvedigital placementId is not defined in any of the imp(s)"
            );
        }
        return bidRequest.toBuilder()
                .imp(impsWithImprovePlacementId)
                .build();
    }

    @Override
    public String code() {
        return "improvedigital-custom-vast-hooks-processed-auction-request";
    }
}
