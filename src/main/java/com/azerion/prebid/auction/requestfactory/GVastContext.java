package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.settings.model.Placement;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;

@Builder(toBuilder = true)
@Value
public class GVastContext {

    RoutingContext routingContext;
    AuctionContext auctionContext;
    Account account;
    Placement placement;
    BidRequest bidRequest;
    GVastParams gVastParams;

    @Setter(AccessLevel.PUBLIC)
    @NonFinal
    BidResponse bidResponse = null;

    public static GVastContext from(GVastParams gVastParams) {
        return GVastContext.builder().gVastParams(gVastParams).build();
    }

    GVastContext with(Account account) {
        return this.toBuilder().account(account).build();
    }

    GVastContext with(Placement placement) {
        return this.toBuilder().placement(placement).build();
    }

    GVastContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    GVastContext with(BidResponse bidResponse) {
        return this.toBuilder().bidResponse(bidResponse).build();
    }

    GVastContext with(AuctionContext auctionContext) {
        return this.toBuilder().auctionContext(auctionContext).build();
    }

    GVastContext with(RoutingContext routingContext) {
        return this.toBuilder().routingContext(routingContext).build();
    }
}
