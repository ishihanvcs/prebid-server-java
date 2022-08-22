package com.improvedigital.prebid.server.customvast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.improvedigital.prebid.server.customvast.model.CreatorContext;
import com.improvedigital.prebid.server.customvast.model.CustomVast;
import com.improvedigital.prebid.server.customvast.model.Floor;
import com.improvedigital.prebid.server.customvast.model.FloorBucket;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExtGam;
import com.improvedigital.prebid.server.utils.FluentMap;
import com.improvedigital.prebid.server.utils.JsonUtils;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
import com.improvedigital.prebid.server.utils.XmlUtils;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.ObjectUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomVastUtils {

    public static final String GAM_VAST_URL_BASE = "https://pubads.g.doubleclick.net/gampad/ads?";
    private static final Logger logger = LoggerFactory.getLogger(CustomVastUtils.class);

    private static final double IMPROVE_DIGITAL_DEAL_FLOOR = 1.5;
    private static final String PROTO_CACHE_HOST = "pbc-proto.360polaris.biz";
    private static final String PRICE_GRANULARITY_FOR_CUSTOM_VAST = "{\"precision\":2,\"ranges\":"
            + "[{\"max\":2,\"increment\":0.01},{\"max\":5,\"increment\":0.05},{\"max\":10,\"increment\":0.1},"
            + "{\"max\":40,\"increment\":0.5},{\"max\":100,\"increment\":1}]}";

    private final JsonNode customVastPriceGranularity;
    private final JsonNode defaultPriceGranularity;

    private final ExtRequest customVastExtRequest;
    private final ExtRequest prioritizedExtRequestForGVast;

    private final RequestUtils requestUtils;
    private final JsonUtils jsonUtils;
    private final JsonMerger merger;
    private final CurrencyConversionService currencyConversionService;
    private final MacroProcessor macroProcessor;
    private final GeoLocationService geoLocationService;
    private final Metrics metrics;
    private final CountryCodeMapper countryCodeMapper;
    private final String externalUrl;
    private final String gamNetworkCode;
    private final String cacheHost;

    public CustomVastUtils(
            RequestUtils requestUtils,
            JsonMerger merger,
            CurrencyConversionService currencyConversionService,
            MacroProcessor macroProcessor,
            GeoLocationService geoLocationService,
            Metrics metrics,
            CountryCodeMapper countryCodeMapper,
            String externalUrl,
            String gamNetworkCode,
            String cacheHost
    ) {
        this.requestUtils = Objects.requireNonNull(requestUtils);
        this.jsonUtils = Objects.requireNonNull(requestUtils.getJsonUtils());
        this.merger = Objects.requireNonNull(merger);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.macroProcessor = Objects.requireNonNull(macroProcessor);
        this.geoLocationService = geoLocationService;
        this.metrics = metrics;
        this.countryCodeMapper = countryCodeMapper;
        this.externalUrl = externalUrl;
        this.gamNetworkCode = gamNetworkCode;
        this.cacheHost = cacheHost;

        customVastPriceGranularity = jsonUtils.readTree(PRICE_GRANULARITY_FOR_CUSTOM_VAST);
        defaultPriceGranularity = jsonUtils.valueToTree(PriceGranularity.DEFAULT);

        this.customVastExtRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null,
                        ExtRequestPrebidCacheVastxml.of(null, true),
                        false))
                .targeting(ExtRequestTargeting.builder()
                        .includebidderkeys(true)
                        .includeformat(true)
                        .includewinners(true)
                        .pricegranularity(customVastPriceGranularity)
                        .build()
                ).build()
        );

        // Set attributes that needs to be prioritized for gvast, over attributes specified in
        // client provided bid request.
        // Currently, only ext.prebid.targeting.includebidderkeys is set to true
        this.prioritizedExtRequestForGVast = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder()
                        .includebidderkeys(true)
                        .build()
                ).build()
        );
    }

    public Bid createBidFromCustomVast(CreatorContext context, CustomVast customVast) {
        if (customVast.getAds().isEmpty()) {
            return null;
        }
        String adm = customVastToXml(customVast);
        if (StringUtils.isEmpty(adm)) {
            return null;
        }
        return Bid.builder()
                .id(UUID.randomUUID().toString())
                .impid(context.getImp().getId())
                .adm(adm)
                .price(BigDecimal.ZERO) // TODO: set price as per imp configuration
                .build();
    }

    private Future<String> resolveCountry(BidRequest bidRequest, Timeout timeout) {
        final Device currentDevice = bidRequest.getDevice();
        final Geo geo = ObjectUtil.getIfNotNull(currentDevice, Device::getGeo);
        String country = ObjectUtil.getIfNotNull(geo, Geo::getCountry);
        if (geoLocationService != null && StringUtils.isEmpty(country)) {
            String ipAddress = ObjectUtil.getIfNotNull(currentDevice, Device::getIp);
            if (StringUtils.isNotEmpty(ipAddress)) {
                return geoLocationService.lookup(ipAddress, timeout)
                        .map(geoInfo -> {
                            metrics.updateGeoLocationMetric(true);
                            if (geoInfo == null || geoInfo.getCountry() == null) {
                                return null;
                            }
                            return countryCodeMapper.mapToAlpha3(geoInfo.getCountry());
                        });
            }
        }
        return Future.succeededFuture(country);
    }

    public Future<HooksModuleContext> resolveCountryAndCreateModuleContext(BidRequest bidRequest, Timeout timeout) {
        return resolveCountry(bidRequest, timeout).map(country -> createModuleContext(bidRequest, country))
                .recover(t -> {
                    logger.error("Could not resolve country", t);
                    return Future.succeededFuture(createModuleContext(bidRequest, null));
                }
        );
    }

    public HooksModuleContext createModuleContext(BidRequest bidRequest, String alpha3Country) {
        final Map<String, ImprovedigitalPbsImpExt> impIdToPbsImpExt = new HashMap<>();
        Map<String, Floor> impIdToEffectiveFloor = new HashMap<>();
        boolean hasCustomVastVideo = false;
        boolean hasGVastVideo = false;
        for (final Imp imp : bidRequest.getImp()) {
            final String impId = imp.getId();
            final ImprovedigitalPbsImpExt pbsImpExt = jsonUtils.getImprovedigitalPbsImpExt(imp);
            if (requestUtils.isCustomVastVideo(imp, pbsImpExt)) {
                hasCustomVastVideo = true;
                if (requestUtils.hasGVastResponseType(pbsImpExt)) {
                    hasGVastVideo = true;
                }
            }
            impIdToPbsImpExt.put(impId, pbsImpExt);
            impIdToEffectiveFloor.put(impId, computeEffectiveFloor(imp, pbsImpExt, alpha3Country));
        }

        HooksModuleContext context = HooksModuleContext
                .from(impIdToPbsImpExt)
                .with(impIdToEffectiveFloor)
                .with(alpha3Country);

        updateImpsWithBidFloorInUsd(bidRequest, context::getEffectiveFloor);

        if (hasCustomVastVideo) {
            bidRequest = updateExtWithCacheSettings(bidRequest, hasGVastVideo);
        }

        return context.with(bidRequest);
    }

    private void updateImpsWithBidFloorInUsd(BidRequest bidRequest, Function<Imp, Floor> floorRetriever) {
        bidRequest.getImp().replaceAll(imp -> {
            Floor effectiveFloor = floorRetriever.apply(imp);
            BigDecimal bidFloorInUsd = null;
            String bidFloorCur = "USD";
            if (effectiveFloor != null) {
                if (StringUtils.compareIgnoreCase("USD", effectiveFloor.getBidFloorCur()) == 0) {
                    bidFloorInUsd = effectiveFloor.getBidFloor().doubleValue() <= 0.0
                            ? null
                            : effectiveFloor.getBidFloor();
                } else {
                    bidFloorInUsd = currencyConversionService.convertCurrency(
                            effectiveFloor.getBidFloor(), bidRequest,
                            effectiveFloor.getBidFloorCur(),
                            "USD"
                    );
                }
            }

            if (bidFloorInUsd == null) {
                bidFloorCur = null;
            }
            return imp.toBuilder()
                    .bidfloor(bidFloorInUsd)
                    .bidfloorcur(bidFloorCur)
                    .build();
        });
    }

    private BidRequest updateExtWithCacheSettings(BidRequest bidRequest, boolean hasGVastImp) {
        ExtRequest mergedExtRequest = merger.merge(
                bidRequest.getExt(),
                customVastExtRequest,
                ExtRequest.class
        );

        // Right now, for /auction requests, there is no way to determine, where the pricegranularity
        // is actually set from. It can be set in original bidRequest, can be set from a stored request merge,
        // or can be set with PriceGranularity.DEFAULT from Ortb2ImplicitParametersResolver.resolvePriceGranularity
        // method. So, when the value of pricegranularity will be equal to PriceGranularity.DEFAULT
        // after merging customVastExtRequest, we'll have to assume that, it is set in place of null value in request
        // and replace that value with customVastPriceGranularity in mergedExtRequest.
        if (defaultPriceGranularity.equals(
                mergedExtRequest.getPrebid().getTargeting().getPricegranularity()
        )) {
            mergedExtRequest = merger.merge(
                    ExtRequest.of(
                            ExtRequestPrebid
                                    .builder()
                                    .targeting(
                                            ExtRequestTargeting
                                                    .builder()
                                                    .pricegranularity(customVastPriceGranularity)
                                                    .build()
                                    ).build()
                    ),
                    mergedExtRequest,
                    ExtRequest.class
            );
        }

        if (hasGVastImp) {
            mergedExtRequest = merger.merge(
                    prioritizedExtRequestForGVast,
                    mergedExtRequest,
                    ExtRequest.class
            );
        }
        return bidRequest.toBuilder().ext(mergedExtRequest).build();
    }

    private static Floor computeEffectiveFloor(Imp imp, ImprovedigitalPbsImpExt pbsImpExt, String alpha3Country) {
        Floor floor = ObjectUtil.getIfNotNull(pbsImpExt, pie -> pie.getFloor(alpha3Country, null));
        BigDecimal effectiveFloorPrice = getEffectiveFloorPrice(imp, floor);
        if (effectiveFloorPrice == null) {
            return floor;
        }
        String effectiveFloorCur = getEffectiveFloorCur(imp, floor);
        return Floor.of(effectiveFloorPrice, effectiveFloorCur);
    }

    private static String getEffectiveFloorCur(Imp imp, Floor floor) {
        if (imp.getBidfloorcur() != null) {
            return imp.getBidfloorcur();
        }

        if (imp.getBidfloor() != null) {
            return "USD"; // default in ortb spec
        }

        return ObjectUtils.defaultIfNull(
                ObjectUtil.getIfNotNull(
                        floor, Floor::getBidFloorCur
                ),
                ImprovedigitalPbsImpExt.DEFAULT_BID_FLOOR_CUR
        );
    }

    private static BigDecimal getEffectiveFloorPrice(Imp imp, Floor floor) {
        return ObjectUtils.defaultIfNull(
                imp.getBidfloor(),
                ObjectUtil.getIfNotNull(
                        floor, Floor::getBidFloor
                )
        );
    }

    public String resolveGamAdUnit(
            ImprovedigitalPbsImpExtGam gamConfig, int improvePlacementId
    ) {
        gamConfig = ObjectUtils.defaultIfNull(gamConfig, ImprovedigitalPbsImpExtGam.DEFAULT);
        String adUnit = gamConfig.getAdUnit();
        String networkCode = StringUtils.defaultIfEmpty(gamConfig.getNetworkCode(), gamNetworkCode);
        if (!StringUtils.isBlank(gamConfig.getChildNetworkCode())) {
            networkCode += "," + gamConfig.getChildNetworkCode();
        }
        if (StringUtils.isBlank(adUnit)) {
            adUnit = "/" + networkCode + "/pbs/" + improvePlacementId;
        } else {
            // If ad unit begins with "/", full ad unit path is expected including the GAM network code
            // otherwise the GAM network code will be prepended
            if (adUnit.charAt(0) != '/') {
                adUnit = "/" + networkCode + "/" + adUnit;
            }
        }
        return adUnit;
    }

    public List<String> getCachedBidUrls(List<Bid> bids, boolean prioritizeImprovedigitalDeals) {
        final List<String> vastUrls = new ArrayList<>();

        if (bids == null || bids.isEmpty()) {
            return vastUrls;
        }

        for (Bid bid : bids) {
            final String vastUrl = JsonUtils.getStringAt(bid.getExt(), "/prebid/cache/vastXml/url");
            if (StringUtils.isBlank(vastUrl)) {
                continue;
            }

            boolean isImproveDeal = prioritizeImprovedigitalDeals
                    && !bid.getExt().at("/prebid/targeting/hb_deal_improvedigit").isMissingNode();
            if (isImproveDeal && vastUrls.size() > 0) {
                vastUrls.add(0, vastUrl);
            } else {
                vastUrls.add(vastUrl);
            }
        }

        return vastUrls;
    }

    public String formatPrebidGamKeyValueString(
            List<Bid> bids, boolean prioritizeImproveDeals
    ) {

        StringBuilder targeting = new StringBuilder();
        boolean improvedigitalDealWon = false;

        if (bids == null || bids.isEmpty()) {
            return StringUtils.EMPTY;
        }

        for (Bid bid : bids) {
            if (bid.getExt() == null) {
                continue;
            }

            if (StringUtils.isBlank(
                    JsonUtils.getStringAt(bid.getExt(), "/prebid/cache/vastXml/cacheId")
            )) {
                // Discard the bid if VastXML couldn't be cached
                continue;
            }

            StringBuilder bidderKeyValues = new StringBuilder();
            JsonNode targetingKvs = bid.getExt().at("/prebid/targeting");
            boolean isImproveDeal = false;
            double price = 0;

            for (Iterator<String> it = targetingKvs.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                bidderKeyValues.append(key).append("=").append(targetingKvs.get(key).asText()).append("&");

                if (key.equals("hb_deal_improvedigit")) {
                    isImproveDeal = true;
                } else if (key.startsWith("hb_pb_")) {
                    price = targetingKvs.get(key).asDouble();
                }
            }

            if (prioritizeImproveDeals && isImproveDeal && price >= IMPROVE_DIGITAL_DEAL_FLOOR) {
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

        if (targeting.length() > 0) {
            // Target ImproveDigital Prebid cache line items (creatives) in GAM
            targeting.append("pbct=").append(this.resolvePbct());
            // Disable Google AdX/AdSense and pricing rules for ImproveDigital deals
            if (improvedigitalDealWon) {
                targeting.append("&tnl_wog=1&nf=1");
            }
        }
        return targeting.toString();
    }

    public String getCustParams(CreatorContext context, Stream<String> targetingStream) {
        String targetingString = targetingStream == null
                ? StringUtils.EMPTY
                : targetingStream
                    .filter(s -> !StringUtils.isBlank(s))
                    .collect(Collectors.joining("&"));

        if (context.getBidfloor() > 0 && !targetingString.contains("fp=")) {
            String fp = resolveGamFloorPrice(
                    context.getBidfloor(), context.getBidfloorcur(), context.getBidRequest()
            );
            targetingString += (targetingString.length() > 0 ? "&" : "") + "fp=" + fp;
        }
        if (!targetingString.contains("tnl_asset_id=")) {
            targetingString += (targetingString.length() > 0 ? "&" : "") + "tnl_asset_id=prebidserver";
        }
        if (!StringUtils.isBlank(targetingString)) {
            targetingString = targetingString.replace("pbct=2", "pbct=" + this.resolvePbct()); // HACK - fix
        }
        return targetingString;
    }

    public String resolveGamFloorPrice(double bidFloor, String bidFloorCur, BidRequest bidRequest) {
        bidFloorCur = StringUtils.defaultIfBlank(bidFloorCur, "USD");
        if (bidFloorCur.compareToIgnoreCase("EUR") != 0) {
            bidFloor = currencyConversionService.convertCurrency(
                    BigDecimal.valueOf(bidFloor), bidRequest, bidFloorCur, "EUR"
            ).doubleValue();
        }

        return FloorBucket.resolveGamFloorPrice(bidFloor);
    }

    public String getRedirect(String bidder, String gdpr, String gdprConsent,
                               String userIdParamName) {
        // TODO add us_privacy
        return externalUrl + "/setuid?bidder=" + bidder + "&gdpr=" + gdpr + "&gdpr_consent=" + gdprConsent
                + "&us_privacy=&uid=" + userIdParamName;
    }

    public String replaceMacros(String tag, CreatorContext context) {
        final Map<String, String> macroValues =
                FluentMap.<String, String>create()
                        .put("gdpr", context.getGdpr())
                        .put("gdpr_consent", context.getGdprConsent())
                        .put("timestamp", Long.toString(System.currentTimeMillis()))
                        .putIfNotNull("referrer", context.getEncodedReferrer())
                        .result();
        String expanded = "";
        try {
            expanded = macroProcessor.process(tag, macroValues, true);
        } catch (Exception ignored) { }
        return expanded;
    }

    private String resolvePbct() {
        return PROTO_CACHE_HOST.endsWith(cacheHost) ? "3" : "1";
    }

    public String buildGamVastTagUrl(
            CreatorContext context,
            Stream<String> targetingStream) {
        FluentMap<String, Object> gamTagParams = FluentMap.<String, Object>create()
                .put("gdfp_req", "1")
                .put("env", "vp")
                .put("unviewed_position_start", "1")
                .put("correlator", System.currentTimeMillis())
                .put("iu", resolveGamAdUnit(
                        context.getGamConfig(),
                        requestUtils.getImprovePlacementId(context.getImp())
                ))
                .put("output", resolveGamOutputFromOrtb(context.getProtocols()))

                // Although we might receive width and height as gvast params, we don't want to send the real size
                // to GAM for 2 reasons:
                // 1) Prebid line item targeting breaks when random sizes are used.
                // 2) GAM test showed a drop in demand with random sizes
                .put("sz", "640x480|640x360")

                .putIfNotNull("description_url", context.getReferrer())
                .putIfNotBlank("cust_params", getCustParams(context, targetingStream));

        // Add additional params for apps running without IMA SDK
        // https://support.google.com/admanager/answer/10660756?hl=en&ref_topic=10684636
        if (context.isApp()) {
            // gamTag.append("&sdki=44d&sdk_apis=2%2C8&sdkv=h.3.460.0");
            gamTagParams
                    .putIfNotBlank("gdpr", context.getGdpr())
                    .putIf("gdpr_consent",
                            !StringUtils.isBlank(context.getGdpr())
                                    && !StringUtils.isBlank(context.getGdprConsent()), context.getGdprConsent())
                    .put("msid", context.getBundle())
                    .put("an", context.getBundle())
                    .put("url", context.getBundle() + ".adsenseformobileapps.com/")
                    .putIfNotBlank("rdid", context.getIfa())
                    .putIfNotNull("is_lat", context.getLmt())
                    .putIfNotNull("idtype", context.getGamIdType());
        } else {
            gamTagParams.putIfNotNull("url", context.getReferrer());
        }

        return GAM_VAST_URL_BASE + gamTagParams.queryString();
    }

    public static String resolveGamOutputFromOrtb(List<Integer> protocols) {
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

    public static String customVastToXml(CustomVast gVastResponse) {
        try {
            return XmlUtils.serialize(gVastResponse);
        } catch (JsonProcessingException e) {
            logger.error("Could not serialize CustomVast to xml", e);
        }
        return null;
    }

    public static CustomVast customVastFromXml(String xml) throws IOException {
        return XmlUtils.deserialize(xml, CustomVast.class);
    }
}
