package com.improvedigital.prebid.server.customvast.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuperBuilder(toBuilder = true)
@Getter
@ToString
@EqualsAndHashCode
public class GVastHandlerParams {

    private static final Pattern START_WITH_HTTP_OR_HTTPS_PATTERN = Pattern.compile("^https?://");

    String impId;

    Integer coppa; // oRTB regs.coppa 1=yes, 0=no

    String gdpr; // "1"=gdpr applies

    @Builder.Default
    String gdprConsent = StringUtils.EMPTY;

    String referrer;

    Long tmax; // oRTB tmax

    CustParams custParams;

    String alpha3Country; // Country code using ISO-3166-1-alpha-3 (NB: oRTB requires alpha-3)

    List<String> cat; // Array of IAB content categories of the site

    Double bidfloor; // oRTB imp.bidfloor

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

    @Builder.Default
    boolean debug = false;

    public String getDomain() {
        Objects.requireNonNull(referrer);
        Matcher m = START_WITH_HTTP_OR_HTTPS_PATTERN.matcher(referrer);
        final String host = m.lookingAt()
                ? HttpUtil.getHostFromUrl(referrer)
                : referrer.replaceFirst("[/#].+$", "");
        Objects.requireNonNull(host);
        return host.trim().replaceFirst("^www\\.", "");
    }
}
