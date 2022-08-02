package com.improvedigital.prebid.server.customtracker.injectors;

import com.improvedigital.prebid.server.customtracker.contracts.IBidTypeSpecificTrackerInjector;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.regex.Pattern;

public class ImgPixelInjectorForBanner implements IBidTypeSpecificTrackerInjector {

    private static final Logger logger = LoggerFactory.getLogger(ImgPixelInjectorForBanner.class);

    private static final Pattern CLOSING_BODY_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*body\\s*>", Pattern.CASE_INSENSITIVE);
    public static final String BODY_TAG_CLOSING = "</body>";

    @Override
    public String inject(String trackingUrl, String adm, String bidder) {
        String[] admParts = CLOSING_BODY_TAG_PATTERN.split(adm);
        String imgTag = String.format("<img src=\"%s\">", trackingUrl);
        if (admParts.length == 2) {
            // Example, adm looks like: <html><body>...</body></html>
            adm = String.join("", admParts[0], imgTag, BODY_TAG_CLOSING, admParts[1]);
            logger.debug("Closing body tag found. Image pixel injected with: " + trackingUrl);
        } else {
            if (admParts[0].length() < adm.length()) {
                // Example, adm looks like: <body>...</body>
                adm = String.join("", admParts[0], imgTag, BODY_TAG_CLOSING);
                logger.debug("Closing body tag found. Image pixel injected with: " + trackingUrl);
            } else {
                // Example, adm looks like: <img src='' />...
                adm = String.join("", adm, imgTag);
                logger.debug("Closing body tag not found. Image pixel appended with: " + trackingUrl);
            }
        }
        return adm;
    }
}
