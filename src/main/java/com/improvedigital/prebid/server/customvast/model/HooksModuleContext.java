package com.improvedigital.prebid.server.customvast.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.ObjectUtil;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class HooksModuleContext {

    Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt;
    Map<String, Floor> impIdToEffectiveFloor;
    String alpha3Country;
    BidRequest bidRequest;
    BidResponse bidResponse;

    public static HooksModuleContext from(Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt) {
        return HooksModuleContext.builder()
                .impIdToPbsImpExt(impIdToPbsImpExt)
                .build();
    }

    public HooksModuleContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public HooksModuleContext with(BidResponse bidResponse) {
        return this.toBuilder().bidResponse(bidResponse).build();
    }

    public HooksModuleContext with(Map<String, Floor> impIdToFloor) {
        return this.toBuilder().impIdToEffectiveFloor(impIdToFloor).build();
    }

    public HooksModuleContext with(String alpha3Country) {
        return this.toBuilder().alpha3Country(alpha3Country).build();
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
