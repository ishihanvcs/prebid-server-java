package com.azerion.prebid.auction.customtrackers.injectors;

import com.azerion.prebid.auction.customtrackers.contracts.IBidTypeSpecificTrackerInjector;

public class ImgPixelInjectorForBanner implements IBidTypeSpecificTrackerInjector {

    @Override
    public String inject(String trackingUrl, String adm, String bidder) {
        String[] admParts = adm.split("<\\s*/\\s*body\\s*>");
        String imgTag = String.format("<img src=\"%s\">", trackingUrl);
        if (admParts.length == 2) {
            adm = String.join("", admParts[0], imgTag, "</body>", admParts[1]);
        } else {
            adm = String.join("", adm, imgTag);
        }
        return adm;
    }
}
