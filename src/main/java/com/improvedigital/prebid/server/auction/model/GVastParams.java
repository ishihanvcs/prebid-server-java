package com.improvedigital.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.HttpUtil;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GVastParams {

    String impId;

    Integer coppa; // oRTB regs.coppa 1=yes, 0=no

    String gdpr; // "1"=gdpr applies

    String gdprConsentString;

    String referrer;

    Long tmax; // oRTB tmax

    CustParams custParams;

    String country; // Country code using ISO-3166-1-alpha-2 (NB: oRTB requires alpha-3)

    List<String> cat; // Array of IAB content categories of the site

    double bidfloor; // oRTB imp.bidfloor

    String bidfloorcur; // oRTB imp.bidfloorcur

    String carrier; // oRTB device.carrier

    String ifa; // oRTB device.ifa

    String ip; // oRTB device.ip

    Integer lmt; // oRTB device.lmt "Limit Ad Tracking" 0=no, 1=yes

    String model; // oRTB device.model

    String os; // oRTB device.os

    String osv; // oRTB device.osv

    String ua;  // oRTB device.ua (user agent)

    String appName; // oRTB app.name

    String bundle; // oRTB app.bundle

    String storeUrl; // oRTB app.storeurl

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
