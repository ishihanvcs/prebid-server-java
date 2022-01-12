package com.improvedigital.prebid.server.customtrackers.contracts;

public interface IBidTypeSpecificTrackerInjector {

    String inject(String trackingUrl, String adm, String bidder);
}
