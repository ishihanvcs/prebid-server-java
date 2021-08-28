package com.azerion.prebid.auction.requestfactory;

import com.azerion.prebid.auction.model.CustParams;
import com.azerion.prebid.auction.model.GVastParams;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class GVastParamsResolver {

    private static final Logger logger = LoggerFactory.getLogger(GVastParamsResolver.class);

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private String resolveReferrer(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();
        return ObjectUtils.firstNonNull(HttpUtil.decodeUrl(queryParams.get("referrer")),
                httpRequest.getHeaders().get(HttpUtil.REFERER_HEADER),
                httpRequest.getHeaders().get(HttpUtil.ORIGIN_HEADER),
                httpRequest.getAbsoluteUri());
    }

    private GVastParams.GVastParamsBuilder setGdprParams(
            HttpRequestContext httpRequest, GVastParams.GVastParamsBuilder builder
    ) {
        // Handle GDPR params
        final int gdpr;
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();
        String gdprConsentString = ObjectUtils.defaultIfNull(queryParams.get("gdpr_consent"), "");
        final String gdprParam = queryParams.get("gdpr");
        if (!StringUtils.isBlank(gdprParam)) {
            if (StringUtils.isNumeric(gdprParam)) {
                gdpr = gdprParam.equals("1") ? 1 : 0;
            } else {
                // "gdpr" param contains a string, assume gdpr applies and the string is a consent string
                gdpr = 1;
                gdprConsentString = gdprParam;
            }
        } else {
            // "gdpr" param not provided, let's derive the value from consent string existence
            gdpr = StringUtils.isBlank(gdprConsentString) ? 0 : 1;
        }

        if (gdpr == 1 && StringUtils.isBlank(gdprConsentString)) {
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

    public GVastParams resolve(HttpRequestContext httpRequest) {
        final CaseInsensitiveMultiMap queryParams = httpRequest.getQueryParams();

        if (queryParams.get("p") == null) {
            throw new InvalidRequestException("'p' parameter required");
        }

        int placementId = 0;
        try {
            placementId = Integer.parseInt(queryParams.get("p"));
        } catch (NumberFormatException e) { }

        return setGdprParams(httpRequest, GVastParams.builder()
                .placementId(placementId)
                .debug(queryParams.contains("debug") && queryParams.get("debug").equals("1"))
                .referrer(resolveReferrer(httpRequest))
                .custParams(new CustParams(queryParams.get("cust_params")))
                .cat(resolveCat(queryParams))
        ).build();
    }
}
