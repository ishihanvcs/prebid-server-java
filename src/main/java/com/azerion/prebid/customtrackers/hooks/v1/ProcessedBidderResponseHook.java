package com.azerion.prebid.customtrackers.hooks.v1;

import com.azerion.prebid.customtrackers.BidderBidModifier;
import com.azerion.prebid.customtrackers.ModuleContext;
import com.azerion.prebid.hooks.v1.InvocationResultImpl;
import com.azerion.prebid.settings.SettingsLoader;
import com.azerion.prebid.settings.model.CustomTrackerSetting;
import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.bidder.BidderResponsePayloadImpl;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;

import java.util.List;
import java.util.stream.Collectors;

public class ProcessedBidderResponseHook implements org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook {

    // private static final Logger logger = LoggerFactory.getLogger(ProcessedBidderResponseHook.class);
    private final BidderBidModifier bidderBidModifier;
    private final SettingsLoader settingsLoader;

    public ProcessedBidderResponseHook(
            SettingsLoader settingsLoader,
            BidderBidModifier bidderBidModifier
    ) {
        this.settingsLoader = settingsLoader;
        this.bidderBidModifier = bidderBidModifier;
    }

    @Override
    public Future<InvocationResult<BidderResponsePayload>> call(
            BidderResponsePayload bidderResponsePayload,
            BidderInvocationContext invocationContext) {

        final List<BidderBid> originalBids = bidderResponsePayload.bids();
        final String bidder = invocationContext.bidder();
        final Object moduleContext = invocationContext.moduleContext();
        return settingsLoader.getCustomTrackerSettingFuture()
                .compose(
                        // successMapper
                        customTrackerSetting -> {
                            final List<BidderBid> updatedBids = updateBids(
                                    customTrackerSetting, moduleContext, originalBids, bidder
                            );
                            return Future.succeededFuture(
                                    InvocationResultImpl.succeeded(payload ->
                                            BidderResponsePayloadImpl.of(updatedBids), moduleContext));
                        },
                        // failureMapper
                        throwable -> Future.succeededFuture(
                                InvocationResultImpl.succeeded(payload ->
                                        BidderResponsePayloadImpl.of(originalBids), moduleContext))
                );
    }

    private List<BidderBid> updateBids(
            CustomTrackerSetting customTrackerSetting,
            Object moduleContext,
            List<BidderBid> originalBids,
            String bidder
    ) {
        if (!(moduleContext instanceof ModuleContext)) {
            return originalBids;
        }
        return originalBids.stream()
                .map(bidderBid -> bidderBidModifier.modifyBidAdm(
                                customTrackerSetting,
                                (ModuleContext) moduleContext,
                                bidderBid, bidder
                        )
                )
                .collect(Collectors.toList());
    }

    @Override
    public String code() {
        return "custom-tracker-processed-bidder-response";
    }
}
