package com.improvedigital.prebid.server.it;

import com.iab.openrtb.request.Request;
import com.improvedigital.prebid.server.customvast.handler.GVastHandler;
import com.improvedigital.prebid.server.utils.TestUtils;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.it.util.BidCacheRequestPattern;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.HttpUtil;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "auction.generate-source-tid=true",
        "admin.port=18060",
        "http.port=18080",
})
@RunWith(SpringRunner.class)
public class ImprovedigitalGvastTest extends ImprovedigitalIntegrationTest {

    /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
    private static final String VALID_PLACEMENT_ID = "20220325";

    @Test
    public void gvastHasProperQueryParamsInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse();

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertGamGeneralParameters(vastQueryParams, "20220325");
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("cust_params").get(0)).isEqualTo("tnl_asset_id=prebidserver");

        assertNoDebug(response.asString(), "0");
    }

    @Test
    public void testGvastEndpointWithDebugParameter() throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("debug", "1")
        );

        assertThat(getVastTagUri(response.asString(), "0"))
                .startsWith("https://pubads.g.doubleclick.net/gampad/ads");

        assertThat(getXmlValue(getDebug(response.asString(), "0"), "//resolvedrequest/id"))
                .isNotEmpty();
    }

    @Test
    public void gvastHasProperQueryParamsInVastTagUriForAppRequest()
            throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("bundle", "com.improvedigital.ittests")
                .queryParam("storeurl", "http://pbs.improvedigital.com")
                .queryParam("gdpr", "1")
                .queryParam("gdpr_consent", "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA")
                .queryParam("ifa", "V0Z851OEHUF5MYVVFQVG8FLSASLFIODF")
                .queryParam("lmt", "0")
                .queryParam("os", "Android")
        );

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(
                vastQueryParams,
                "20220325",
                "com.improvedigital.ittests.adsenseformobileapps.com/"
        );

        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("cust_params").get(0)).isEqualTo("tnl_asset_id=prebidserver");

        assertThat(vastQueryParams.get("gdpr").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdpr").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("gdpr_consent").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdpr_consent").get(0)).isEqualTo("BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA");

        assertThat(vastQueryParams.get("msid").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("msid").get(0)).isEqualTo("com.improvedigital.ittests");

        assertThat(vastQueryParams.get("an").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("an").get(0)).isEqualTo("com.improvedigital.ittests");

        assertThat(vastQueryParams.get("rdid").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("rdid").get(0)).isEqualTo("V0Z851OEHUF5MYVVFQVG8FLSASLFIODF");

        assertThat(vastQueryParams.get("is_lat").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("is_lat").get(0)).isEqualTo("0");

        assertThat(vastQueryParams.get("idtype").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("idtype").get(0)).isEqualTo("adid");
    }

    @Test
    public void gvastHasProperPbctInVastTagUri()
            throws XPathExpressionException, MalformedURLException {
        // Case 1: pbct=2 will be replace by pbct=1
        Response response = getGvastResponse(spec -> spec
                .queryParam("cust_params", "pbct=2")
        );
        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertQuerySingleValue(getNonEmptyCustomParams(vastAdTagUri).get("pbct"), "1");

        // Case 2: pbct=3 will be as is.
        Response response2 = getGvastResponse(spec -> spec
                .queryParam("cust_params", "pbct=3")
        );
        String vastAdTagUri2 = getVastTagUri(response2.asString(), "0");
        assertQuerySingleValue(getNonEmptyCustomParams(vastAdTagUri2).get("pbct"), "3");
    }

    @Test
    public void gvastDoesNotHaveUnnecessaryQueryParamsInVastTagUriForAppRequest()
            throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("bundle", "com.improvedigital.ittests")
                .queryParam("os", "UNKNOWN")
        );

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertThat(vastQueryParams.get("gdpr_consent")).isNull();
        assertThat(vastQueryParams.get("rdid")).isNull();
        assertThat(vastQueryParams.get("is_lat")).isNull();
        assertThat(vastQueryParams.get("idtype")).isNull();
    }

    @Test
    public void gvastReturnsDefaultTnlAssetIdInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("cust_params", "abc=def")
        );

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").get(0)).isEqualTo("prebidserver");
    }

    @Test
    public void gvastReturnsRequestProvidedTnlAssetIdInVastTagUri()
            throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("cust_params", "abc=def&tnl_asset_id=custom_tnl_123")
        );

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(custParams.get("tnl_asset_id").get(0)).isEqualTo("custom_tnl_123");
    }

    @Test
    public void gvastReturnsRequestProvidedMultipleTnlAssetIdsInVastTagUri()
            throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse(spec -> spec
                .queryParam("cust_params", "abc=def&tnl_asset_id=custom_2_tnl,custom_1_tnl")
        );

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("tnl_asset_id")).isNotNull();
        assertThat(custParams.get("tnl_asset_id").size()).isEqualTo(1);
        assertThat(
                custParams.get("tnl_asset_id").get(0).contains("custom_1_tnl,custom_2_tnl")
                        || custParams.get("tnl_asset_id").get(0).contains("custom_2_tnl,custom_1_tnl")
        ).isTrue();
    }

    @Test
    public void auctionEndpointReturnsVastResponse() throws XPathExpressionException, IOException, JSONException {
        JSONObject responseJson = doVastAuctionRequest(
                getVastXmlInline("ad_1", true), "1.25", "20220608", 20220608
        );
        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we had on creative.
        String mediaUrl = getXmlValue(adm, "/VAST/Ad[@id='ad_1']/InLine/Creatives"
                + "/Creative[@AdID='ad_1']/Linear/MediaFiles/MediaFile[1]");
        assertThat(mediaUrl.trim()).isEqualTo("https://media.pbs.improvedigital.com/ad_1.mp4");
    }

    @Test
    public void testCustomVastResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("ad_1", true);
        String cacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .storedImpId("20220608")
                .improvePlacementId(20220608)
                .improveAdm(vastXml)
                .improvePrice("1.08")
                .improveCacheId(cacheId)
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");

        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri, cacheId, "20220608", "1.08"
        );
        assertCachedContentFromCacheId(cacheId, getVastXmlToCache(
                vastXml, "improvedigital", "1.08", 20220608
        ));
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertNoExtensions(adm, "0");
    }

    @Test
    public void testCustomVastResponseWithMultipleWaterfallConfig()
            throws XPathExpressionException, IOException, JSONException {

        String vastXml = getVastXmlInline("ad_1", true);
        String cacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .storedImpId("20220608")
                .improvePlacementId(20220608)
                .improveAdm(vastXml)
                .improvePrice("1.09")
                .improveCacheId(cacheId)
                .defaultWaterfalls(List.of(
                        "gam_first_look",
                        "gam",
                        "gam_first_look",
                        "https://my.customvast.xml"
                ))
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag = gam_first_look
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamFirstLookUrl(vastAdTagUri1, "20220608");
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertExtensions(adm, "0", 0);

        // 2nd tag = gam
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri2, cacheId, "20220608", "1.09"
        );
        assertCachedContentFromCacheId(cacheId, getVastXmlToCache(
                vastXml, "improvedigital", "1.09", 20220608
        ));
        assertSSPSyncPixels(adm, "1");
        assertNoCreative(adm, "1");
        assertExtensions(adm, "1", 1);

        // 3rd tag = gam_first_look again
        String vastAdTagUri3 = getVastTagUri(adm, "2");
        assertGamFirstLookUrl(vastAdTagUri3, "20220608");
        assertSSPSyncPixels(adm, "2");
        assertNoCreative(adm, "2");
        assertExtensions(adm, "2", 2);

        // 4th tag = custom
        String vastAdTagUri4 = getVastTagUri(adm, "3");
        assertThat(vastAdTagUri4.trim()).isEqualTo("https://my.customvast.xml");
        assertNoSSPSyncPixels(adm, "3");
        assertNoCreative(adm, "3");
        assertExtensions(adm, "3", 3);

        // Last ad will not have fallbackOnNoAd=true. All the others will.
        assertFallbackOnNoAd(adm, true, "0");
        assertFallbackOnNoAd(adm, true, "1");
        assertFallbackOnNoAd(adm, true, "2");
        assertFallbackOnNoAd(adm, false, "3");
    }

    @Test
    public void testCustomVastResponseWithMacroReplacement()
            throws XPathExpressionException, IOException, JSONException {

        String vastXml = getVastXmlInline("ad_1", true);
        String cacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .storedImpId("20220608")
                .improvePlacementId(20220608)
                .improveAdm(vastXml)
                .improvePrice("1.11")
                .improveCacheId(cacheId)
                .gdprConsent("BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA")
                .defaultWaterfalls(List.of(
                        "gam", "https://my.customvast.xml"
                                + "?gdpr={{gdpr}}"
                                + "&gdpr_consent={{gdpr_consent}}"
                                + "&referrer={{referrer}}"
                                + "&t={{timestamp}}"
                ))
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri1, cacheId, "20220608", "1.11"
        );

        // 2nd tag.
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        Map<String, List<String>> customUrlParams = TestUtils.splitQuery(new URL(vastAdTagUri2).getQuery());
        assertQuerySingleValue(customUrlParams.get("gdpr"), "0");
        assertQuerySingleValue(customUrlParams.get("gdpr_consent"), "BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA");
        assertQuerySingleValue(customUrlParams.get("referrer"), "http://pbs.improvedigital.com");
        assertThat(Long.parseLong(customUrlParams.get("t").get(0)) > (System.currentTimeMillis() - 5 * 60 * 1000L));
        assertThat(Long.parseLong(customUrlParams.get("t").get(0)) < System.currentTimeMillis());
    }

    @Test
    public void testCustomVastResponseWithMultipleBidder()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 2nd bid and generic's 1st bid will be picked and cached.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml2, "improvedigital", "1.75", 20220617);
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml1, "generic", "1.95", 20220617);
        String improveCacheId = getCacheIdRandom();
        String genericCacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestToMultipleBidder(
                GvastMultipleBidderAuctionTestParam.builder()
                        .responseType("gvast")
                        .defaultWaterfalls(List.of("gam_first_look", "gam"))
                        .improvePlacementId(20220617)
                        .improveAdm1(improveVastXml1)
                        .improvePrice1("1.65")
                        .improveAdm2(improveVastXml2)
                        .improvePrice2("1.75")
                        .improveCacheId(improveCacheId)
                        .improveReturnsDeal(false)
                        .genericAdm1(genericVastXml1)
                        .genericPrice1("1.95")
                        .genericAdm2(genericVastXml2)
                        .genericPrice2("1.85")
                        .genericCacheId(genericCacheId)
                        .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag = gam_first_look
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamFirstLookUrl(vastAdTagUri1, "20220617");
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertExtensions(adm, "0", 0);

        // 2nd tag = gam. Improve lost here.
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        assertGamUrlWithImprovedigitalLostToGenericBidder(
                vastAdTagUri2,
                "20220617",
                improveCacheId,
                genericCacheId,
                "1.75",
                "1.95"
        );
        assertSSPSyncPixels(adm, "1");
        assertNoCreative(adm, "1");
        assertExtensions(adm, "1", 1);

        // Note: Because of WireMock's scenario implementation, we must call /cache in this order.
        assertCachedContentFromCacheId(improveCacheId, improveVastXmlToCache);
        assertCachedContentFromCacheId(genericCacheId, genericVastXmlToCache);
    }

    @Test
    public void testCustomVastResponseWithMultipleBidderAndDeal()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 2nd bid and generic's 1st bid will be picked and cached.
        String improveCacheId = getCacheIdRandom();
        String genericCacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestToMultipleBidder(
                GvastMultipleBidderAuctionTestParam.builder()
                        .responseType("gvast")
                        .defaultWaterfalls(List.of("gam_first_look", "gam"))
                        .improvePlacementId(20220617)
                        .improveAdm1(improveVastXml1)
                        .improvePrice1("1.65")
                        .improveAdm2(improveVastXml2)
                        .improvePrice2("1.75")
                        .improveCacheId(improveCacheId)
                        .improveReturnsDeal(true)
                        .genericAdm1(genericVastXml1)
                        .genericPrice1("1.95")
                        .genericAdm2(genericVastXml2)
                        .genericPrice2("1.85")
                        .genericCacheId(genericCacheId)
                        .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag = whatever was in the request, gam_improve_deal will be the first.
        // gam_improve_deal and gam are equal in behavior.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamUrlWithImprovedigitalAsSingleBidder(vastAdTagUri1, improveCacheId, "20220617", "1.75", true);
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertExtensions(adm, "0", 0);

        // 2nd tag = gam_first_look
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        assertGamFirstLookUrl(vastAdTagUri2, "20220617");
        assertSSPSyncPixels(adm, "1");
        assertNoCreative(adm, "1");
        assertExtensions(adm, "1", 1);

        // 3rd tag = gam will be gam_no_hb
        String vastAdTagUri3 = getVastTagUri(adm, "2");
        assertGamNoHbUrl(vastAdTagUri3, "20220617");
        assertSSPSyncPixels(adm, "2");
        assertNoCreative(adm, "2");
        assertExtensions(adm, "2", 2);
    }

    @Test
    public void testCustomVastForWaterfallResponseType() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("ad_1", true);
        String cacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("waterfall")
                .storedImpId("20220608")
                .improvePlacementId(20220608)
                .improveAdm(vastXml)
                .improvePrice("1.13")
                .improveCacheId(cacheId)
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");
        assertThat(vastAdTagUri.trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + cacheId);

        // Hit the cache.
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=" + cacheId, getVastXmlToCache(
                vastXml, "improvedigital", "1.13", 20220608
        ));

        assertSSPSyncPixels(adm, "0");
    }

    @Test
    public void testCustomVastForWaterfallResponseTypeWithMultipleBidderAndDeal()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 1st bid and generic's 2nd bid will be picked and cached.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml1, "improvedigital", "1.17", 20220615);
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml2, "generic", "1.05", 20220615);
        String improveCacheId = getCacheIdRandom();
        String genericCacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestToMultipleBidder(
                GvastMultipleBidderAuctionTestParam.builder()
                        .responseType("waterfall")
                        .improvePlacementId(20220615)
                        .improveAdm1(improveVastXml1)
                        .improvePrice1("1.17")
                        .improveAdm2(improveVastXml2)
                        .improvePrice2("1.11")
                        .improveCacheId(improveCacheId)
                        .improveReturnsDeal(true)
                        .genericAdm1(genericVastXml1)
                        .genericPrice1("1.01")
                        .genericAdm2(genericVastXml2)
                        .genericPrice2("1.05")
                        .genericCacheId(genericCacheId)
                        .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag. It should be improvedigital's because of hb_deal_improvedigital.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertThat(vastAdTagUri1.trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + improveCacheId);
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertExtensions(adm, "0", 0);

        // 2nd tag.
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        assertThat(vastAdTagUri2.trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + genericCacheId);
        assertSSPSyncPixels(adm, "1");
        assertNoCreative(adm, "1");
        assertExtensions(adm, "1", 1);

        // Note: Because of WireMock's scenario implementation, we must call /cache in this order.
        assertCachedContent(vastAdTagUri1.trim(), improveVastXmlToCache);
        assertCachedContent(vastAdTagUri2.trim(), genericVastXmlToCache);
    }

    @Test
    public void testCustomVastForWaterfallResponseTypeWhenSSPReturnsNoBid() throws JSONException {
        JSONObject responseJson = doCustomVastRequestWhenSSPReturnsNoBid(
                "waterfall", UUID.randomUUID().toString(), 20220629, 0
        );
        assertBidCount(responseJson, 0);
        assertCurrency(responseJson, "USD");
    }

    @Test
    public void testCustomVastForWaterfallResponseTypeWhenSSPReturnsNoBidAndTestIsTrue()
            throws JSONException, XPathExpressionException {
        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = doCustomVastRequestWhenSSPReturnsNoBid(
                "waterfall", uniqueId, 20220629, 1
        );

        assertBidCount(responseJson, 1, 1);
        assertCurrency(responseJson, "USD");
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");

        String adm = getAdm(responseJson, 0, 0);
        assertThat(getVastTagUri(adm, "0")).isEqualTo("https://example.com");
        assertSSPSyncPixels(adm, "0");
        assertExtensionDebug(adm, "0", "improvedigital", uniqueId);
    }

    @Test
    public void testGvastResponseWithAdUnit()
            throws XPathExpressionException, IOException, JSONException {

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/" + GAM_NETWORK_CODE + "/pbs/20220618");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "", "childNetworkCode", "DEF")
        )).isEqualTo("/" + GAM_NETWORK_CODE + ",DEF/pbs/20220618");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "ABC", "childNetworkCode", "")
        )).isEqualTo("/ABC/pbs/20220618");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/ABC,DEF/pbs/20220618");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "/XYZ", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/XYZ");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "/XYZ", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/XYZ");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/" + GAM_NETWORK_CODE + "/XYZ");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "", "childNetworkCode", "DEF")
        )).isEqualTo("/" + GAM_NETWORK_CODE + ",DEF/XYZ");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "ABC", "childNetworkCode", "")
        )).isEqualTo("/ABC/XYZ");

        assertThat(doCustomVastRequestAndGetAdUnitParam(
                getVastXmlInline("ad_1", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/ABC,DEF/XYZ");
    }

    private String doCustomVastRequestAndGetAdUnitParam(
            String improveAdm, String improvePrice, int placementId, Map<String, String> gamParams
    ) throws IOException, JSONException, XPathExpressionException {

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .gamParams(gamParams)
                .improvePlacementId(placementId)
                .improveAdm(improveAdm)
                .improvePrice(improvePrice)
                .improveCacheId(getCacheIdRandom())
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        return vastQueryParams.get("iu").get(0);
    }

    @Test
    public void testGvastResponseWithVideoProtocol()
            throws XPathExpressionException, IOException, JSONException {

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                List.of(2, 3, 7)
        )).isEqualTo("xml_vast4");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                List.of(2, 3)
        )).isEqualTo("xml_vast3");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                List.of(2)
        )).isEqualTo("xml_vast2");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                List.of(1)
        )).isEqualTo("vast");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                List.of(1, 4, 5, 6, 8, 9, 10)
        )).isEqualTo("vast");
    }

    private String doCustomVastRequestWithProtocolAndGetOutputParam(
            String improveAdm, String improvePrice, int placementId, List<Integer> protocols
    ) throws IOException, JSONException, XPathExpressionException {

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .videoProtocols(protocols)
                .improvePlacementId(placementId)
                .improveAdm(improveAdm)
                .improvePrice(improvePrice)
                .improveCacheId(getCacheIdRandom())
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        return vastQueryParams.get("output").get(0);
    }

    @Test
    public void testGvastResponseWithSiteCategory() throws XPathExpressionException, IOException, JSONException {
        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                List.of()
        )).isNull();

        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                List.of("IAB1")
        )).isEqualTo("IAB1");

        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                List.of("IAB1", "IAB2")
        )).isEqualTo("IAB1,IAB2");
    }

    private String doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
            String improveAdm, String improvePrice, int placementId, List<String> siteIabCategories
    ) throws IOException, JSONException, XPathExpressionException {

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .siteIabCategories(siteIabCategories)
                .improvePlacementId(placementId)
                .improveAdm(improveAdm)
                .improvePrice(improvePrice)
                .improveCacheId(getCacheIdRandom())
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));

        if (CollectionUtils.isEmpty(siteIabCategories)) {
            assertThat(custParams.get("iab_cat")).isNull();
            return null;
        }

        assertThat(custParams.get("iab_cat").size()).isEqualTo(1);
        return custParams.get("iab_cat").get(0);
    }

    @Test
    public void testGvastResponseWithCustomKeyValues() throws XPathExpressionException, JSONException, IOException {
        Map<String, List<String>> custParams = doCustomVastRequestWithCustomKeyValuesAndGetCustParam(
                getVastXmlInline("ad_1", false), "0.75", 20220620, Map.of(
                        "key1", "value1", "key2", "value2"
                )
        );
        assertThat(custParams.get("key1").size()).isEqualTo(1);
        assertThat(custParams.get("key1").get(0)).isEqualTo("value1");

        assertThat(custParams.get("key2").size()).isEqualTo(1);
        assertThat(custParams.get("key2").get(0)).isEqualTo("value2");
    }

    private Map<String, List<String>> doCustomVastRequestWithCustomKeyValuesAndGetCustParam(
            String improveAdm, String improvePrice, int placementId, Map<String, String> customKeyValues
    ) throws IOException, JSONException, XPathExpressionException {

        JSONObject responseJson = doCustomVastAuctionRequest(GvastAuctionTestParam.builder()
                .responseType("gvast")
                .improvePlacementId(placementId)
                .improveCustomKeyValues(customKeyValues)
                .improveAdm(improveAdm)
                .improvePrice(improvePrice)
                .improveCacheId(getCacheIdRandom())
                .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        return TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
    }

    @Test
    public void testCustomVastResponseWhenBidDiscardedForNotCaching()
            throws IOException, JSONException, XPathExpressionException {

        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 2nd bid and generic's 1st bid will be picked and cached.
        String improveCacheId = getCacheIdRandom();
        String genericCacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestToMultipleBidder(
                GvastMultipleBidderAuctionTestParam.builder()
                        .responseType("gvast")
                        .improvePlacementId(20220617)
                        .improveAdm1(improveVastXml1)
                        .improvePrice1("1.65")
                        .improveAdm2(improveVastXml2)
                        .improvePrice2("1.75")
                        .improveCacheId(improveCacheId)
                        .improveReturnsDeal(true)
                        .genericAdm1(genericVastXml1)
                        .genericPrice1("1.95")
                        .genericAdm2(genericVastXml2)
                        .genericPrice2("1.85")
                        .genericCacheId(genericCacheId)
                        .isCacheFailForImprove(true) /* Improve's adm's caching will fail. */
                        .build()
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag = generic's bid. Even though we had deal which was supposed to be 1st tag, we will not
        // get it because it's content was not cached.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamUrlWithGenericAsSingleBidder(vastAdTagUri1, genericCacheId, "20220617", "1.95");
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertNoExtensions(adm, "0");

        // Make sure we have only 1 ad and that is generic.
        assertAdCount(adm, 1);
        assertFallbackOnNoAd(adm, false, "0");
    }

    @Test
    public void testCustomVastResponseWhenSSPReturnsNoBid()
            throws XPathExpressionException, IOException, JSONException {

        int placementId = 20220629;
        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = doCustomVastRequestWhenSSPReturnsNoBid(
                "gvast", uniqueId, placementId, 0
        );

        assertBidCount(responseJson, 1, 1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // Only 1 tag with no bidder.
        String vastAdTagUri = getVastTagUri(adm, "0");
        assertGamUrlWithNoBidder(vastAdTagUri, "" + placementId);
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertNoExtensions(adm, "0");

        // Make sure we have only 1 ad and that is generic.
        assertAdCount(adm, 1);
        assertFallbackOnNoAd(adm, false, "0");
    }

    @Test
    public void testCustomVastResponseWithMultiImpsInRequest()
            throws XPathExpressionException, IOException, JSONException {
        String vastXml1 = getVastXmlInline("ad_1", true);
        String vastXml2 = getVastXmlInline("ad_2", false);
        String vastXml3 = getVastXmlInline("ad_3", false);
        String bannerAd = "<img src='banner-1.png' />";
        String nativeAd = toJsonString(createNativeResponse(3000, 2250, List.of(), List.of()));
        String cacheId1 = getCacheIdRandom();
        String cacheId2 = getCacheIdRandom();
        String cacheId3 = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestWithMultiImps(
                GvastMultiImpAuctionTestParam.builder()
                        .impId("imp_id_1")
                        .responseType("vast")
                        .improvePlacementId(2022091301)
                        .improveAdm(vastXml1)
                        .improvePrice("1.12")
                        .improveCacheId(cacheId1)
                        .build(),
                GvastMultiImpAuctionTestParam.builder()
                        .impId("imp_id_2")
                        .responseType("gvast")
                        .improvePlacementId(2022091302)
                        .improveAdm(vastXml2)
                        .improvePrice("1.23")
                        .improveCacheId(cacheId2)
                        .build(),
                GvastMultiImpAuctionTestParam.builder()
                        .impId("imp_id_3")
                        .responseType("waterfall")
                        .improvePlacementId(2022091303)
                        .improveAdm(vastXml3)
                        .improvePrice("1.34")
                        .improveCacheId(cacheId3)
                        .build(),
                GvastMultiImpAuctionTestParam.builder()
                        .impId("imp_id_4")
                        .improvePlacementId(2022091304)
                        .improveAdm(bannerAd)
                        .improvePrice("1.45")
                        .build(),
                GvastMultiImpAuctionTestParam.builder()
                        .impId("imp_id_5")
                        .nativeRequest(createNativeRequest("1.2", 90, 128, 128, 120))
                        .improvePlacementId(2022091305)
                        .improveAdm(nativeAd)
                        .improvePrice("1.56")
                        .build()
        );

        assertBidCount(responseJson, 1, 5);
        assertBidIdExists(responseJson, 0, 0);
        assertBidIdExists(responseJson, 0, 1);
        assertBidIdExists(responseJson, 0, 2);
        assertBidIdExists(responseJson, 0, 3);
        assertBidIdExists(responseJson, 0, 4);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidImpId(responseJson, 0, 1, "imp_id_2");
        assertBidImpId(responseJson, 0, 2, "imp_id_3");
        assertBidImpId(responseJson, 0, 3, "imp_id_4");
        assertBidImpId(responseJson, 0, 4, "imp_id_5");
        assertBidPrice(responseJson, 0, 0, 1.12);
        assertBidPrice(responseJson, 0, 1, 0.0);
        assertBidPrice(responseJson, 0, 2, 0.0);
        assertBidPrice(responseJson, 0, 3, 1.45);
        assertBidPrice(responseJson, 0, 4, 1.56);
        assertSeat(responseJson, 0, "improvedigital");

        // 1st imp's ad. responseType=vast
        String adm1 = getAdm(responseJson, 0, 0);
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        String mediaUrl = getXmlValue(adm1, "/VAST/Ad[@id='ad_1']/InLine/Creatives"
                + "/Creative[@AdID='ad_1']/Linear/MediaFiles/MediaFile[1]");
        assertThat(mediaUrl.trim()).isEqualTo("https://media.pbs.improvedigital.com/ad_1.mp4");
        assertCachedContentFromCacheId(cacheId1, getVastXmlToCache(
                vastXml1, "improvedigital", "1.12", 2022091301
        ));

        // 2nd imp's ad. responseType=gvast
        String adm2 = getAdm(responseJson, 0, 1);
        assertGamUrlWithImprovedigitalAsSingleBidder(
                getVastTagUri(adm2, "0"), cacheId2, "2022091302", "1.23"
        );
        assertCachedContentFromCacheId(cacheId2, getVastXmlToCache(
                vastXml2, "improvedigital", "1.23", 2022091302
        ));
        assertSSPSyncPixels(adm2, "0");
        assertNoCreative(adm2, "0");
        assertNoExtensions(adm2, "0");

        // 3rd imp's ad. responseType=waterfall
        String adm3 = getAdm(responseJson, 0, 2);
        assertThat(getVastTagUri(adm3, "0").trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + cacheId3);
        assertCachedContentFromCacheId(cacheId3, getVastXmlToCache(
                vastXml3, "improvedigital", "1.34", 2022091303
        ));
        assertSSPSyncPixels(adm3, "0");
        assertNoCreative(adm3, "0");
        assertNoExtensions(adm3, "0");

        // 4th imp's ad. <img />
        String adm4 = getAdm(responseJson, 0, 3);
        assertThat(adm4).startsWith(bannerAd);

        // 5th imp's ad. {{"assets":[{"id":1...
        String adm5 = getAdm(responseJson, 0, 4);
        assertThat(adm5).isEqualTo(nativeAd);
    }

    @Test
    public void testCustomVastResponseWithMultiFormatToMultipleBidders() throws Exception {
        int improvePlacementId = 2022091601;

        GvastMultiFormatSSPResponseTestParam respImprovedigital = GvastMultiFormatSSPResponseTestParam.builder()
                .respondToImpId("imp_id_1")
                .respondToBidderName("improvedigital")
                .price(1.12)
                .adm(getVastXmlInline("ad_1", false))
                .videoCacheId(getCacheIdRandom())
                .build();
        GvastMultiFormatSSPResponseTestParam respEvolution = GvastMultiFormatSSPResponseTestParam.builder()
                .respondToImpId("imp_id_1")
                .respondToBidderName("e_volution")
                .price(1.23)
                .adm(toJsonString(createNativeResponse(300, 250, List.of(), List.of())))
                .bidExt(new BidResponseBidExt()
                        .putEvolutionBidExt("native"))
                .build();
        GvastMultiFormatSSPResponseTestParam respSmarthub = GvastMultiFormatSSPResponseTestParam.builder()
                .respondToImpId("imp_id_1")
                .respondToBidderName("smarthub")
                .price(1.56) /* Highest bid so that 1 video is winner. */
                .adm(getVastXmlInline("ad_2", true))
                .videoCacheId(getCacheIdRandom())
                .bidExt(new BidResponseBidExt()
                        .putEvolutionBidExt("video"))
                .build();
        GvastMultiFormatSSPResponseTestParam respSalunamedia = GvastMultiFormatSSPResponseTestParam.builder()
                .respondToImpId("imp_id_1")
                .respondToBidderName("sa_lunamedia")
                .price(1.45)
                .adm("<img src='banner-1.png' />")
                .bidExt(new BidResponseBidExt()
                        .putEvolutionBidExt("banner"))
                .build();

        JSONObject responseJson = doCustomVastAuctionRequestWithMultiFormatMultiBidder(
                "gvast", improvePlacementId,
                List.of(
                        respImprovedigital,
                        respEvolution,
                        respSmarthub,
                        respSalunamedia
                ),
                GvastMultiFormatAuctionTestParam.builder()
                        .impId("imp_id_1")
                        .impExt(new AuctionBidRequestImpExt()
                                .putImprovedigitalPbs()
                                .putImprovedigitalPbsKeyValue("responseType", "gvast")
                                .putBidder("improvedigital")
                                .putBidderKeyValue("improvedigital", "placementId", improvePlacementId)
                                .putBidder("e_volution")
                                .putBidderKeyValue("e_volution", "key", "E_VOLUTION_KEY")
                                .putBidder("smarthub")
                                .putBidderKeyValue("smarthub", "token", "SMARTHUB_TOKEN")
                                .putBidderKeyValue("smarthub", "seat", "SMARTHUB_SEAT")
                                .putBidderKeyValue("smarthub", "partnerName", "SMARTHUB_PARTNER")
                                .putBidder("sa_lunamedia")
                                .putBidderKeyValue("sa_lunamedia", "key", "SA_LUNAMEDIA_KEY"))
                        .build()
        );

        // improvedigital and smarthub's video bids will be merged into 1 gam vast.
        // e_volution and sa_lunamedia will have their own non-video bids.

        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 3, 1, 1, 1);

        assertBidIdExists(responseJson, 2, 0);
        assertBidImpId(responseJson, 2, 0, "imp_id_1");
        assertSeat(responseJson, 2, "e_volution");
        assertBidPrice(responseJson, 2, 0, 1.23);
        assertThat(getAdm(responseJson, 2, 0)).isEqualTo(respEvolution.adm);

        assertBidIdExists(responseJson, 1, 0);
        assertBidImpId(responseJson, 1, 0, "imp_id_1");
        assertSeat(responseJson, 1, "sa_lunamedia");
        assertBidPrice(responseJson, 1, 0, 1.45);
        assertThat(getAdm(responseJson, 1, 0)).isEqualTo(
                respSalunamedia.adm + getCustomTrackerPixel("sa_lunamedia", "1.45", improvePlacementId + "")
        );

        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertSeat(responseJson, 0, "improvedigital");
        assertBidPrice(responseJson, 0, 0, 0.0);

        String vastAdTagUri = getVastTagUri(getAdm(responseJson, 0, 0), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertGamGeneralParameters(vastQueryParams, improvePlacementId + "");
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "smarthub");
        assertQuerySingleValue(custParams.get("hb_bidder_smarthub"), "smarthub");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");
        assertNoOtherKeysExcept(custParams, "hb_bidder", List.of(
                "hb_bidder", "hb_bidder_smarthub", "hb_bidder_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), respSmarthub.videoCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_smarthub"), respSmarthub.videoCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), respImprovedigital.videoCacheId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", List.of(
                "hb_uuid", "hb_uuid_smarthub", "hb_uuid_improvedigit"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_smarthub"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", List.of(
                "hb_format", "hb_format_smarthub", "hb_format_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), toMoneyFormat(respSmarthub.price));
        assertQuerySingleValue(custParams.get("hb_pb_smarthub"), toMoneyFormat(respSmarthub.price));
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), toMoneyFormat(respImprovedigital.price));
        assertNoOtherKeysExcept(custParams, "hb_pb", List.of(
                "hb_pb", "hb_pb_smarthub", "hb_pb_improvedigital"
        ));

        assertThat(getCustomParamCacheUrl(custParams, null))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + respSmarthub.videoCacheId);
        assertThat(getCustomParamCacheUrl(custParams, "smarthub"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + respSmarthub.videoCacheId);
        assertThat(getCustomParamCacheUrl(custParams, "improvedigital"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + respImprovedigital.videoCacheId);
    }

    private String getVastTagUri(String adm, String adId) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because vast tag uri will only appear in Wrapper.
        return getXmlValue(adm, "/VAST/Ad[@id='" + adId + "']/Wrapper/VASTAdTagURI");
    }

    private void assertGamUrlWithNoBidder(String vastAdTagUri, String placementId) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.size()).isEqualTo(1); /* Only 1 parameter within cust_params. */
        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamUrlWithImprovedigitalAsSingleBidder(
            String vastAdTagUri, String uniqueId, String placementId, String price
    ) throws MalformedURLException {
        assertGamUrlWithImprovedigitalAsSingleBidder(vastAdTagUri, uniqueId, placementId, price, false);
    }

    private void assertGamUrlWithImprovedigitalAsSingleBidder(
            String vastAdTagUri, String uniqueId, String placementId, String price, boolean isDeal
    ) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "improvedigital");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");
        assertNoOtherKeysExcept(custParams, "hb_bidder", List.of(
                "hb_bidder", "hb_bidder_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), uniqueId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", List.of(
                "hb_uuid", "hb_uuid_improvedigit"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", List.of(
                "hb_format", "hb_format_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), price);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), price);
        assertNoOtherKeysExcept(custParams, "hb_pb", List.of(
                "hb_pb", "hb_pb_improvedigital"
        ));

        assertThat(getCustomParamCacheUrl(custParams, null))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);
        assertThat(getCustomParamCacheUrl(custParams, "improvedigital"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);

        assertQuerySingleValue(custParams.get("pbct"), "1");
        assertThat(custParams.get("fl")).isNull(); /* No first look */

        if (isDeal) {
            assertQuerySingleValue(custParams.get("nf"), "1");
            assertQuerySingleValue(custParams.get("tnl_wog"), "1");
        } else {
            assertThat(custParams.get("nf")).isNull();
            assertThat(custParams.get("tnl_wog")).isNull(); /* No disabling of other SSP */
        }

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamUrlWithGenericAsSingleBidder(
            String vastAdTagUri, String uniqueId, String placementId, String price
    ) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_generic"), "generic");
        assertNoOtherKeysExcept(custParams, "hb_bidder", List.of(
                "hb_bidder", "hb_bidder_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_generic"), uniqueId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", List.of(
                "hb_uuid", "hb_uuid_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_generic"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", List.of(
                "hb_format", "hb_format_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), price);
        assertQuerySingleValue(custParams.get("hb_pb_generic"), price);
        assertNoOtherKeysExcept(custParams, "hb_pb", List.of(
                "hb_pb", "hb_pb_generic"
        ));

        assertThat(getCustomParamCacheUrl(custParams, null))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);
        assertThat(getCustomParamCacheUrl(custParams, "generic"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);

        assertQuerySingleValue(custParams.get("pbct"), "1");
        assertThat(custParams.get("fl")).isNull(); /* No first look */
        assertThat(custParams.get("nf")).isNull();
        assertThat(custParams.get("tnl_wog")).isNull(); /* No disabling of other SSP */
        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamUrlWithImprovedigitalLostToGenericBidder(
            String vastAdTagUri,
            String placementId,
            String improveCacheId,
            String genericCacheId,
            String improvePrice,
            String genericPrice
    ) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertGamGeneralParameters(vastQueryParams, placementId);
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_generic"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");
        assertNoOtherKeysExcept(custParams, "hb_bidder", List.of(
                "hb_bidder", "hb_bidder_generic", "hb_bidder_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_generic"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), improveCacheId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", List.of(
                "hb_uuid", "hb_uuid_generic", "hb_uuid_improvedigit"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_generic"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", List.of(
                "hb_format", "hb_format_generic", "hb_format_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_generic"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), improvePrice);
        assertNoOtherKeysExcept(custParams, "hb_pb", List.of(
                "hb_pb", "hb_pb_generic", "hb_pb_improvedigital"
        ));

        assertThat(getCustomParamCacheUrl(custParams, null))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + genericCacheId);
        assertThat(getCustomParamCacheUrl(custParams, "generic"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + genericCacheId);
        assertThat(getCustomParamCacheUrl(custParams, "improvedigital"))
                .isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + improveCacheId);

        assertQuerySingleValue(custParams.get("pbct"), "1");
        assertThat(custParams.get("fl")).isNull();
        assertThat(custParams.get("tnl_wog")).isNull();
        assertThat(custParams.get("nf")).isNull();

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamFirstLookUrl(String vastAdTagUri, String placementId) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("pbct")).isNull();
        assertQuerySingleValue(custParams.get("fl"), "1");
        assertQuerySingleValue(custParams.get("tnl_wog"), "1");
        assertThat(custParams.get("nf")).isNull();

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamNoHbUrl(String vastAdTagUri, String placementId) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("pbct")).isNull();
        assertThat(custParams.get("fl")).isNull(); /* No first look */
        assertThat(custParams.get("tnl_wog")).isNull();
        assertThat(custParams.get("nf")).isNull();

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertNoCreative(String vastXml, String adId) throws XPathExpressionException {
        NodeList creatives = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']//Creative");
        assertThat(creatives.getLength()).isEqualTo(0);
    }

    private void assertNoSSPSyncPixels(String vastXml, String adId) throws XPathExpressionException {
        NodeList syncPixels = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']//Impression");
        assertThat(syncPixels.getLength()).isEqualTo(0);
    }

    private void assertSSPSyncPixels(String vastXml, String adId) throws XPathExpressionException {
        List<String> syncPixels = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            // Looking only for Wrapper (not InLine) because sync pixel will only be added in Wrapper.
            syncPixels.add(getXmlValue(vastXml, "/VAST/Ad[@id='" + adId + "']/Wrapper/Impression[" + i + "]"));
        }
        Collections.sort(syncPixels);
        assertThat(syncPixels.get(0)).isEqualTo("https://ad.360yield.com/server_match"
                + "?gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&r="
                + HttpUtil.encodeUrl("http://localhost:8080/setuid"
                + "?bidder=improvedigital"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy"
                + "=&uid={PUB_USER_ID}")
        );
        assertThat(syncPixels.get(1)).isEqualTo("https://ib.adnxs.com/getuid"
                + "?"
                + HttpUtil.encodeUrl("http://localhost:8080/setuid"
                + "?bidder=adnxs"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&uid=$UID")
        );
        assertThat(syncPixels.get(2)).isEqualTo("https://image8.pubmatic.com/AdServer/ImgSync"
                + "?p=159706"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&pu="
                + HttpUtil.encodeUrl("http://localhost:8080/setuid"
                + "?bidder=pubmatic"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&uid=#PMUID")
        );
        assertThat(syncPixels.get(3)).isEqualTo("https://ssbsync-global.smartadserver.com/api/sync"
                + "?callerId=5"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&redirectUri="
                + HttpUtil.encodeUrl("http://localhost:8080/setuid"
                + "?bidder=smartadserver"
                + "&gdpr=0"
                + "&gdpr_consent="
                + "&us_privacy="
                + "&uid=[ssb_sync_pid]")
        );
    }

    private void assertAdCount(String vastXml, int expectedAdCount) throws XPathExpressionException {
        NodeList creatives = getXmlNodeList(vastXml, "/VAST/Ad");
        assertThat(creatives.getLength()).isEqualTo(expectedAdCount);
    }

    private void assertNoExtensions(String vastXml, String adId) throws XPathExpressionException {
        NodeList creatives = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']//Extension");
        assertThat(creatives.getLength()).isEqualTo(0);
    }

    private void assertExtensions(String vastXml, String adId, int fallbackIndex) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because extension will only be added in Wrapper.
        NodeList extensions = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']/Wrapper/Extensions/Extension");
        assertThat(extensions.getLength()).isEqualTo(1);

        NamedNodeMap extensionAttr = extensions.item(0).getAttributes();
        assertThat(extensionAttr.getNamedItem("type").getNodeValue()).isEqualTo("waterfall");
        assertThat(extensionAttr.getNamedItem("fallback_index").getNodeValue()).isEqualTo("" + fallbackIndex);
    }

    private void assertExtensionDebug(String vastXml, String adId, String bidderName, String uniqueId)
            throws XPathExpressionException {
        Node debug = getDebug(vastXml, adId);
        assertThat(getXmlValue(debug, "//" + bidderName + "/uri"))
                .isEqualTo("http://localhost:8090/" + bidderName + "-exchange");
        assertThat(getXmlValue(debug, "//" + bidderName + "/requestbody"))
                .contains("request_id_" + uniqueId);
        assertThat(getXmlValue(debug, "//" + bidderName + "/responsebody"))
                .contains("request_id_" + uniqueId);
        assertThat(getXmlValue(debug, "//" + bidderName + "/status"))
                .isEqualTo("200");

        assertThat(getXmlValue(debug, "//resolvedrequest/id"))
                .isEqualTo("request_id_" + uniqueId);
    }

    private Node getDebug(String vastXml, String adId) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because extension will only be added in Wrapper.
        NodeList debugs = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']/Wrapper"
                + "/Extensions/Extension[@type='debug']/responseExt/debug");
        assertThat(debugs.getLength()).isEqualTo(1);

        return debugs.item(0);
    }

    private void assertNoDebug(String vastXml, String adId) throws XPathExpressionException {
        NodeList debugExt = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']/Wrapper"
                + "/Extensions/Extension[@type='debug']");
        assertThat(debugExt.getLength()).isEqualTo(0);
    }

    private void assertFallbackOnNoAd(String vastXml, boolean hasFallbackOnNoAd, String adId)
            throws XPathExpressionException {

        String wrapperLookupAttr = hasFallbackOnNoAd ? "@fallbackOnNoAd='true'" : "not(@fallbackOnNoAd)";

        NodeList wrappers = getXmlNodeList(vastXml, "/VAST/Ad[@id='" + adId + "']/Wrapper[" + wrapperLookupAttr + "]");
        assertThat(wrappers.getLength()).isEqualTo(1);
    }

    private NodeList getXmlNodeList(String xml, String xpathExpression) throws XPathExpressionException {
        return (NodeList) XPathFactory.newInstance().newXPath()
                .compile(xpathExpression)
                .evaluate(new InputSource(new StringReader(xml)), XPathConstants.NODESET);
    }

    private String getXmlValue(String xml, String xpathExpression) throws XPathExpressionException {
        return XPathFactory.newInstance().newXPath()
                .compile(xpathExpression)
                .evaluate(new InputSource(new StringReader(xml)));
    }

    private String getXmlValue(Node xmlNode, String xpathExpression) throws XPathExpressionException {
        return XPathFactory.newInstance().newXPath()
                .compile(xpathExpression)
                .evaluate(xmlNode);
    }

    private void assertGamGeneralParameters(Map<String, List<String>> vastQueryParams, String placementId) {
        assertGamGeneralParameters(vastQueryParams, placementId, "http://pbs.improvedigital.com" /* Web request */);
    }

    private void assertGamGeneralParameters(Map<String, List<String>> vastQueryParams, String placementId, String url) {
        assertThat(vastQueryParams.get("gdfp_req").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdfp_req").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("env").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("env").get(0)).isEqualTo("vp");

        assertThat(vastQueryParams.get("unviewed_position_start").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("unviewed_position_start").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("correlator").size()).isEqualTo(1);
        assertThat(Long.parseLong(vastQueryParams.get("correlator").get(0)))
                .isGreaterThan(System.currentTimeMillis() - 5 * 60 * 1000);

        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("iu").get(0)).isEqualTo("/" + GAM_NETWORK_CODE + "/pbs/" + placementId);

        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("output").get(0)).isEqualTo("xml_vast2");

        assertThat(vastQueryParams.get("sz").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("sz").get(0)).isEqualTo("640x480|640x360");

        assertThat(vastQueryParams.get("description_url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("description_url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("url").get(0)).isEqualTo(url);
    }

    private Map<String, List<String>> getNonEmptyCustomParams(String vastAdTagUri) throws MalformedURLException {
        Map<String, List<String>> vastQueryParams = TestUtils.splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        return TestUtils.splitQuery(vastQueryParams.get("cust_params").get(0));
    }

    private Response getGvastResponse() {
        return getGvastResponse(null);
    }

    private Response getGvastResponse(
            Function<RequestSpecification, RequestSpecification> modifier
    ) {
        return getGvastResponse(VALID_PLACEMENT_ID, modifier);
    }

    private Response getGvastResponse(
            String placementId,
            Function<RequestSpecification, RequestSpecification> modifier
    ) {
        RequestSpecification spec = specWithPBSHeader(18080)
                .queryParam("p", placementId);
        if (modifier != null) {
            spec = modifier.apply(spec);
        }
        return spec.get(GVastHandler.END_POINT);
    }

    private JSONObject doVastAuctionRequest(
            String improveAdm, String price, String storedImpId, int placementIdOfStoredImp
    ) throws IOException, JSONException {

        String uniqueId = UUID.randomUUID().toString();
        String cacheId = getCacheIdRandom();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", placementIdOfStoredImp))
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .channel(getExtPrebidChannelForGvast())
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(Double.parseDouble(price))
                                .adm(improveAdm)
                                .build()
                )))
        );

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(createCacheRequest(
                        "request_id_" + uniqueId,
                        getVastXmlToCache(improveAdm, "improvedigital", price, placementIdOfStoredImp)
                                .replace("\"", "\\\"")
                )))
                .willReturn(aResponse().withBody(createCacheResponse(
                        cacheId
                )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        /* Nothing here. All are defined in stored imp. */
                                        .build())
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(storedImpId)
                                        .putImprovedigitalPbs()
                                        .putImprovedigitalPbsKeyValue("responseType", "vast"))
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidCount(responseJson, 1, 1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, Double.parseDouble(price));
        assertSeat(responseJson, 0, "improvedigital");
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequest(GvastAuctionTestParam param) throws IOException, JSONException {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(param.toSSPBidRequestImpExt())
                                        .videoData(VideoTestParam.getDefault().toBuilder()
                                                .protocols(param.videoProtocols)
                                                .build())
                                        .build())
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .siteIABCategories(param.siteIabCategories)
                                .gdprConsent(param.gdprConsent)
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(Double.parseDouble(param.improvePrice))
                                .adm(param.improveAdm)
                                .build()
                )))
        );

        String cachedContent = getVastXmlToCache(
                param.improveAdm, "improvedigital", param.improvePrice, param.improvePlacementId
        );
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(createCacheRequest(
                        "request_id_" + uniqueId,
                        cachedContent.replace("\"", "\\\"")
                )))
                .willReturn(aResponse().withBody(createCacheResponse(
                        param.improveCacheId
                )))
        );

        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                .withQueryParam("uuid", equalToIgnoreCase(param.improveCacheId))
                .willReturn(aResponse()
                        .withBody(cachedContent))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(param.toCustomVastAuctionBidRequestImpExt())
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .videoData(VideoTestParam.getDefault().toBuilder()
                                                .protocols(param.videoProtocols)
                                                .build())
                                        .build())
                                .build()))
                        .siteIABCategories(param.siteIabCategories)
                        .gdprConsent(param.gdprConsent)
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidCount(responseJson, 1, 1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequestWithMultiFormatMultiBidder(
            String responseType,
            int improvePlacementId,
            List<GvastMultiFormatSSPResponseTestParam> sspResponseParams,
            GvastMultiFormatAuctionTestParam... auctionParams
    ) throws JSONException {
        String uniqueId = UUID.randomUUID().toString();

        sspResponseParams.forEach(param ->
                WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/" + param.respondToBidderName.replace("_", "") + "-exchange"))
                        .willReturn(aResponse().withBody(getSSPBidResponse(
                                param.respondToBidderName, uniqueId, "USD", BidResponseTestData.builder()
                                        .impId(param.respondToImpId)
                                        .price(param.price)
                                        .adm(param.adm)
                                        .bidExt(param.bidExt)
                                        .build()
                        )))
                ));

        List<GvastMultiFormatSSPResponseTestParam> videoParams = sspResponseParams.stream()
                .filter(e -> StringUtils.containsIgnoreCase(e.adm, "<vast"))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(videoParams)) {
            WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                    .willReturn(aResponse()
                            .withTransformers("it-test-cache-set-by-content")
                            .withTransformerParameters(videoParams.stream()
                                    .collect(Collectors.toMap(
                                            p -> p.toVastXmlToCache(p.respondToBidderName, improvePlacementId),
                                            p -> p.videoCacheId)))
                    )
            );

            WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                    .willReturn(aResponse()
                            .withTransformers("it-test-cache-get-by-uuid")
                            .withTransformerParameters(videoParams.stream()
                                    .collect(Collectors.toMap(
                                            p -> p.videoCacheId,
                                            p -> p.toVastXmlToCache(p.respondToBidderName, improvePlacementId))))
                    )
            );
        }

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(Arrays.stream(auctionParams)
                                .map(auctionParam -> AuctionBidRequestImpTestData.builder()
                                        .impExt(auctionParam.impExt)
                                        .impData(SingleImpTestData.builder()
                                                .id(auctionParam.impId)
                                                .bannerData(BannerTestParam.getDefault())
                                                .videoData(VideoTestParam.getDefault())
                                                .nativeData(NativeTestParam.builder()
                                                        .request(createNativeRequest(
                                                                "1.2", 90, 128, 128, 120
                                                        ))
                                                        .build())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequestWithMultiImps(GvastMultiImpAuctionTestParam... params)
            throws IOException, JSONException {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .willReturn(aResponse()
                        .withTransformers("it-test-bid-response-by-impid")
                        .withTransformerParameters(Arrays.stream(params)
                                .collect(Collectors.toMap(
                                        p -> p.impId,
                                        p -> getSSPBidResponse(
                                                "improvedigital", uniqueId, "USD", p.toBidResponseTestData()
                                        ))))
                )
        );

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                        "request_id_" + uniqueId,
                        Arrays.stream(params)
                                .filter(p -> StringUtils.isNotEmpty(p.improveCacheId))
                                .map(p -> p.toVastXmlToCache("improvedigital")
                                        .replace("\"", "\\\""))
                                .collect(Collectors.toList())
                )))
                .willReturn(aResponse()
                        .withTransformers("it-test-cache-set-by-content")
                        .withTransformerParameters(Arrays.stream(params)
                                .filter(p -> StringUtils.isNotEmpty(p.improveCacheId))
                                .collect(Collectors.toMap(
                                        p -> p.toVastXmlToCache("improvedigital"),
                                        p -> p.improveCacheId)))
                )
        );

        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                .willReturn(aResponse()
                        .withTransformers("it-test-cache-get-by-uuid")
                        .withTransformerParameters(Arrays.stream(params)
                                .filter(p -> StringUtils.isNotEmpty(p.improveCacheId))
                                .collect(Collectors.toMap(
                                        p -> p.improveCacheId,
                                        p -> p.toVastXmlToCache("improvedigital"))))
                )
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(Arrays.stream(params)
                                .map(p -> AuctionBidRequestImpTestData.builder()
                                        .impData(SingleImpTestData.builder()
                                                .id(p.impId)
                                                .bannerData(p.toBannerTestData())
                                                .videoData(p.toVideoTestData())
                                                .nativeData(p.toNativeTestData())
                                                .build())
                                        .impExt(p.toAuctionBidRequestImpExt())
                                        .build()
                                )
                                .collect(Collectors.toList()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequestToMultipleBidder(GvastMultipleBidderAuctionTestParam param)
            throws IOException, JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);
        String improveVastXmlToCache = improvePrice1Value > improvePrice2Value
                ? param.toVastXml1ToCacheOfImprovedigital() : param.toVastXml2ToCacheOfImprovedigital();

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);
        String genericVastXmlToCache = genericPrice1Value > genericPrice2Value
                ? param.toVastXml1ToCacheOfGeneric() : param.toVastXml2ToCacheOfGeneric();

        BidResponseBidExt improveDealBidExt = new BidResponseBidExt()
                .putImprovedigitalBidExt("classic", param.improvePlacementId * 10);

        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(param.toImproveSSPBidRequestImpExt())
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(improvePrice1Value)
                                .adm(param.improveAdm1)
                                .bidExt(param.improveReturnsDeal && improvePrice1Value > improvePrice2Value
                                        ? improveDealBidExt
                                        : null)
                                .build(),
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(improvePrice2Value)
                                .adm(param.improveAdm2)
                                .bidExt(param.improveReturnsDeal && improvePrice2Value > improvePrice1Value
                                        ? improveDealBidExt
                                        : null)
                                .build()
                )))
        );

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(param.toGenericSSPBidRequestImpExt())
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse("generic", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(genericPrice1Value)
                                .adm(param.genericAdm1)
                                .build(),
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(genericPrice2Value)
                                .adm(param.genericAdm2)
                                .build()
                )))
        );

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                        "request_id_" + uniqueId,
                        improveVastXmlToCache.replace("\"", "\\\""),
                        genericVastXmlToCache.replace("\"", "\\\"")
                )))
                .willReturn(aResponse()
                        .withTransformers("it-test-cache-set-by-content")
                        .withTransformerParameter(
                                param.isCacheFailForImprove ? "error" : improveVastXmlToCache, param.improveCacheId
                        )
                        .withTransformerParameter(genericVastXmlToCache, param.genericCacheId)
                )
        );

        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                .willReturn(aResponse()
                        .withTransformers("it-test-cache-get-by-uuid")
                        .withTransformerParameter(param.improveCacheId, improveVastXmlToCache)
                        .withTransformerParameter(param.genericCacheId, genericVastXmlToCache))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .impExt(new AuctionBidRequestImpExt()
                                        .putImprovedigitalPbs()
                                        .putImprovedigitalPbsKeyValue("responseType", param.responseType)
                                        .putImprovedigitalPbsKeyValue("waterfall", param.defaultWaterfalls == null
                                                ? null : Map.of("default", param.defaultWaterfalls))
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", param.improvePlacementId)
                                        .putBidder("generic")
                                        .putBidderKeyValue("generic", "exampleProperty", "examplePropertyValue"))
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidCount(responseJson, 1, 1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastRequestWhenSSPReturnsNoBid(
            String responseType, String uniqueId, int placementId, int test
    ) throws JSONException {
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putBidder()
                                                .putBidderKeyValue("placementId", placementId))
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .test(test)
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD"
                )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .impExt(new AuctionBidRequestImpExt()
                                        .putImprovedigitalPbs()
                                        .putImprovedigitalPbsKeyValue("responseType", responseType)
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", placementId))
                                .build()))
                        .test(test)
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        return responseJson;
    }

    private ExtRequestPrebidCache getExtPrebidCacheForGvast() {
        return ExtRequestPrebidCache.of(
                null,
                ExtRequestPrebidCacheVastxml.of(null, true),
                false
        );
    }

    private ExtRequestPrebidChannel getExtPrebidChannelForGvast() {
        return ExtRequestPrebidChannel.of("web");
    }

    private ExtRequestTargeting getExtPrebidTargetingForGvast() {
        return ExtRequestTargeting.builder()
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(true)
                .pricegranularity(BID_REQUEST_MAPPER.valueToTree(
                        ExtPriceGranularity.of(2, List.of(
                                ExtGranularityRange.of(BigDecimal.valueOf(2), BigDecimal.valueOf(0.01)),
                                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.05)),
                                ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.1)),
                                ExtGranularityRange.of(BigDecimal.valueOf(40), BigDecimal.valueOf(0.5)),
                                ExtGranularityRange.of(BigDecimal.valueOf(100), BigDecimal.valueOf(1))
                        ))
                ))
                .build();
    }

    private String getCustomParamCacheUrl(Map<String, List<String>> custParams, String bidderName) {
        if (StringUtils.isNotEmpty(bidderName)) {
            return "http://"
                    + custParams.get(StringUtils.truncate("hb_cache_host_" + bidderName, 20)).get(0)
                    + custParams.get(StringUtils.truncate("hb_cache_path_" + bidderName, 20)).get(0)
                    + "?uuid=" + custParams.get(StringUtils.truncate("hb_uuid_" + bidderName, 20)).get(0);
        }
        return "http://"
                + custParams.get("hb_cache_host").get(0)
                + custParams.get("hb_cache_path").get(0)
                + "?uuid=" + custParams.get("hb_uuid").get(0);
    }

    private static String getVastXmlToCache(String vastXml, String bidder, String cpm, int placementId) {
        return vastXml.replace(
                "</InLine>",
                "<Impression>"
                        + "<![CDATA[https://it.pbs.com/ssp_bids?bidder=" + bidder + "&cpm=" + cpm + "&pid=" + placementId + "]]>"
                        + "</Impression>"
                        + "</InLine>"
        );
    }

    private void assertNoOtherKeysExcept(
            Map<String, List<String>> custParams, String keyPrefix, List<String> ignoringValues
    ) {
        assertThat(custParams.keySet().stream()
                .filter(k -> k.startsWith(keyPrefix))
                .sorted()
                .collect(Collectors.toList())
        ).isEqualTo(ignoringValues.stream()
                .sorted()
                .collect(Collectors.toList())
        );
    }

    @Builder(toBuilder = true)
    private static class GvastAuctionTestParam {
        String responseType;
        List<String> defaultWaterfalls;
        List<Integer> videoProtocols;
        Map<String, ?> gamParams;
        List<String> siteIabCategories;
        String storedImpId;
        int improvePlacementId;
        Map<String, String> improveCustomKeyValues;
        String improveAdm;
        String improvePrice;
        String improveCacheId;
        String gdprConsent;

        public SSPBidRequestImpExt toSSPBidRequestImpExt() {
            return new SSPBidRequestImpExt()
                    .putStoredRequest(storedImpId)
                    .putBidder()
                    .putBidderKeyValue("placementId", improvePlacementId)
                    .putBidderKeyValue("keyValues", improveCustomKeyValues);
        }

        public AuctionBidRequestImpExt toCustomVastAuctionBidRequestImpExt() {
            return new AuctionBidRequestImpExt()
                    .putImprovedigitalPbs()
                    .putImprovedigitalPbsKeyValue("responseType", responseType)
                    .putImprovedigitalPbsKeyValue("gam", gamParams)
                    .putImprovedigitalPbsKeyValue("waterfall", defaultWaterfalls == null
                            ? null : Map.of("default", defaultWaterfalls))
                    .putStoredRequest(storedImpId)
                    .putBidder("improvedigital")
                    .putBidderKeyValue("improvedigital", "placementId", improvePlacementId)
                    .putBidderKeyValue("improvedigital", "keyValues", improveCustomKeyValues);
        }
    }

    @Builder(toBuilder = true)
    private static class GvastMultiImpAuctionTestParam {
        String impId;
        String responseType;
        Request nativeRequest;
        int improvePlacementId;
        String improveAdm;
        String improvePrice;
        String improveCacheId;

        AuctionBidRequestImpExt toAuctionBidRequestImpExt() {
            return new AuctionBidRequestImpExt()
                    .putImprovedigitalPbs()
                    .putImprovedigitalPbsKeyValue("responseType", responseType)
                    .putBidder("improvedigital")
                    .putBidderKeyValue("improvedigital", "placementId", improvePlacementId);
        }

        BidResponseTestData toBidResponseTestData() {
            return BidResponseTestData.builder()
                    .impId(impId)
                    .price(Double.parseDouble(improvePrice))
                    .adm(improveAdm)
                    .build();
        }

        String toVastXmlToCache(String bidderName) {
            return getVastXmlToCache(
                    improveAdm, bidderName, improvePrice, improvePlacementId
            );
        }

        public BannerTestParam toBannerTestData() {
            return !StringUtils.containsIgnoreCase(improveAdm, "<img") ? null : BannerTestParam.getDefault();
        }

        public VideoTestParam toVideoTestData() {
            return !StringUtils.containsIgnoreCase(improveAdm, "<vast") ? null : VideoTestParam.getDefault();
        }

        public NativeTestParam toNativeTestData() {
            return !StringUtils.startsWith(improveAdm, "{") ? null : NativeTestParam.builder()
                    .request(nativeRequest)
                    .build();
        }
    }

    @Builder(toBuilder = true)
    private static class GvastMultiFormatAuctionTestParam {
        String impId;
        AuctionBidRequestImpExt impExt;
    }

    @Builder(toBuilder = true)
    private static class GvastMultiFormatSSPResponseTestParam {
        String respondToImpId;
        String respondToBidderName;
        double price;
        String adm;
        String videoCacheId;
        BidResponseBidExt bidExt;

        String toVastXmlToCache(String bidderName, int improvePlacementId) {
            return getVastXmlToCache(
                    adm, bidderName, String.format("%.2f", price), improvePlacementId
            );
        }
    }

    @Builder(toBuilder = true)
    private static class GvastMultipleBidderAuctionTestParam {
        String responseType;
        List<String> defaultWaterfalls;
        int improvePlacementId;
        String improveAdm1;
        String improvePrice1;
        String improveAdm2;
        String improvePrice2;
        String improveCacheId;
        boolean improveReturnsDeal;
        String genericAdm1;
        String genericPrice1;
        String genericAdm2;
        String genericPrice2;
        String genericCacheId;
        boolean isCacheFailForImprove;

        SSPBidRequestImpExt toImproveSSPBidRequestImpExt() {
            return new SSPBidRequestImpExt()
                    .putBidder()
                    .putBidderKeyValue("placementId", improvePlacementId);
        }

        public SSPBidRequestImpExt toGenericSSPBidRequestImpExt() {
            return new SSPBidRequestImpExt()
                    .putBidder()
                    .putBidderKeyValue("exampleProperty", "examplePropertyValue");
        }

        public String toVastXml1ToCacheOfImprovedigital() {
            return getVastXmlToCache(improveAdm1, "improvedigital", improvePrice1, improvePlacementId);
        }

        public String toVastXml2ToCacheOfImprovedigital() {
            return getVastXmlToCache(improveAdm2, "improvedigital", improvePrice2, improvePlacementId);
        }

        public String toVastXml1ToCacheOfGeneric() {
            return getVastXmlToCache(genericAdm1, "generic", genericPrice1, improvePlacementId);
        }

        public String toVastXml2ToCacheOfGeneric() {
            return getVastXmlToCache(genericAdm2, "generic", genericPrice2, improvePlacementId);
        }
    }
}
