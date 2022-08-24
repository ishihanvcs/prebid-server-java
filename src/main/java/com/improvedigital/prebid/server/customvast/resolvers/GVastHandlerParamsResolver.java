package com.improvedigital.prebid.server.customvast.resolvers;

import com.improvedigital.prebid.server.customvast.model.CustParams;
import com.improvedigital.prebid.server.customvast.model.GVastHandlerParams;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.CountryCodeMapper;
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

public class GVastHandlerParamsResolver {

    private static final Logger logger = LoggerFactory.getLogger(GVastHandlerParamsResolver.class);

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final GdprConfig gdprConfig;
    private final CountryCodeMapper countryCodeMapper;

    public GVastHandlerParamsResolver(
            CountryCodeMapper countryCodeMapper,
            GdprConfig gdprConfig
    ) {
        this.gdprConfig = Objects.requireNonNull(gdprConfig);
        this.countryCodeMapper = Objects.requireNonNull(countryCodeMapper);
    }

    private String resolveReferrer(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();
        return ObjectUtils.firstNonNull(HttpUtil.decodeUrl(queryParams.get("referrer")),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER),
                httpRequest.getHeaders().get(HttpUtil.ORIGIN_HEADER),
                httpRequest.getAbsoluteUri());
    }

    private GVastHandlerParams.GVastHandlerParamsBuilder<?, ?> setGdprParams(
            HttpRequestContext httpRequest,
            GVastHandlerParams.GVastHandlerParamsBuilder<?, ?> builder
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

        return builder.gdpr(gdpr).gdprConsent(gdprConsentString);
    }

    private String resolveAlpha3Country(CaseInsensitiveMultiMap queryParams) {
        String alpha3Country = queryParams.get("country_alpha3");
        if (StringUtils.isNotBlank(alpha3Country)) {
            return alpha3Country;
        }
        String alpha2Country = queryParams.get("country");
        if (StringUtils.isNotBlank(alpha2Country)) {
            return countryCodeMapper.mapToAlpha3(alpha2Country);
        }
        return null;
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
                throw new InvalidRequestException("Invalid value for 'coppa'");
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

    private Double resolveDoubleParam(CaseInsensitiveMultiMap queryParams, String param) {
        final String value = queryParams.get(param);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(String.format("'%s' parameter must be a number", param));
        }
    }

    private String resolveCurrency(CaseInsensitiveMultiMap queryParams, String param) {
        final String currency = queryParams.get(param);
        if (currency == null) {
            return null;
        }
        if (currency.length() != 3) {
            throw new InvalidRequestException(String.format("Invalid currency code in parameter '%s'", param));
        }
        return currency.toUpperCase();
    }

    public GVastHandlerParams resolve(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();

        if (queryParams.get("p") == null) {
            throw new InvalidRequestException("'p' parameter is required");
        }

        int placementId = ObjectUtils.defaultIfNull(resolveIntParam(queryParams, "p"), 0);

        Integer tmax = resolveIntParam(queryParams, "tmax");

        return setGdprParams(httpRequest, GVastHandlerParams.builder()
                .coppa(resolveCoppa(queryParams.get("coppa")))
                .impId(String.valueOf(placementId))
                .debug(queryParams.contains("debug") && queryParams.get("debug").equals("1"))
                .referrer(resolveReferrer(httpRequest))
                .tmax(tmax == null ? null : tmax.longValue())
                .custParams(new CustParams(queryParams.get("cust_params")))
                .cat(resolveCat(queryParams))
                .bidfloor(resolveDoubleParam(queryParams, "bidfloor"))
                .bidfloorcur(resolveCurrency(queryParams, "bidfloorcur"))
                // Device
                .carrier(queryParams.get("carrier"))
                .ifa(queryParams.get("ifa"))
                .ip(queryParams.get("ip"))
                .lmt(resolveIntParam(queryParams, "lmt"))
                .model(queryParams.get("model"))
                .os(queryParams.get("os"))
                .osv(queryParams.get("osv"))
                .ua(queryParams.get("ua"))
                .alpha3Country(resolveAlpha3Country(queryParams))
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
