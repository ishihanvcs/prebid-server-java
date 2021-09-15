package com.azerion.prebid.auction.customtrackers.injectors;

import com.azerion.prebid.auction.customtrackers.contracts.IBidTypeSpecificTrackerInjector;
import com.azerion.prebid.auction.customtrackers.contracts.ITrackerInjector;
import org.apache.commons.collections4.map.HashedMap;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Map;

public class TrackerInjector implements ITrackerInjector {

    protected final Map<BidType, IBidTypeSpecificTrackerInjector> typeSpecificInjectors;

    public TrackerInjector() {
        typeSpecificInjectors = new HashedMap<>(BidType.values().length);
        typeSpecificInjectors.put(BidType.banner, new ImgPixelInjectorForBanner());
        typeSpecificInjectors.put(BidType.video, new ImpressionInjectorForVideo());
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
