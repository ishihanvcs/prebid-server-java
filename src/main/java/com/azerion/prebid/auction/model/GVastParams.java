package com.azerion.prebid.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.HttpUtil;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GVastParams {

    long placementId;

    String gdpr; // "1"=gdpr applies

    String gdprConsentString;

    String referrer;

    CustParams custParams;

    String country; // Country code using ISO-3166-1-alpha-2 (NB: oRTB requires alpha-3)

    List<String> cat; // Array of IAB content categories of the site

    String ifa; // oRTB device.ifa

    String ua;  // oRTB device.ua (user agent)

    String bundle; // oRTB app.bundle

    Integer minduration; // oRTB video.minduration;

    Integer maxduration; // oRTB video.maxduration

    Integer w; // oRTB video.w, player width

    Integer h; // oRTB video.h, player height

    List<Integer> protocols; // oRTB video.protocols

    List<Integer> api; // oRTB video.api

    Integer placement; // oRTB video.placement

    boolean debug;

    public String getDomain() {
        final String host = referrer.startsWith("http") ? HttpUtil.getHostFromUrl(referrer) : referrer;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
