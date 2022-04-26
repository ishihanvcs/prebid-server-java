package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.auction.model.GVastParams;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.ObjectUtil;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class GVastHooksModuleContext {

    Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt;
    BidRequest bidRequest;
    GVastParams gVastParams;

    public static GVastHooksModuleContext from(Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt) {
        return GVastHooksModuleContext.builder()
                .impIdToPbsImpExt(impIdToPbsImpExt)
                .build();
    }

    public GVastHooksModuleContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public GVastHooksModuleContext with(GVastParams gVastParams) {
        return this.toBuilder().gVastParams(gVastParams).build();
    }

    public ImprovedigitalPbsImpExt getPbsImpExt(Imp imp) {
        return ObjectUtil.getIfNotNull(imp, i -> impIdToPbsImpExt.get(i.getId()));
    }
}
