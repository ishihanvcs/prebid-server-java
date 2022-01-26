package com.improvedigital.prebid.server.customtrackers.injectors;

import com.improvedigital.prebid.server.customtrackers.contracts.IBidTypeSpecificTrackerInjector;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.regex.Pattern;

public class ImgPixelInjectorForBanner implements IBidTypeSpecificTrackerInjector {

    private static final Logger logger = LoggerFactory.getLogger(ImgPixelInjectorForBanner.class);

    private static final Pattern CLOSING_BODY_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*body\\s*>", Pattern.CASE_INSENSITIVE);

    @Override
    public String inject(String trackingUrl, String adm, String bidder) {
        String[] admParts = CLOSING_BODY_TAG_PATTERN.split(adm);
        String imgTag = String.format("<img src=\"%s\">", trackingUrl);
        if (admParts.length == 2) {
            adm = String.join("", admParts[0], imgTag, "</body>", admParts[1]);
            logger.debug("Closing body tag found. Image pixel injected with: "
                    + trackingUrl);
        } else {
            adm = String.join("", adm, imgTag);
            logger.debug("Closing body tag not found. Image pixel appended with: "
                    + trackingUrl);
        }
        return adm;
    }
}
