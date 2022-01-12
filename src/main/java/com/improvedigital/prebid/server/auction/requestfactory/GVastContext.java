package com.improvedigital.prebid.server.auction.requestfactory;

import com.improvedigital.prebid.server.auction.model.GVastParams;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;

@Builder(toBuilder = true)
@Value
public class GVastContext {

    RoutingContext routingContext;
    AuctionContext auctionContext;
    Account account;
    Imp imp;
    ImprovedigitalPbsImpExt improvedigitalPbsImpExt;
    BidRequest bidRequest;
    BidResponse bidResponse;
    GVastParams gVastParams;

    public static GVastContext from(GVastParams gVastParams) {
        return GVastContext.builder().gVastParams(gVastParams).build();
    }

    public GVastContext with(Imp imp, JacksonMapper mapper) throws JsonProcessingException {
        ImprovedigitalPbsImpExt improvedigitalPbsImpExt = mapper.mapper().treeToValue(
                imp.getExt().at("/prebid/improvedigitalpbs"), ImprovedigitalPbsImpExt.class
        );
        return this.toBuilder().imp(imp).improvedigitalPbsImpExt(improvedigitalPbsImpExt).build();
    }

    public GVastContext with(Account account) {
        return this.toBuilder().account(account).build();
    }

    public GVastContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public GVastContext with(BidResponse bidResponse) {
        return this.toBuilder().bidResponse(bidResponse).build();
    }

    public GVastContext with(AuctionContext auctionContext) {
        return this.toBuilder().auctionContext(auctionContext).build();
    }

    public GVastContext with(RoutingContext routingContext) {
        return this.toBuilder().routingContext(routingContext).build();
    }
}
