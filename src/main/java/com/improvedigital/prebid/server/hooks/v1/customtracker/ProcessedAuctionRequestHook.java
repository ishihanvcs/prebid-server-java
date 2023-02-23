package com.improvedigital.prebid.server.hooks.v1.customtracker;

import com.iab.openrtb.request.BidRequest;
import com.improvedigital.prebid.server.customtracker.model.AuctionRequestModuleContext;
import com.improvedigital.prebid.server.hooks.v1.InvocationResultImpl;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import io.vertx.core.Future;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

public class ProcessedAuctionRequestHook implements org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook {

    private final SettingsLoader settingsLoader;

    public ProcessedAuctionRequestHook(SettingsLoader settingsLoader) {
        this.settingsLoader = settingsLoader;
    }

    // private static final Logger logger = LoggerFactory.getLogger(ProcessedAuctionRequestHook.class);

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload, AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof AuctionRequestModuleContext) {
            return settingsLoader.getAccountFuture(bidRequest, invocationContext.timeout())
                    .map(account -> {
                        AuctionRequestModuleContext updatedContext = ((AuctionRequestModuleContext) moduleContext)
                                .with(bidRequest)
                                .with(account);
                        return InvocationResultImpl.succeeded(
                                payload -> auctionRequestPayload, updatedContext
                        );
                    });
        }
        return Future.succeededFuture(InvocationResultImpl.succeeded(
                payload -> auctionRequestPayload, moduleContext
        ));
    }

    @Override
    public String code() {
        return "improvedigital-custom-tracker-hooks-processed-auction-request";
    }
}

