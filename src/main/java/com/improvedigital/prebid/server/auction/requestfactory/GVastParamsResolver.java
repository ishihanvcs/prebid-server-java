package com.improvedigital.prebid.server.auction.requestfactory;

import com.improvedigital.prebid.server.auction.model.CustParams;
import com.improvedigital.prebid.server.auction.model.GVastParams;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GVastParamsResolver {

    private static final Logger logger = LoggerFactory.getLogger(GVastParamsResolver.class);

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final GdprConfig gdprConfig;

    public GVastParamsResolver(GdprConfig gdprConfig) {
        this.gdprConfig = Objects.requireNonNull(gdprConfig);
    }

    private String resolveReferrer(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();
        return ObjectUtils.firstNonNull(HttpUtil.decodeUrl(queryParams.get("referrer")),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER),
                httpRequest.getHeaders().get(HttpUtil.ORIGIN_HEADER),
                httpRequest.getAbsoluteUri());
    }

    private GVastParams.GVastParamsBuilder setGdprParams(
            HttpRequestContext httpRequest,
            GVastParams.GVastParamsBuilder builder
    ) {
        // Handle GDPR params
        final String gdpr;
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();
        String gdprConsentString = ObjectUtils.defaultIfNull(queryParams.get("gdpr_consent"), "");
        final String gdprParam = queryParams.get("gdpr");
        if (!StringUtils.isBlank(gdprParam)) {
            if (StringUtils.isNumeric(gdprParam)) {
                gdpr = gdprParam.equals("1") ? "1" : "0";
            } else {
                // "gdpr" param contains a string, assume gdpr applies and the string is a consent string
                gdpr = "1";
                gdprConsentString = gdprParam;
            }
        } else {
            // "gdpr" param not provided, let's derive the value from consent string existence
            if (!StringUtils.isBlank(gdprConsentString)) {
                gdpr = "1";
            } else if (gdprConfig.getEnabled()) {
                gdpr = gdprConfig.getDefaultValue();
            } else {
                gdpr = "";
            }
        }

        if (gdpr.equals("1") && StringUtils.isBlank(gdprConsentString)) {
            conditionalLogger.warn(
                    String.format("Consent missing. Referer: %s", resolveReferrer(httpRequest)),
                    1000
            );
        }

        return builder.gdpr(gdpr).gdprConsentString(gdprConsentString);
    }

    private List<String> resolveCat(CaseInsensitiveMultiMap queryParams) {
        return queryParams.contains("cat")
                ? Arrays.asList(Objects.requireNonNull(HttpUtil.decodeUrl(queryParams.get("cat"))).split(","))
                : new ArrayList<>();
    }

    private List<Integer> resolveIntArray(CaseInsensitiveMultiMap queryParams, String param) {
        final String value = queryParams.get(param);
        if (value == null) {
            return null;
        }

        try {
            return Arrays.stream(value.split(","))
                .mapToInt(Integer::parseInt)
                .boxed()
                .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(String.format("'%s' parameter must be an array of numbers", param));
        }
    }

    private Integer resolveCoppa(String coppa) {
        if (StringUtils.isBlank(coppa)) {
            return null;
        }
        switch (coppa) {
            case "1":
                return 1;
            case "0":
                return 0;
            default:
                throw new InvalidRequestException(String.format("Invalid value for 'coppa'"));
        }
    }

    private Integer resolveIntParam(CaseInsensitiveMultiMap queryParams, String param) {
        final String value = queryParams.get(param);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(String.format("'%s' parameter must be a number", param));
        }
    }

    public GVastParams resolve(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();

        if (queryParams.get("p") == null) {
            throw new InvalidRequestException("'p' parameter is required");
        }

        int placementId = ObjectUtils.defaultIfNull(resolveIntParam(queryParams, "p"), 0);

        Integer tmax = resolveIntParam(queryParams, "tmax");

        return setGdprParams(httpRequest, GVastParams.builder()
                .coppa(resolveCoppa(queryParams.get("coppa")))
                .impId(String.valueOf(placementId))
                .debug(queryParams.contains("debug") && queryParams.get("debug").equals("1"))
                .referrer(resolveReferrer(httpRequest))
                .tmax(tmax == null ? null : tmax.longValue())
                .custParams(new CustParams(queryParams.get("cust_params")))
                .cat(resolveCat(queryParams))
                // Device
                .carrier(queryParams.get("carrier"))
                .ifa(queryParams.get("ifa"))
                .ip(queryParams.get("ip"))
                .lmt(resolveIntParam(queryParams, "lmt"))
                .model(queryParams.get("model"))
                .os(queryParams.get("os"))
                .osv(queryParams.get("osv"))
                .ua(queryParams.get("ua"))
                // App
                .appName(queryParams.get("appname"))
                .bundle(queryParams.get("bundle"))
                .storeUrl(queryParams.get("storeurl"))
                // Video
                .minduration(resolveIntParam(queryParams, "minduration"))
                .maxduration(resolveIntParam(queryParams, "maxduration"))
                .w(resolveIntParam(queryParams, "w"))
                .h(resolveIntParam(queryParams, "h"))
                .protocols(resolveIntArray(queryParams, "protocols"))
                .api(resolveIntArray(queryParams, "api"))
                .placement(resolveIntParam(queryParams, "placement"))
        ).build();
    }
}
