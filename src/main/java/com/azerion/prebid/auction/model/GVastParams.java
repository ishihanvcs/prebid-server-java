package com.azerion.prebid.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.HttpUtil;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GVastParams {

    String placementId;

    Integer gdpr; // 1=gdpr applies

    String gdprConsentString;

    String referrer;

    String custParams;

    String country; // Country code using ISO-3166-1-alpha-2 (NB: oRTB requires alpha-3)

    List<String> cat; // Array of IAB content categories of the site

    boolean debug;

    public String getDomain() {
        final String host = referrer.startsWith("http") ? HttpUtil.getHostFromUrl(referrer) : referrer;
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
