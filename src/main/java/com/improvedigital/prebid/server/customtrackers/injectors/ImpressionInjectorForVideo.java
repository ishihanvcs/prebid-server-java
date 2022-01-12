package com.improvedigital.prebid.server.customtrackers.injectors;

import com.improvedigital.prebid.server.customtrackers.contracts.IBidTypeSpecificTrackerInjector;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;

public class ImpressionInjectorForVideo implements IBidTypeSpecificTrackerInjector {

    private static final Logger logger = LoggerFactory.getLogger(ImpressionInjectorForVideo.class);
    protected static final String IN_LINE_TAG = "<InLine>";
    protected static final String IN_LINE_CLOSE_TAG = "</InLine>";
    protected static final String WRAPPER_TAG = "<Wrapper>";
    protected static final String WRAPPER_CLOSE_TAG = "</Wrapper>";
    protected static final String IMPRESSION_CLOSE_TAG = "</Impression>";

    /**
     * Implementation borrowed from {@link org.prebid.server.vast.VastModifier}
     * to inject Impression tag into the vastXml
     * @param vastXml {@link String}
     * @param vastUrlTracking {@link String}
     * @param bidder {@link String}
     * @return String modified vastXml after appending Impression tag in appropriate place
     */
    private String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking, String bidder) {
        final int inLineTagIndex = StringUtils.indexOfIgnoreCase(vastXml, IN_LINE_TAG);
        final int wrapperTagIndex = StringUtils.indexOfIgnoreCase(vastXml, WRAPPER_TAG);

        if (inLineTagIndex != -1) {
            return appendTrackingUrl(vastXml, vastUrlTracking, IN_LINE_CLOSE_TAG);
        } else if (wrapperTagIndex != -1) {
            return appendTrackingUrl(vastXml, vastUrlTracking, WRAPPER_CLOSE_TAG);
        }
        throw new PreBidException(
                String.format("VastXml does not contain neither InLine nor Wrapper for %s response", bidder));
    }

    private String appendTrackingUrl(String vastXml, String vastUrlTracking, String elementCloseTag) {
        if (vastXml.contains(IMPRESSION_CLOSE_TAG)) {
            return insertAfterExistingImpressionTag(vastXml, vastUrlTracking);
        }
        return insertBeforeElementCloseTag(vastXml, vastUrlTracking, elementCloseTag);
    }

    private String insertAfterExistingImpressionTag(String vastXml, String vastUrlTracking) {
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";
        final int replacementStart = vastXml.lastIndexOf(IMPRESSION_CLOSE_TAG);
        logger.info("Impression tag injection successful for: " + vastUrlTracking);
        return vastXml.substring(0, replacementStart)
                + IMPRESSION_CLOSE_TAG
                + impressionTag
                + vastXml.substring(replacementStart + IMPRESSION_CLOSE_TAG.length());
    }

    private String insertBeforeElementCloseTag(String vastXml, String vastUrlTracking, String elementCloseTag) {
        final int indexOfCloseTag = StringUtils.indexOfIgnoreCase(vastXml, elementCloseTag);

        if (indexOfCloseTag == -1) {
            logger.warn("Impression tag injection failed for: " + vastUrlTracking);
            return vastXml;
        }

        final String caseSpecificCloseTag =
                vastXml.substring(indexOfCloseTag, indexOfCloseTag + elementCloseTag.length());
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";
        logger.info("Impression tag injection successful for: " + vastUrlTracking);
        return vastXml.replace(caseSpecificCloseTag, impressionTag + caseSpecificCloseTag);
    }

    @Override
    public String inject(String trackingUrl, String adm, String bidder) {
        return appendTrackingUrlToVastXml(adm, trackingUrl, bidder);
    }
}
