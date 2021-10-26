package com.azerion.prebid.customtrackers;

import com.azerion.prebid.auction.requestfactory.GVastContext;
import com.azerion.prebid.settings.model.Placement;
import com.iab.openrtb.request.BidRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

@SuperBuilder(toBuilder = true)
@Getter
public class BidRequestContext {

    @NonNull
    ApplicationContext applicationContext;
    @NonNull
    BidRequest bidRequest;
    Placement placement;
    Account account;
    HttpRequestContext httpRequest;
    UidsCookie uidsCookie;

    public static BidRequestContext from(
            ApplicationContext applicationContext,
            BidRequest bidRequest) {
        return BidRequestContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(bidRequest)
                .build();
    }

    public static BidRequestContext from(
            ApplicationContext applicationContext,
            CustomTrackerModuleContext moduleContext) {
        return BidRequestContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(moduleContext.getBidRequest())
                .placement(moduleContext.getPlacement())
                .build();
    }

    public static BidRequestContext from(
            ApplicationContext applicationContext,
            BidRequest bidRequest,
            Account account,
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie
    ) {
        return BidRequestContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(bidRequest)
                .account(account)
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookie)
                .build();
    }

    public static BidRequestContext from(
            ApplicationContext applicationContext,
            AuctionContext auctionContext
    ) {
        return BidRequestContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(auctionContext.getBidRequest())
                .account(auctionContext.getAccount())
                .httpRequest(auctionContext.getHttpRequest())
                .uidsCookie(auctionContext.getUidsCookie())
                .build();
    }

    public static BidRequestContext from(
            ApplicationContext applicationContext,
            GVastContext gVastContext
    ) {
        return BidRequestContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(gVastContext.getBidRequest())
                .account(gVastContext.getAccount())
                .placement(gVastContext.getPlacement())
                .httpRequest(gVastContext.getAuctionContext().getHttpRequest())
                .uidsCookie(gVastContext.getAuctionContext().getUidsCookie())
                .build();
    }

    public BidRequestContext with(
            Placement placement
    ) {
        return this.toBuilder()
                .placement(placement)
                .build();
    }

    public BidRequestContext with(
            Account account,
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie
    ) {
        return this.toBuilder()
                .account(account)
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookie)
                .build();
    }
}
