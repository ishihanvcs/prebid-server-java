package com.azerion.prebid.auction;

import com.azerion.prebid.auction.model.BidFloor;
import com.azerion.prebid.auction.model.GVastParams;
import com.azerion.prebid.auction.model.ImpExtConfig;
import com.azerion.prebid.auction.requestfactory.GVastContext;
import com.azerion.prebid.utils.FluentMap;
import com.azerion.prebid.utils.MacroProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for creating VAST XML response for the /gvast handler. The response consists of a Google Ad Manager tag
 * (https://support.google.com/admanager/table/9749596?hl=en) with params and SSP bids attached.
 * If debug mode is enabled, the response XML will also include a "debug" extension with SSP requests/responses
 * and Prebid cache calls.
 */
public class GVastResponseCreator {

    private static final String GOOGLE_VAST_TAG = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480|640x360&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&correlator=";
    private static final double IMPROVE_DIGITAL_DEAL_FLOOR = 1.5;
    private static final Logger logger = LoggerFactory.getLogger(GVastResponseCreator.class);

    private final String externalUrl;
    private final String gamNetworkCode;
    private final String pbct;
    private final MacroProcessor macroProcessor;

    public GVastResponseCreator(
            MacroProcessor macroProcessor,
            String externalUrl,
            String gamNetworkCode,
            String cacheHost
    ) {
        this.macroProcessor = Objects.requireNonNull(macroProcessor);
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
        this.gamNetworkCode = Objects.requireNonNull(gamNetworkCode);
        if ("pbc-proto.360polaris.biz".equals(cacheHost)) {
            this.pbct = "3";
        } else {
            this.pbct = "1";
        }
    }

    public String create(GVastContext gVastContext, boolean prioritizeImprovedigitalDeals) {
        GVastParams gVastParams = gVastContext.getGVastParams();
        RoutingContext routingContext = gVastContext.getRoutingContext();
        BidResponse bidResponse = gVastContext.getBidResponse();
        routingContext.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER,
                AsciiString.cached("application/xml"));
        XmlMapper xmlMapper = new XmlMapper();

        String debugInfo = "";
        if (gVastParams.isDebug()) {
            try {
                debugInfo = xmlMapper.writeValueAsString(bidResponse.getExt());
            } catch (JsonProcessingException ignored) {
            }
        }

        final String targeting = formatPrebidGamKeyValueString(bidResponse, prioritizeImprovedigitalDeals);
        return buildVastXmlResponse(
                targeting,
                gVastContext,
                prioritizeImprovedigitalDeals && targeting.contains("hb_deal_improvedigit"),
                debugInfo
        );
    }

    private String formatPrebidGamKeyValueString(BidResponse bidResponse, boolean prioritizeImprovedigitalDeals) {
        if (bidResponse.getSeatbid().isEmpty()) {
            return "";
        }

        StringBuilder targeting = new StringBuilder();
        boolean improvedigitalDealWon = false;
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid.getBid().isEmpty()) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                if (bid.getExt() == null) {
                    continue;
                }
                StringBuilder bidderKeyValues = new StringBuilder();
                JsonNode targetingKvs = bid.getExt().at("/prebid/targeting");
                boolean isDeal = false;
                boolean hasUuid = false;
                double price = 0;

                for (Iterator<String> it = targetingKvs.fieldNames(); it.hasNext(); ) {
                    String key = it.next();
                    bidderKeyValues.append(key).append("=").append(targetingKvs.get(key).asText()).append("&");

                    if (key.equals("hb_deal_improvedigit")) {
                        isDeal = true;
                        continue;
                    }
                    if (key.startsWith("hb_uuid")) {
                        hasUuid = true;
                        continue;
                    }
                    if (key.startsWith("hb_pb_")) {
                        price = targetingKvs.get(key).asDouble();
                    }
                }

                // Discard the bid if VastXML couldn't be cached, i.e. hb_uuid KV wasn't set
                if (!hasUuid) {
                    continue;
                }

                if (prioritizeImprovedigitalDeals && isDeal && price >= IMPROVE_DIGITAL_DEAL_FLOOR) {
                    // ImproveDigital deal won't always win if there's a higher bid. In that case we need to add
                    // winner Prebid KVs
                    if (bidderKeyValues.indexOf("hb_pb=") == -1) {
                        for (Iterator<String> it = targetingKvs.fieldNames(); it.hasNext(); ) {
                            String key = it.next();
                            // Create winner keys by removing the bidder name from the key,
                            // i.e. hb_pb_improvedigital -> hb_pb
                            String winnerKey = key.substring(0, key.lastIndexOf('_'));
                            bidderKeyValues
                                    .append(winnerKey)
                                    .append("=")
                                    .append(targetingKvs.get(key).asText())
                                    .append("&");
                        }
                    }
                    // Discard bids from other SSPs when prioritizing deal/campaigns from Improve Digital
                    targeting = new StringBuilder(bidderKeyValues.toString());
                    improvedigitalDealWon = true;
                    break;
                }

                targeting.append(bidderKeyValues);
            }
            if (improvedigitalDealWon) {
                break;
            }
        }
        if (targeting.length() > 0) {
            // Target Azerion Prebid cache line items (creatives) in GAM
            targeting.append("pbct=").append(pbct);
            // Disable Google AdX/AdSense and pricing rules for ImproveDigital deals
            if (improvedigitalDealWon) {
                targeting.append("&tnl_wog=1&nf=1");
            }
        }
        return targeting.toString();
    }

    private String resolveCountryCode(Map<String, ?> map, GeoInfo geoInfo) {
        return ObjectUtil.getIfNotNullOrDefault(geoInfo,
                gn -> map.containsKey(gn.getCountry())
                        ? gn.getCountry()
                        : ImpExtConfig.DEFAULT_CONFIG_KEY,
                () -> ImpExtConfig.DEFAULT_CONFIG_KEY
        );
    }

    private double getBidFloor(ImpExtConfig config, GeoInfo geoInfo) {
        final double defaultResult = 0.0;
        final Map<String, BidFloor> bidFloors = config.getBidFloors();
        if (bidFloors.isEmpty()) {
            return defaultResult;
        }

        final String countryCode = resolveCountryCode(bidFloors, geoInfo);
        // logger.info("countryCode = " + countryCode);

        if (bidFloors.containsKey(countryCode)) {
            return bidFloors.get(countryCode).getBidFloor();
        }

        return defaultResult;
    }

    private List<String> getWaterfall(ImpExtConfig config, GeoInfo geoInfo) {
        final Map<String, List<String>> waterfall = config.getWaterfall();
        final List<String> defaultResult = List.of("gam");
        if (waterfall.isEmpty()) {
            return defaultResult;
        }
        final String countryCode = resolveCountryCode(waterfall, geoInfo);

        if (waterfall.containsKey(countryCode)) {
            return waterfall.get(countryCode);
        }
        return defaultResult;
    }

    private String getGamAdUnit(GVastParams gVastParams, ImpExtConfig config) {
        String adUnit = config.getGamAdUnit();
        if (StringUtils.isBlank(adUnit)) {
            adUnit = "/" + gamNetworkCode + "/pbs/" + gVastParams.getImpId();
        } else {
            // If ad unit begins with "/", full ad unit path is expected including the GAM network code
            // otherwise the GAM network code will be prepended
            if (adUnit.charAt(0) != '/') {
                adUnit = "/" + gamNetworkCode + "/" + adUnit;
            }
        }
        return adUnit;
    }

    private String buildGamVastTagUrl(
            String impId,
            String adUnit,
            double bidFloor,
            String referrer,
            String targeting, String gdpr, String gdprConsent) {
        String gamTag = GOOGLE_VAST_TAG + System.currentTimeMillis() + "&iu=" + adUnit;
        if (referrer != null) {
            final String encodedReferrer = HttpUtil.encodeUrl(referrer);
            gamTag += "&url=" + encodedReferrer + "&description_url=" + encodedReferrer;
        }

        // cust_params
        String targetingString = "";
        if (!StringUtils.isBlank(targeting)) {
            targetingString += targeting;
        }
        if (bidFloor > 0 && !targetingString.contains("fp=")) {
            targetingString += (targetingString.length() > 0 ? "&" : "") + "fp=" + bidFloor;
        }
        if (!StringUtils.isBlank(targetingString)) {
            targetingString = targetingString.replace("pbct=2", "pbct=" + pbct); // HACK - fix
            gamTag += "&cust_params=" + HttpUtil.encodeUrl(targetingString);
        }

        if (impId != null && impId.equals("22505159")) {
            gamTag += "&sdki=44d&sdk_apis=2%2C8&sdkv=h.3.460.0&gdpr=" + gdpr;
            if (!StringUtils.isBlank(gdprConsent)) {
                gamTag += "&gdpr_consent=" + gdprConsent;
            }
        }
        return gamTag;
    }

    private String getRedirect(String externalUrl, String bidder, String gdpr, String gdprConsent,
                               String userIdParamName) {
        // TODO add us_privacy
        return externalUrl + "/setuid?bidder=" + bidder + "&gdpr=" + gdpr + "&gdpr_consent=" + gdprConsent
                + "&us_privacy=&uid=" + userIdParamName;
    }

    private String replaceMacros(String tag, String gdpr, String gdprConsent, String referrer) {
        final Map<String, String> macroValues =
                FluentMap.<String, String>create()
                        .put("gdpr", gdpr)
                        .put("gdpr_consent", gdprConsent)
                        .put("timestamp", Long.toString(System.currentTimeMillis()))
                        .put("referrer", HttpUtil.encodeUrl(referrer))
                        .result();
        String expanded = "";
        try {
            expanded = macroProcessor.process(tag, macroValues, true);
        } catch (Exception ignored) { }
        return expanded;
    }

    private String buildVastAdTag(String tagUrl, boolean isGam, String gdpr, String gdprConsent, String referrer,
                                  String debugInfo, int adIndex, boolean isLastAd) {
        StringBuilder sb = new StringBuilder();
        final boolean singleAd = adIndex == 0 && isLastAd;
        sb.append("<Ad><Wrapper")
                .append(isLastAd ? ">" : " fallbackOnNoAd=\"true\">")
                .append("<AdSystem>Azerion PBS</AdSystem>")
                .append("<VASTAdTagURI><![CDATA[")
                .append(replaceMacros(tagUrl, gdpr, gdprConsent, referrer))
                .append("]]></VASTAdTagURI><Creatives></Creatives>");

        if (isGam) {
            // Inject sync pixels as imp pixels
            // TODO
            // Move list of SSPs to a config
            // Use logic from CookieSyncHandler to generate sync urls
            final String[] userSyncs = {
                    "https://ib.adnxs.com/getuid?"
                            + HttpUtil.encodeUrl(getRedirect(externalUrl, "adnxs", gdpr, gdprConsent, "$UID")),
                    "https://ad.360yield.com/server_match?gdpr=" + gdpr + "&gdpr_consent=" + gdprConsent + "&us_privacy=&r="
                            + HttpUtil.encodeUrl(getRedirect(externalUrl, "improvedigital", gdpr, gdprConsent,
                            "{PUB_USER_ID}")),
                    "https://image8.pubmatic.com/AdServer/ImgSync?p=159706&gdpr=" + gdpr + "&gdpr_consent=" + gdprConsent
                            + "&us_privacy=&pu="
                            + HttpUtil.encodeUrl(getRedirect(externalUrl, "pubmatic", gdpr, gdprConsent, "#PMUID")),
                    "https://ssbsync-global.smartadserver.com/api/sync?callerId=5&gdpr=" + gdpr + "&gdpr_consent=" + gdprConsent
                            + "&us_privacy=&redirectUri="
                            + HttpUtil.encodeUrl(getRedirect(externalUrl, "smartadserver", gdpr, gdprConsent,
                            "[ssb_sync_pid]"))
            };
            for (String userSync : userSyncs) {
                sb.append("<Impression><![CDATA[").append(userSync).append("]]></Impression>");
            }
        }

        // Extensions
        sb.append("<Extensions>");
        if (!StringUtils.isBlank(debugInfo)) {
            sb.append("<Extension type=\"debug\">")
                    .append(debugInfo)
                    .append("</Extension>");
        }
        if (!singleAd) {
            sb.append("<Extension type=\"waterfall\" fallback_index=\"").append(adIndex).append("\"/>");
        }
        sb.append("</Extensions>");

        sb.append("</Wrapper></Ad>");
        return sb.toString();
    }

    private String buildTargetingString(Stream<String> stream) {
        return stream.filter(s -> !StringUtils.isBlank(s)).collect(Collectors.joining("&"));
    }

    private String buildVastXmlResponse(String gamPrebidTargeting, GVastContext gVastContext,
                                        boolean isImprovedigitalDeal, String hbAuctionDebugInfo) {
        final GVastParams gVastParams = gVastContext.getGVastParams();
        final String custParams = gVastParams.getCustParams().toString();
        final String gdprConsent = gVastParams.getGdprConsentString();
        final String referrer = gVastParams.getReferrer();
        final String gdpr = gVastParams.getGdpr();
        final ImpExtConfig config = gVastContext.getImpExtConfig();
        final String adUnit = getGamAdUnit(gVastParams, config);
        final GeoInfo geoInfo = gVastContext.getAuctionContext().getGeoInfo();
        final String impId = gVastParams.getImpId();
        final double bidFloor = getBidFloor(config, geoInfo);
        // logger.info("bidFloor = " + bidFloor);
        final List<String> waterfall = getWaterfall(config, geoInfo);
        final String categoryTargeting;
        final List<String> categories = gVastParams.getCat();

        if (categories != null && categories.size() > 0) {
            categoryTargeting = "iab_cat=" + String.join(",", gVastParams.getCat());
        } else {
            categoryTargeting = null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\">");

        // In order to prioritize Improve Digital deal over other ads, a second GAM tag is added
        // The first GAM call will disable AdX/AdSense. In case Improve's VAST doesn't fill or the ad
        // fails to play, the second GAM call is added as a fallback to give AdX a chance to backfill
        if (isImprovedigitalDeal) {
            waterfall.add(0, "gam_improve_deal");
            final int gamIndex = waterfall.indexOf("gam");
            if (gamIndex >= 0) {
                waterfall.set(gamIndex, "gam_no_hb");
            }
        }
        // logger.info("waterfall = " + waterfall);
        final int numTags = waterfall.size();
        int i = 0;
        for (String adTag : waterfall) {
            switch (adTag) {
                // GAM + HB bids
                case "gam":
                case "gam_improve_deal":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(impId, adUnit, bidFloor, referrer,
                                    buildTargetingString(Stream.of(gamPrebidTargeting, custParams, categoryTargeting)),
                                    gdpr,
                                    gdprConsent),
                            true, gdpr, gdprConsent, referrer, hbAuctionDebugInfo, i, i == numTags - 1));
                    break;
                case "gam_no_hb":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(impId, adUnit, bidFloor, referrer,
                                    buildTargetingString(Stream.of(custParams, categoryTargeting)),
                                    gdpr,
                                    gdprConsent),
                            true, gdpr, gdprConsent, referrer, null, i, i == numTags - 1));
                    break;
                // First look is for all low-fill campaigns that should get first look before allowing AdX to monetise
                // fl=1 -> first look targeting KV
                // tnl_wog=1 -> disable AdX & AdSense
                case "gam_first_look":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(impId, adUnit, bidFloor, referrer,
                                    buildTargetingString(Stream.of(custParams, categoryTargeting, "fl=1&tnl_wog=1")),
                                    gdpr,
                                    gdprConsent),
                            true, gdpr, gdprConsent, referrer, null, i, i == numTags - 1));
                    break;
                default:
                    sb.append(buildVastAdTag(adTag, false, gdpr, gdprConsent, referrer,
                            null, i, i == numTags - 1));
            }
            i++;
        }

        sb.append("</VAST>");
        return sb.toString();
    }
}
