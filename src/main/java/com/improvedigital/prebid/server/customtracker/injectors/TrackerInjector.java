package com.improvedigital.prebid.server.customtracker.injectors;

import com.improvedigital.prebid.server.customtracker.contracts.IBidTypeSpecificTrackerInjector;
import com.improvedigital.prebid.server.customtracker.contracts.ITrackerInjector;
import org.apache.commons.collections4.map.HashedMap;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Map;

public class TrackerInjector implements ITrackerInjector {

    protected final Map<BidType, IBidTypeSpecificTrackerInjector> typeSpecificInjectors;

    public TrackerInjector(JacksonMapper mapper) {
        typeSpecificInjectors = new HashedMap<>(BidType.values().length);
        typeSpecificInjectors.put(BidType.banner, new ImgPixelInjectorForBanner());
        typeSpecificInjectors.put(BidType.video, new ImpressionInjectorForVideo());
        typeSpecificInjectors.put(BidType.xNative, new ImpTrackerInjectorForNative(mapper));
    }

    @Override
    public String inject(String trackingUrl, String adm, String bidder, BidType bidType) {
        if (typeSpecificInjectors.containsKey(bidType)) {
            return typeSpecificInjectors.get(bidType)
                    .inject(trackingUrl, adm, bidder);
        }
        return adm;
    }
}
