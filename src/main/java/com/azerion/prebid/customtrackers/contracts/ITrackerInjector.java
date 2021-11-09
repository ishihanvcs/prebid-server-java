package com.azerion.prebid.customtrackers.contracts;

import org.prebid.server.proto.openrtb.ext.response.BidType;

public interface ITrackerInjector {

    String inject(String trackingUrl, String adm, String bidder, BidType bidType);
}
