package com.azerion.prebid.auction.customtrackers.contracts;

public interface IBidTypeSpecificTrackerInjector {

    String inject(String trackingUrl, String adm, String bidder);
}
