package com.improvedigital.prebid.server.customtracker.contracts;

public interface IBidTypeSpecificTrackerInjector {

    String inject(String trackingUrl, String adm, String bidder);
}
