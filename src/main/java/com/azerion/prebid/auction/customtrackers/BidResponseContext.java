package com.azerion.prebid.auction.customtrackers;

import com.azerion.prebid.auction.requestfactory.GVastContext;
import com.azerion.prebid.settings.model.Placement;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Objects;

@SuperBuilder(toBuilder = true)
@Getter
public class BidResponseContext {

    @NonNull
    ApplicationContext applicationContext;
    @NonNull
    BidRequest bidRequest;
    @NonNull
    BidResponse bidResponse;
    Account account;
    Placement placement;
    HttpRequestContext httpRequest;
    UidsCookie uidsCookie;

    public static BidResponseContext from(
            ApplicationContext applicationContext,
            BidRequest bidRequest,
            BidResponse bidResponse,
            Account account,
            HttpRequestContext httpRequest,
            UidsCookie uidsCookie
    ) {
        return BidResponseContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(bidRequest)
                .bidResponse(bidResponse)
                .account(account)
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookie)
                .build();
    }

    public static BidResponseContext from(
            ApplicationContext applicationContext,
            AuctionContext auctionContext,
            BidResponse bidResponse
    ) {
        return BidResponseContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(auctionContext.getBidRequest())
                .bidResponse(bidResponse)
                .account(auctionContext.getAccount())
                .httpRequest(auctionContext.getHttpRequest())
                .uidsCookie(auctionContext.getUidsCookie())
                .build();
    }

    public static BidResponseContext from(
            ApplicationContext applicationContext,
            GVastContext gVastContext
    ) {
        return BidResponseContext.builder()
                .applicationContext(applicationContext)
                .bidRequest(gVastContext.getBidRequest())
                .bidResponse(gVastContext.getBidResponse())
                .account(gVastContext.getAccount())
                .placement(gVastContext.getPlacement())
                .httpRequest(gVastContext.getAuctionContext().getHttpRequest())
                .uidsCookie(gVastContext.getAuctionContext().getUidsCookie())
                .build();
    }

    public BidType getBidType(Bid bid) {
        List<Imp> imps = this.bidRequest.getImp();
        Imp bidImp = imps.stream().filter(imp -> Objects.equals(imp.getId(), bid.getImpid())).findFirst().orElse(null);
        if (bidImp != null) {
            if (bidImp.getBanner() != null) {
                return BidType.banner;
            }

            if (bidImp.getVideo() != null) {
                return BidType.video;
            }

            if (bidImp.getAudio() != null) {
                return BidType.audio;
            }

            if (bidImp.getXNative() != null) {
                return BidType.xNative;
            }
        }
        return null;
    }
}
