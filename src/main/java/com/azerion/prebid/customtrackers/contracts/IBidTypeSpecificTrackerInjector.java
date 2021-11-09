package com.azerion.prebid.customtrackers.contracts;

public interface IBidTypeSpecificTrackerInjector {

    String inject(String trackingUrl, String adm, String bidder);
}
