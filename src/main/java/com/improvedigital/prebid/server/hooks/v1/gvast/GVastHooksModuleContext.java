package com.improvedigital.prebid.server.hooks.v1.gvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.auction.model.Floor;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.ObjectUtil;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class GVastHooksModuleContext {

    Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt;
    Map<String, Floor> impIdToEffectiveFloor;
    BidRequest bidRequest;
    BidResponse bidResponse;

    public static GVastHooksModuleContext from(Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt) {
        return GVastHooksModuleContext.builder()
                .impIdToPbsImpExt(impIdToPbsImpExt)
                .build();
    }

    public GVastHooksModuleContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public GVastHooksModuleContext with(BidResponse bidResponse) {
        return this.toBuilder().bidResponse(bidResponse).build();
    }

    public GVastHooksModuleContext with(Map<String, Floor> impIdToFloor) {
        return this.toBuilder().impIdToEffectiveFloor(impIdToFloor).build();
    }

    public ImprovedigitalPbsImpExt getPbsImpExt(String impId) {
        return ObjectUtil.getIfNotNull(impId, impIdToPbsImpExt::get);
    }

    public ImprovedigitalPbsImpExt getPbsImpExt(Imp imp) {
        return ObjectUtil.getIfNotNull(imp, i -> this.getPbsImpExt(i.getId()));
    }

    public Floor getEffectiveFloor(String impId) {
        return ObjectUtil.getIfNotNull(impId, impIdToEffectiveFloor::get);
    }

    public Floor getEffectiveFloor(Imp imp) {
        return ObjectUtil.getIfNotNull(imp, i -> this.getEffectiveFloor(i.getId()));
    }
}
