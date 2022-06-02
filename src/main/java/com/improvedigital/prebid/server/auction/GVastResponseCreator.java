package com.improvedigital.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.auction.model.Floor;
import com.improvedigital.prebid.server.auction.model.GVastParams;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.auction.model.ImprovedigitalPbsImpExtGam;
import com.improvedigital.prebid.server.auction.requestfactory.GVastContext;
import com.improvedigital.prebid.server.utils.FluentMap;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
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
        routingContext.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER,
                AsciiString.cached("application/xml"));

        String debugInfo = "";
        if (gVastParams.isDebug()) {
            try {
                final XmlMapper xmlMapper = new XmlMapper();
                debugInfo = xmlMapper.writeValueAsString(gVastContext.getBidResponse().getExt());
            } catch (JsonProcessingException ignored) {
            }
        }

        final ImprovedigitalPbsImpExt config = gVastContext.getImprovedigitalPbsImpExt();

        final List<String> waterfall = config.getWaterfall(gVastContext.getAuctionContext().getGeoInfo());
        // Response without GAM
        if (!waterfall.isEmpty() && waterfall.contains("no_gam")) {
            return buildVastXmlResponseWithoutGam(gVastContext, prioritizeImprovedigitalDeals, waterfall, debugInfo);
        }

        // Response with GAM
        return buildVastXmlResponseWithGam(
                gVastContext,
                prioritizeImprovedigitalDeals,
                debugInfo
        );
    }

    private List<String> getCachedBidUrls(BidResponse bidResponse, boolean prioritizeImprovedigitalDeals) {
        final List<String> vastUrls = new ArrayList<>();

        if (bidResponse.getSeatbid().isEmpty()) {
            return vastUrls;
        }

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid.getBid().isEmpty()) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                if (bid.getExt() == null) {
                    continue;
                }
                final JsonNode vastUrlNode = bid.getExt().at("/prebid/cache/vastXml/url");
                if (vastUrlNode.isMissingNode()) {
                    continue;
                }

                final String vastUrl = vastUrlNode.textValue();
                JsonNode targetingKvs = bid.getExt().at("/prebid/targeting");

                boolean isImproveDeal = false;
                if (prioritizeImprovedigitalDeals && vastUrls.size() > 0
                        && seatBid.getSeat().equals("improvedigital")) {
                    for (Iterator<String> it = targetingKvs.fieldNames(); it.hasNext(); ) {
                        String key = it.next();
                        if (key.equals("hb_deal_improvedigit")) {
                            isImproveDeal = true;
                            break;
                        }
                    }
                    if (isImproveDeal) {
                        vastUrls.add(0, vastUrl);
                    }
                }
                if (!isImproveDeal) {
                    vastUrls.add(vastUrl);
                }
            }
        }
        return vastUrls;
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
            // Target ImproveDigital Prebid cache line items (creatives) in GAM
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
                        : ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY,
                () -> ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY
        );
    }

    private double getBidFloor(ImprovedigitalPbsImpExt config, GeoInfo geoInfo) {
        final double defaultResult = 0.0;
        final Map<String, Floor> floors = config.getFloors();
        if (floors.isEmpty()) {
            return defaultResult;
        }
        final String countryCode = resolveCountryCode(floors, geoInfo);
        // logger.info("countryCode = " + countryCode);

        if (floors.containsKey(countryCode)) {
            return floors.get(countryCode).getBidFloor();
        }

        return defaultResult;
    }

    private String resolveGamOutputFromOrtb(List<Integer> protocols) {
        if (protocols != null) {
            if (protocols.contains(7)) {
                return "xml_vast4";
            } else if (protocols.contains(3)) {
                return "xml_vast3";
            } else if (protocols.contains(2)) {
                return "xml_vast2";
            }
        }
        return "vast";
    }

    private String getGamAdUnit(GVastParams gVastParams, ImprovedigitalPbsImpExt config) {
        ImprovedigitalPbsImpExtGam gamConfig = ObjectUtils.defaultIfNull(config.getImprovedigitalPbsImpExtGam(),
                ImprovedigitalPbsImpExtGam.of(null, null, null));
        String adUnit = gamConfig.getAdUnit();
        String networkCode = ObjectUtils.defaultIfNull(gamConfig.getNetworkCode(), gamNetworkCode);
        if (!StringUtils.isBlank(gamConfig.getChildNetworkCode())) {
            networkCode += "," + gamConfig.getChildNetworkCode();
        }
        if (StringUtils.isBlank(adUnit)) {
            adUnit = "/" + networkCode + "/pbs/" + gVastParams.getImpId();
        } else {
            // If ad unit begins with "/", full ad unit path is expected including the GAM network code
            // otherwise the GAM network code will be prepended
            if (adUnit.charAt(0) != '/') {
                adUnit = "/" + networkCode + "/" + adUnit;
            }
        }
        return adUnit;
    }

    private boolean isApp(GVastParams gVastParams) {
        return !StringUtils.isBlank(gVastParams.getBundle());
    }

    // Resolve GAM id type from operating system. See: https://support.google.com/admanager/answer/6238701
    private String resolveGamIdType(String os) {
        if (StringUtils.isBlank(os)) {
            return null;
        }
        switch (os.toLowerCase()) {
            case "ios":
                return "idfa";
            case "android":
                return "adid";
            default:
                return null;
        }
    }

    private String buildGamVastTagUrl(
            String adUnit,
            double bidFloor,
            GVastParams gVastParams,
            String targeting) {
        final String referrer = gVastParams.getReferrer();
        FluentMap<String, Object> gamTagParams = FluentMap.<String, Object>create()
                .put("gdfp_req", "1")
                .put("env", "vp")
                .put("unviewed_position_start", "1")
                .put("correlator", System.currentTimeMillis())
                .put("iu", adUnit)
                .put("output", resolveGamOutputFromOrtb(gVastParams.getProtocols()))

                // Although we might receive width and height as gvast params, we don't want to send the real size
                // to GAM for 2 reasons:
                // 1) Prebid line item targeting breaks when random sizes are used.
                // 2) GAM test showed a drop in demand with random sizes
                .put("sz", "640x480|640x360")

                .putIfNotNull("description_url", referrer)
                .putIfNotBlank("cust_params", getCustParams(bidFloor, targeting));

        // Add additional params for apps running without IMA SDK
        // https://support.google.com/admanager/answer/10660756?hl=en&ref_topic=10684636
        if (isApp(gVastParams)) {
            // gamTag.append("&sdki=44d&sdk_apis=2%2C8&sdkv=h.3.460.0");
            final String gdpr = gVastParams.getGdpr();
            final String gdprConsent = gVastParams.getGdprConsentString();
            final String bundleId = ObjectUtils.defaultIfNull(gVastParams.getBundle(), "");
            gamTagParams
                    .putIfNotBlank("gdpr", gdpr)
                    .putIf("gdpr_consent",
                            !StringUtils.isBlank(gdpr) && !StringUtils.isBlank(gdprConsent), gdprConsent)
                    .put("msid", bundleId)
                    .put("an", bundleId)
                    .put("url", bundleId + ".adsenseformobileapps.com/")
                    .putIfNotBlank("rdid", gVastParams.getIfa())
                    .putIfNotNull("is_lat", gVastParams.getLmt())
                    .putIfNotNull("idtype", resolveGamIdType(gVastParams.getOs()));
        } else {
            gamTagParams.putIfNotNull("url", referrer);
        }

        return "https://pubads.g.doubleclick.net/gampad/ads?" + gamTagParams.queryString();
    }

    private String getCustParams(double bidFloor, String targeting) {
        String targetingString = "";
        if (!StringUtils.isBlank(targeting)) {
            targetingString += targeting;
        }
        if (bidFloor > 0 && !targetingString.contains("fp=")) {
            targetingString += (targetingString.length() > 0 ? "&" : "") + "fp=" + bidFloor;
        }
        if (!targetingString.contains("tnl_asset_id=")) {
            targetingString += (targetingString.length() > 0 ? "&" : "") + "tnl_asset_id=prebidserver";
        }
        if (!StringUtils.isBlank(targetingString)) {
            targetingString = targetingString.replace("pbct=2", "pbct=" + pbct); // HACK - fix
        }
        return targetingString;
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
        } catch (Exception ignored) {
        }
        return expanded;
    }

    private String buildVastAdTag(String tagUrl, boolean addUserSyncs, GVastParams gVastParams,
                                  String debugInfo, int adIndex, boolean isLastAd) {
        final String gdprConsent = gVastParams.getGdprConsentString();
        final String referrer = gVastParams.getReferrer();
        final String gdpr = gVastParams.getGdpr();
        StringBuilder sb = new StringBuilder();
        final boolean singleAd = adIndex == 0 && isLastAd;
        sb.append(String.format("<Ad id=\"%d\"><Wrapper", adIndex))
                .append(isLastAd ? ">" : " fallbackOnNoAd=\"true\">")
                .append("<AdSystem>ImproveDigital PBS</AdSystem>")
                .append("<VASTAdTagURI><![CDATA[")
                .append(replaceMacros(tagUrl, gdpr, gdprConsent, referrer))
                .append("]]></VASTAdTagURI><Creatives></Creatives>");

        if (addUserSyncs && !isApp(gVastParams)) {
            // Inject sync pixels as imp pixels.
            // Only inject for web, not app as apps can't do cookie syncing and rely on device id (IFA) instead
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

    private String buildVastXmlResponseWithGam(GVastContext gVastContext,
                                               boolean prioritizeImprovedigitalDeals,
                                               String hbAuctionDebugInfo) {

        final String gamPrebidTargeting = formatPrebidGamKeyValueString(gVastContext.getBidResponse(),
                prioritizeImprovedigitalDeals);
        final boolean isImprovedigitalDeal = prioritizeImprovedigitalDeals
                && gamPrebidTargeting.contains("hb_deal_improvedigit");
        final GVastParams gVastParams = gVastContext.getGVastParams();
        final String custParams = gVastParams.getCustParams().toString();
        final ImprovedigitalPbsImpExt config = gVastContext.getImprovedigitalPbsImpExt();
        final String adUnit = getGamAdUnit(gVastParams, config);
        final GeoInfo geoInfo = gVastContext.getAuctionContext().getGeoInfo();
        final double bidFloor = config.getBidFloor(geoInfo);
        final List<String> waterfall = config.getWaterfall(geoInfo);
        final String categoryTargeting;
        final List<String> categories = gVastParams.getCat();

        if (categories != null && categories.size() > 0) {
            categoryTargeting = "iab_cat=" + String.join(",", gVastParams.getCat());
        } else {
            categoryTargeting = null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"2.0\">");

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
        final int numTags = waterfall.size();
        int i = 0;
        for (String adTag : waterfall) {
            switch (adTag) {
                // GAM + HB bids
                case "gam":
                case "gam_improve_deal":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(
                                    adUnit, bidFloor, gVastParams,
                                    buildTargetingString(Stream.of(gamPrebidTargeting, custParams, categoryTargeting))
                            ),
                            true, gVastParams, hbAuctionDebugInfo, i, i == numTags - 1));
                    break;
                case "gam_no_hb":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(adUnit, bidFloor, gVastParams,
                                    buildTargetingString(Stream.of(custParams, categoryTargeting))
                            ),
                            true, gVastParams, null, i, i == numTags - 1));
                    break;
                // First look is for all low-fill campaigns that should get first look before allowing AdX to monetise
                // fl=1 -> first look targeting KV
                // tnl_wog=1 -> disable AdX & AdSense
                case "gam_first_look":
                    sb.append(buildVastAdTag(
                            buildGamVastTagUrl(adUnit, bidFloor, gVastParams,
                                    buildTargetingString(Stream.of(custParams, categoryTargeting, "fl=1&tnl_wog=1"))
                            ),
                            true, gVastParams, null, i, i == numTags - 1));
                    break;
                default:
                    sb.append(buildVastAdTag(adTag, false, gVastParams,
                            null, i, i == numTags - 1));
            }
            i++;
        }

        sb.append("</VAST>");
        return sb.toString();
    }

    private String buildVastXmlResponseWithoutGam(GVastContext gVastContext,
                                               boolean prioritizeImprovedigitalDeals,
                                               List<String> waterfall,
                                               String hbAuctionDebugInfo) {
        final List<String> sspVastUrls = getCachedBidUrls(gVastContext.getBidResponse(), prioritizeImprovedigitalDeals);
        final GVastParams gVastParams = gVastContext.getGVastParams();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"2.0\">");

        List<String> vastTags = sspVastUrls;

        if (waterfall.size() > 1) {
            vastTags = new ArrayList<>();
            for (String adTag : waterfall) {
                if (adTag.equals("no_gam")) {
                    vastTags.addAll(sspVastUrls);
                } else {
                    vastTags.add(adTag);
                }
            }
        }

        // If there's no bid/tag but the debug mode is enabled, respond with a test domain and debug info
        if (gVastParams.isDebug() && vastTags.isEmpty()) {
            vastTags.add("https://example.com");
        }

        for (int i = 0; i < vastTags.size(); i++) {
            sb.append(buildVastAdTag(vastTags.get(i), true, gVastParams,
                    (i == 0) ? hbAuctionDebugInfo : null, i, i == vastTags.size() - 1));
        }

        sb.append("</VAST>");
        return sb.toString();
    }
}