package com.improvedigital.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.improvedigital.prebid.server.customvast.handler.GVastHandler;
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

@TestPropertySource(
        locations = {
                "/com/improvedigital/prebid/server/it/test-application-improvedigital-hooks.properties"
        },
        properties = {
                "auction.generate-source-tid=true",
                "admin.port=18060",
                "http.port=18080",
        }
)
@RunWith(SpringRunner.class)
public class ImprovedigitalGvastTest extends ImprovedigitalIntegrationTest {

    /* This placement's stored imp contains ext.prebid.improvedigitalpbs.waterfall.default=gam */
    private static final String VALID_PLACEMENT_ID = "20220325";

    @Test
    public void gvastHasProperQueryParamsInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse();

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertGamGeneralParameters(vastQueryParams, "20220325");
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("cust_params").get(0)).isEqualTo("tnl_asset_id=prebidserver");
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
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
        String mediaUrl = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='ad_1']/InLine/Creatives"
                        + "/Creative[@AdID='ad_1']/Linear/MediaFiles/MediaFile[1]")
                .evaluate(new InputSource(new StringReader(adm)));
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
                .defaultWaterfalls(Arrays.asList(
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
                .defaultWaterfalls(Arrays.asList(
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
        Map<String, List<String>> customUrlParams = splitQuery(new URL(vastAdTagUri2).getQuery());
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
                        .defaultWaterfalls(Arrays.asList("gam_first_look", "gam"))
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
                        .defaultWaterfalls(Arrays.asList("gam_first_look", "gam"))
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
                "waterfall", UUID.randomUUID().toString(), 20220629
        );
        assertBidCountIsZero(responseJson);
        assertCurrency(responseJson, "USD");
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

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        return vastQueryParams.get("iu").get(0);
    }

    @Test
    public void testGvastResponseWithVideoProtocol()
            throws XPathExpressionException, IOException, JSONException {

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                Arrays.asList(2, 3, 7)
        )).isEqualTo("xml_vast4");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                Arrays.asList(2, 3)
        )).isEqualTo("xml_vast3");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                Arrays.asList(2)
        )).isEqualTo("xml_vast2");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                Arrays.asList(1)
        )).isEqualTo("vast");

        assertThat(doCustomVastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("ad_1", true), "0.97", 20220620,
                Arrays.asList(1, 4, 5, 6, 8, 9, 10)
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

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        return vastQueryParams.get("output").get(0);
    }

    @Test
    public void testGvastResponseWithSiteCategory() throws XPathExpressionException, IOException, JSONException {
        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                Arrays.asList()
        )).isNull();

        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                Arrays.asList("IAB1")
        )).isEqualTo("IAB1");

        assertThat(doCustomVastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("ad_1", true), "0.85", 20220620,
                Arrays.asList("IAB1", "IAB2")
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

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));

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

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        return splitQuery(vastQueryParams.get("cust_params").get(0));
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
                "gvast", uniqueId, placementId
        );

        assertBidCountIsOne(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_0_" + uniqueId);
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
    public void testCustomVastResponseWithMultipleImpsInRequest()
            throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("ad_1", true);
        String cacheId = getCacheIdRandom();

        JSONObject responseJson = doCustomVastAuctionRequestWithMultipleImp(
                GvastMultipleImpAuctionTestParam.builder()
                        .responseType("gvast")
                        .storedImpId("20220608")
                        .improvePlacementId(20220608)
                        .improveAdm(vastXml)
                        .improvePrice("1.08")
                        .improveCacheId(cacheId)
                        .build(),
                GvastMultipleImpAuctionTestParam.builder()
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

    private String getVastTagUri(String adm, String adId) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because vast tag uri will only appear in Wrapper.
        return XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(adm)));
    }

    private void assertGamUrlWithNoBidder(String vastAdTagUri, String placementId) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "improvedigital");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");
        assertNoOtherKeysExcept(custParams, "hb_bidder", Arrays.asList(
                "hb_bidder", "hb_bidder_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), uniqueId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", Arrays.asList(
                "hb_uuid", "hb_uuid_improvedigit"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", Arrays.asList(
                "hb_format", "hb_format_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), price);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), price);
        assertNoOtherKeysExcept(custParams, "hb_pb", Arrays.asList(
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_generic"), "generic");
        assertNoOtherKeysExcept(custParams, "hb_bidder", Arrays.asList(
                "hb_bidder", "hb_bidder_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_generic"), uniqueId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", Arrays.asList(
                "hb_uuid", "hb_uuid_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_generic"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", Arrays.asList(
                "hb_format", "hb_format_generic"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), price);
        assertQuerySingleValue(custParams.get("hb_pb_generic"), price);
        assertNoOtherKeysExcept(custParams, "hb_pb", Arrays.asList(
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertGamGeneralParameters(vastQueryParams, placementId);
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_generic"), "generic");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");
        assertNoOtherKeysExcept(custParams, "hb_bidder", Arrays.asList(
                "hb_bidder", "hb_bidder_generic", "hb_bidder_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_uuid"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_generic"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), improveCacheId);
        assertNoOtherKeysExcept(custParams, "hb_uuid", Arrays.asList(
                "hb_uuid", "hb_uuid_generic", "hb_uuid_improvedigit"
        ));

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_generic"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");
        assertNoOtherKeysExcept(custParams, "hb_format", Arrays.asList(
                "hb_format", "hb_format_generic", "hb_format_improvedig"
        ));

        assertQuerySingleValue(custParams.get("hb_pb"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_generic"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), improvePrice);
        assertNoOtherKeysExcept(custParams, "hb_pb", Arrays.asList(
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

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("pbct")).isNull();
        assertQuerySingleValue(custParams.get("fl"), "1");
        assertQuerySingleValue(custParams.get("tnl_wog"), "1");
        assertThat(custParams.get("nf")).isNull();

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertGamNoHbUrl(String vastAdTagUri, String placementId) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertGamGeneralParameters(vastQueryParams, placementId);

        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertThat(custParams.get("pbct")).isNull();
        assertThat(custParams.get("fl")).isNull(); /* No first look */
        assertThat(custParams.get("tnl_wog")).isNull();
        assertThat(custParams.get("nf")).isNull();

        assertQuerySingleValue(custParams.get("tnl_asset_id"), "prebidserver");
    }

    private void assertNoCreative(String vastXml, String adId) throws XPathExpressionException {
        NodeList creatives = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']//Creative")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(creatives.getLength()).isEqualTo(0);
    }

    private void assertNoSSPSyncPixels(String vastXml, String adId) throws XPathExpressionException {
        NodeList syncPixels = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']//Impression")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(syncPixels.getLength()).isEqualTo(0);
    }

    private void assertSSPSyncPixels(String vastXml, String adId) throws XPathExpressionException {
        List<String> syncPixels = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            // Looking only for Wrapper (not InLine) because sync pixel will only be added in Wrapper.
            syncPixels.add(XPathFactory.newInstance().newXPath()
                    .compile("/VAST/Ad[@id='" + adId + "']/Wrapper/Impression[" + i + "]")
                    .evaluate(new InputSource(new StringReader(vastXml))));
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
        NodeList creatives = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(creatives.getLength()).isEqualTo(expectedAdCount);
    }

    private void assertNoExtensions(String vastXml, String adId) throws XPathExpressionException {
        NodeList creatives = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']//Extension")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(creatives.getLength()).isEqualTo(0);
    }

    private void assertExtensions(String vastXml, String adId, int fallbackIndex) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because extension will only be added in Wrapper.
        NodeList extensions = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']/Wrapper/Extensions/Extension")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(extensions.getLength()).isEqualTo(1);

        NamedNodeMap extensionAttr = extensions.item(0).getAttributes();
        assertThat(extensionAttr.getNamedItem("type").getNodeValue()).isEqualTo("waterfall");
        assertThat(extensionAttr.getNamedItem("fallback_index").getNodeValue()).isEqualTo("" + fallbackIndex);
    }

    private void assertFallbackOnNoAd(String vastXml, boolean hasFallbackOnNoAd, String adId)
            throws XPathExpressionException {

        String wrapperLookupAttr = hasFallbackOnNoAd ? "@fallbackOnNoAd='true'" : "not(@fallbackOnNoAd)";

        NodeList wrappers = (NodeList) XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']/Wrapper[" + wrapperLookupAttr + "]")
                .evaluate(new InputSource(new StringReader(vastXml)), XPathConstants.NODESET);
        assertThat(wrappers.getLength()).isEqualTo(1);
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
        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        return splitQuery(vastQueryParams.get("cust_params").get(0));
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

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(Arrays.asList(SingleImpTestData.builder()
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putStoredRequest(storedImpId)
                                                        .putBidder()
                                                        .putBidderKeyValue("placementId", placementIdOfStoredImp))
                                                .videoData(VideoTestParam.builder()
                                                        .w(640)
                                                        .h(480)
                                                        .mimes(Arrays.asList("video/mp4"))
                                                        .build())
                                                .build()))
                                        .channel(getExtPrebidChannelForGvast())
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                        .price(Double.parseDouble(price))
                                        .adm(improveAdm)
                                        .build()
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementIdOfStoredImp)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                cacheId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequestVideo(uniqueId, AuctionBidRequestVideoTestData.builder()
                        .currency("USD")
                        .impData(Arrays.asList(AuctionBidRequestImpVideoTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(storedImpId)
                                        .putImprovedigitalPbs()
                                        .putImprovedigitalPbsKeyValue("responseType", "vast"))
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountIsOneOrMore(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_0_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, Double.parseDouble(price));
        assertSeat(responseJson, 0, "improvedigital");
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequest(GvastAuctionTestParam param) throws IOException, JSONException {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(Arrays.asList(SingleImpTestData.builder()
                                                .impExt(param.toSSPBidRequestImpExt())
                                                .videoData(param.toVideoData())
                                                .build()))
                                        .channel(getExtPrebidChannelForGvast())
                                        .extRequestTargeting(getExtPrebidTargetingForGvast())
                                        .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                        .siteIABCategories(param.siteIabCategories)
                                        .gdprConsent(param.gdprConsent)
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                        .price(Double.parseDouble(param.improvePrice))
                                        .adm(param.improveAdm)
                                        .build()
                        )))
        );

        String cachedContent = getVastXmlToCache(
                param.improveAdm, "improvedigital", param.improvePrice, param.improvePlacementId
        );
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                cachedContent
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                param.improveCacheId
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase(param.improveCacheId))
                        .willReturn(aResponse()
                                .withBody(cachedContent))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequestVideo(uniqueId, AuctionBidRequestVideoTestData.builder()
                        .currency("USD")
                        .impData(Arrays.asList(AuctionBidRequestImpVideoTestData.builder()
                                .impExt(param.toAuctionBidRequestImpExt())
                                .videoProtocols(param.videoProtocols)
                                .build()))
                        .siteIABCategories(param.siteIabCategories)
                        .gdprConsent(param.gdprConsent)
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountIsOne(responseJson); /* As we are sending some bids from SSP, we will definitely get 1 bid. */
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_0_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequestWithMultipleImp(GvastMultipleImpAuctionTestParam... params)
            throws IOException, JSONException {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(Arrays.stream(params)
                                                .map(param -> SingleImpTestData.builder()
                                                        .impExt(param.toSSPBidRequestImpExt())
                                                        .videoData(param.toVideoData())
                                                        .build())
                                                .collect(Collectors.toList()))
                                        .channel(getExtPrebidChannelForGvast())
                                        .extRequestTargeting(getExtPrebidTargetingForGvast())
                                        .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", uniqueId, "USD", Arrays.stream(params)
                                        .map(param -> Arrays.asList(param.toBidResponseTestData()))
                                        .collect(Collectors.toList())
                        )))
        );

        List<String> cacheResponses = Arrays.stream(params)
                .map(param -> "\"" + param.toVastXmlToCache("improvedigital") + "\":\"" + param.improveCacheId + "\"")
                .collect(Collectors.toList());

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                        "request_id_" + uniqueId, Arrays.stream(params)
                                .map(param -> param.toVastXmlToCache("improvedigital"))
                                .collect(Collectors.toList()))
                ))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", createResourceFile(
                                "com/improvedigital/prebid/server/it/"
                                        + "test-gvast-multiple-bidder-cache-response.json",
                                "{" + String.join(",", cacheResponses) + "}"
                        ))
                )
        );

        String cacheStubScenario = "caching::get";
        for (int i = 0; i < params.length; i++) {
            String cachedContent = params[i].toVastXmlToCache("improvedigital");

            WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                    .inScenario(cacheStubScenario)
                    .whenScenarioStateIs(i == 0 ? Scenario.STARTED : "cache_" + i)
                    .withQueryParam("uuid", equalToIgnoreCase(params[i].improveCacheId))
                    .willReturn(aResponse().withBody(cachedContent))
                    .willSetStateTo(i == params.length - 1 ? Scenario.STARTED : "cache_" + (i + 1))
            );
        }

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequestVideo(uniqueId, AuctionBidRequestVideoTestData.builder()
                        .currency("USD")
                        .impData(Arrays.stream(params)
                                .map(param -> AuctionBidRequestImpVideoTestData.builder()
                                        .impExt(param.toAuctionBidRequestImpExt())
                                        .build())
                                .collect(Collectors.toList()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountIsOne(responseJson); /* As we are sending some bids from SSP, we will definitely get 1 bid. */
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_0_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastAuctionRequestToMultipleBidder(GvastMultipleBidderAuctionTestParam param)
            throws IOException, JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);
        String improveVastXmlToCache = improvePrice1Value > improvePrice2Value
                ? getVastXmlToCache(param.improveAdm1, "improvedigital", param.improvePrice1, param.improvePlacementId)
                : getVastXmlToCache(param.improveAdm2, "improvedigital", param.improvePrice2, param.improvePlacementId);

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);
        String genericVastXmlToCache = genericPrice1Value > genericPrice2Value
                ? getVastXmlToCache(param.genericAdm1, "generic", param.genericPrice1, param.improvePlacementId)
                : getVastXmlToCache(param.genericAdm2, "generic", param.genericPrice2, param.improvePlacementId);

        BidResponseBidExt improveDealBidExt = new BidResponseBidExt()
                .putBuyingType("classic")
                .putLineItemId(param.improvePlacementId * 10);

        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(Arrays.asList(SingleImpTestData.builder()
                                        .impExt(param.toImproveSSPBidRequestImpExt())
                                        .videoData(param.toVideoData())
                                        .build()))
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .build()
                )))
                .willReturn(aResponse().withBody(getBidResponse(
                        "improvedigital", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .price(improvePrice1Value)
                                .adm(param.improveAdm1)
                                .bidExt(param.improveReturnsDeal && improvePrice1Value > improvePrice2Value
                                        ? improveDealBidExt
                                        : null)
                                .build(),
                        BidResponseTestData.builder()
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
                                .impData(Arrays.asList(SingleImpTestData.builder()
                                        .impExt(param.toGenericSSPBidRequestImpExt())
                                        .videoData(param.toVideoData())
                                        .build()))
                                .channel(getExtPrebidChannelForGvast())
                                .extRequestTargeting(getExtPrebidTargetingForGvast())
                                .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                .build()
                )))
                .willReturn(aResponse().withBody(getBidResponse("generic", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .price(genericPrice1Value)
                                .adm(param.genericAdm1)
                                .build(),
                        BidResponseTestData.builder()
                                .price(genericPrice2Value)
                                .adm(param.genericAdm2)
                                .build()
                )))
        );

        List<String> cacheResponses = new ArrayList<>();
        if (param.isCacheFailForImprove) {
            cacheResponses.add("\"" + improveVastXmlToCache + "\":\"\"");
        } else {
            cacheResponses.add("\"" + improveVastXmlToCache + "\":\"" + param.improveCacheId + "\"");
        }
        cacheResponses.add("\"" + genericVastXmlToCache + "\":\"" + param.genericCacheId + "\"");

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                        "request_id_" + uniqueId,
                        improveVastXmlToCache,
                        genericVastXmlToCache
                )))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", createResourceFile(
                                "com/improvedigital/prebid/server/it/"
                                        + "test-gvast-multiple-bidder-cache-response.json",
                                "{" + String.join(",", cacheResponses) + "}"
                        ))
                )
        );

        // This mocked API should be called in the following order.
        String cacheStubScenario = "caching::get";
        String cacheStubNext = "next cache";
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                .inScenario(cacheStubScenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .withQueryParam("uuid", equalToIgnoreCase(param.improveCacheId))
                .willReturn(aResponse().withBody(improveVastXmlToCache))
                .willSetStateTo(cacheStubNext)
        );
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/cache"))
                .inScenario(cacheStubScenario)
                .whenScenarioStateIs(cacheStubNext)
                .withQueryParam("uuid", equalToIgnoreCase(param.genericCacheId))
                .willReturn(aResponse().withBody(genericVastXmlToCache))
                .willSetStateTo(Scenario.STARTED)
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequestVideo(uniqueId, AuctionBidRequestVideoTestData.builder()
                        .currency("USD")
                        .impData(Arrays.asList(AuctionBidRequestImpVideoTestData.builder()
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
        assertBidCountIsOne(responseJson); /* As we are sending some bids from SSP, we will definitely get 1 bid. */
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_0_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private JSONObject doCustomVastRequestWhenSSPReturnsNoBid(
            String responseType, String uniqueId, int placementId
    ) throws JSONException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(Arrays.asList(SingleImpTestData.builder()
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putBidder()
                                                        .putBidderKeyValue("placementId", placementId))
                                                .videoData(VideoTestParam.builder()
                                                        .w(640)
                                                        .h(480)
                                                        .mimes(Arrays.asList("video/mp4"))
                                                        .build())
                                                .build()))
                                        .channel(getExtPrebidChannelForGvast())
                                        .extRequestTargeting(getExtPrebidTargetingForGvast())
                                        .extRequestPrebidCache(getExtPrebidCacheForGvast())
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", uniqueId, "USD"
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getAuctionBidRequestVideo(uniqueId, AuctionBidRequestVideoTestData.builder()
                        .currency("USD")
                        .impData(Arrays.asList(AuctionBidRequestImpVideoTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putImprovedigitalPbs()
                                        .putImprovedigitalPbsKeyValue("responseType", responseType)
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", placementId))
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        return new JSONObject(response.asString());
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
                        ExtPriceGranularity.of(2, Arrays.asList(
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
        return vastXml
                .replace(
                        "</InLine>",
                        "<Impression>"
                                + "<![CDATA[https://it.pbs.com/ssp_bids?bidder=" + bidder + "&cpm=" + cpm + "&pid=" + placementId + "]]>"
                                + "</Impression>"
                                + "</InLine>"
                )
                .replace("\"", "\\\"");
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

    /**
     * Class to deal with many permutation/combination of gvast request/response parameters.
     * This is to avoid long method parameter names code smell.
     */
    @Builder(toBuilder = true)
    public static class GvastAuctionTestParam {
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

        public AuctionBidRequestImpExt toAuctionBidRequestImpExt() {
            AuctionBidRequestImpExt auctionImpExt = new AuctionBidRequestImpExt()
                    .putImprovedigitalPbs()
                    .putImprovedigitalPbsKeyValue("responseType", responseType)
                    .putImprovedigitalPbsKeyValue("gam", gamParams)
                    .putImprovedigitalPbsKeyValue("waterfall", defaultWaterfalls == null
                            ? null : Map.of("default", defaultWaterfalls));

            if (storedImpId != null) {
                auctionImpExt.putStoredRequest(storedImpId);
            } else {
                auctionImpExt
                        .putBidder("improvedigital")
                        .putBidderKeyValue("improvedigital", "placementId", improvePlacementId)
                        .putBidderKeyValue("improvedigital", "keyValues", improveCustomKeyValues);
            }

            return auctionImpExt;
        }

        VideoTestParam toVideoData() {
            return VideoTestParam.builder()
                    .w(640)
                    .h(480)
                    .mimes(Arrays.asList("video/mp4"))
                    .protocols(videoProtocols)
                    .build();
        }
    }

    /**
     * Class to deal with many permutation/combination of gvast request/response parameters for multiple imp request.
     * This is to avoid long method parameter names code smell.
     */
    @Builder(toBuilder = true)
    public static class GvastMultipleImpAuctionTestParam {
        String responseType;
        String storedImpId;
        int improvePlacementId;
        String improveAdm;
        String improvePrice;
        String improveCacheId;

        SSPBidRequestImpExt toSSPBidRequestImpExt() {
            return new SSPBidRequestImpExt()
                    .putStoredRequest(storedImpId)
                    .putBidder()
                    .putBidderKeyValue("placementId", improvePlacementId);
        }

        AuctionBidRequestImpExt toAuctionBidRequestImpExt() {
            AuctionBidRequestImpExt auctionImpExt = new AuctionBidRequestImpExt()
                    .putImprovedigitalPbs()
                    .putImprovedigitalPbsKeyValue("responseType", responseType);

            if (storedImpId != null) {
                auctionImpExt.putStoredRequest(storedImpId);
            } else {
                auctionImpExt
                        .putBidder("improvedigital")
                        .putBidderKeyValue("improvedigital", "placementId", improvePlacementId);
            }

            return auctionImpExt;
        }

        BidResponseTestData toBidResponseTestData() {
            return BidResponseTestData.builder()
                    .price(Double.parseDouble(improvePrice))
                    .adm(improveAdm)
                    .build();
        }

        String toVastXmlToCache(String bidderName) {
            return getVastXmlToCache(
                    improveAdm, bidderName, improvePrice, improvePlacementId
            );
        }

        VideoTestParam toVideoData() {
            return VideoTestParam.builder()
                    .w(640)
                    .h(480)
                    .mimes(Arrays.asList("video/mp4"))
                    .build();
        }
    }

    /**
     * Class to deal with many permutation/combination of gvast request/response parameters.
     * This is to avoid long method parameter names code smell.
     */
    @Builder(toBuilder = true)
    public static class GvastMultipleBidderAuctionTestParam {
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

        VideoTestParam toVideoData() {
            return VideoTestParam.builder()
                    .w(640)
                    .h(480)
                    .mimes(Arrays.asList("video/mp4"))
                    .build();
        }

        public SSPBidRequestImpExt toGenericSSPBidRequestImpExt() {
            return new SSPBidRequestImpExt()
                    .putBidder()
                    .putBidderKeyValue("exampleProperty", "examplePropertyValue");
        }
    }
}
