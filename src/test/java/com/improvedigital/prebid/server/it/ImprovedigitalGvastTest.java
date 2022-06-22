package com.improvedigital.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.improvedigital.prebid.server.handler.GVastHandler;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.it.util.BidCacheRequestPattern;
import org.prebid.server.model.Endpoint;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    public static final String IT_TEST_CACHE_URL = "http://localhost:8090/cache";

    @Test
    public void gvastHasProperQueryParamsInVastTagUri() throws XPathExpressionException, MalformedURLException {
        Response response = getGvastResponse();

        String vastAdTagUri = getVastTagUri(response.asString(), "0");
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());

        assertThat(vastQueryParams.get("gdfp_req").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdfp_req").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("env").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("env").get(0)).isEqualTo("vp");

        assertThat(vastQueryParams.get("unviewed_position_start").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("unviewed_position_start").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("correlator").size()).isEqualTo(1);
        assertThat(Long.parseLong(vastQueryParams.get("correlator").get(0)))
                .isGreaterThan(System.currentTimeMillis() - 2000);

        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("iu").get(0)).isEqualTo("/1015413/pbs/20220325");

        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("output").get(0)).isEqualTo("xml_vast2");

        assertThat(vastQueryParams.get("sz").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("sz").get(0)).isEqualTo("640x480|640x360");

        assertThat(vastQueryParams.get("description_url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("description_url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("url").get(0)).isEqualTo("http://pbs.improvedigital.com");

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

        assertThat(vastQueryParams.get("gdfp_req").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("gdfp_req").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("env").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("env").get(0)).isEqualTo("vp");

        assertThat(vastQueryParams.get("unviewed_position_start").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("unviewed_position_start").get(0)).isEqualTo("1");

        assertThat(vastQueryParams.get("correlator").size()).isEqualTo(1);
        assertThat(Long.parseLong(vastQueryParams.get("correlator").get(0)))
                .isGreaterThan(System.currentTimeMillis() - 2000);

        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("iu").get(0)).isEqualTo("/1015413/pbs/20220325");

        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("output").get(0)).isEqualTo("xml_vast2");

        assertThat(vastQueryParams.get("sz").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("sz").get(0)).isEqualTo("640x480|640x360");

        assertThat(vastQueryParams.get("description_url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("description_url").get(0)).isEqualTo("http://pbs.improvedigital.com");

        assertThat(vastQueryParams.get("url").size()).isEqualTo(1);
        assertThat(vastQueryParams.get("url").get(0)).isEqualTo("com.improvedigital.ittests.adsenseformobileapps.com/");

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
        Response response = getVastResponseFromAuction(
                getVastXmlInline("20220608", true)
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id");
        assertBidPrice(responseJson, 0, 0, 1.25);
        assertSeat(responseJson, 0, "improvedigital");
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we had on creative.
        String mediaUrl = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220608']/InLine/Creatives"
                        + "/Creative[@AdID='20220608']/Linear/MediaFiles/MediaFile[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(mediaUrl.trim()).isEqualTo("https://media.pbs.improvedigital.com/20220608.mp4");
    }

    @Test
    public void auctionEndpointReturnsGvastResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String vastXmlWillBeCached = getVastXmlToCache(vastXml, "improvedigital", "1.08", "20220608");
        String uniqueId = "R3WOPUPAGZYVYLA02ONHOGPANWYVX2D";

        Response response = getGVastResponseFromAuction(
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_gvast_20220608");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");

        assertGamUrl(uniqueId, vastAdTagUri);
        assertGamCachedContent(uniqueId, vastXmlWillBeCached);
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
    }

    @Test
    public void auctionEndpointReturnsGvastResponseWithMultipleWaterfallConfig()
            throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String vastXmlWillBeCached = getVastXmlToCache(vastXml, "improvedigital", "1.08", "20220608");
        String uniqueId = "R3WOPUPAGZYVYLA02ONHOGPANWYVX2D";

        Response response = getGVastResponseFromAuctionWithWaterfallConfig(
                Arrays.asList("gam_first_look", "gam", "gam_first_look", "https://my.customvast.xml"),
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_gvast_20220608");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamFirstLookUrl(vastAdTagUri1);
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertExtensions(adm, "0", 0);

        // 2nd tag.
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        assertGamUrl(uniqueId, vastAdTagUri2);
        assertGamCachedContent(uniqueId, vastXmlWillBeCached);
        assertSSPSyncPixels(adm, "1");
        assertNoCreative(adm, "1");
        assertExtensions(adm, "1", 1);

        // 3rd tag.
        String vastAdTagUri3 = getVastTagUri(adm, "2");
        assertGamFirstLookUrl(vastAdTagUri3);
        assertSSPSyncPixels(adm, "2");
        assertNoCreative(adm, "2");
        assertExtensions(adm, "2", 2);

        // 4th tag.
        String vastAdTagUri4 = getVastTagUri(adm, "3");
        assertThat(vastAdTagUri4.trim()).isEqualTo("https://my.customvast.xml");
        assertNoSSPSyncPixels(adm, "3");
        assertNoCreative(adm, "3");
        assertExtensions(adm, "3", 3);
    }

    @Test
    public void auctionEndpointReturnsGvastResponseWithMacroReplacement()
            throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String vastXmlWillBeCached = getVastXmlToCache(vastXml, "improvedigital", "1.08", "20220608");
        String uniqueId = "OLJLUADDQM7Y4T8IF8YU6ZWDU1FSESZC";

        Response response = getGVastResponseFromAuctionWithWaterfallConfig(
                Arrays.asList("gam", "https://my.customvast.xml"
                        + "?gdpr={{gdpr}}"
                        + "&gdpr_consent={{gdpr_consent}}"
                        + "&referrer={{referrer}}"
                        + "&t={{timestamp}}"),
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_gvast_20220608");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamUrl(uniqueId, vastAdTagUri1);

        // 2nd tag.
        String vastAdTagUri2 = getVastTagUri(adm, "1");
        Map<String, List<String>> customUrlParams = splitQuery(new URL(vastAdTagUri2).getQuery());
        assertQuerySingleValue(customUrlParams.get("gdpr"), "0");
        assertQuerySingleValue(customUrlParams.get("gdpr_consent"), "");
        assertQuerySingleValue(customUrlParams.get("referrer"), "http://pbs.improvedigital.com");
        assertThat(Long.parseLong(customUrlParams.get("t").get(0)) > (System.currentTimeMillis() - 5 * 60 * 1000L));
        assertThat(Long.parseLong(customUrlParams.get("t").get(0)) < System.currentTimeMillis());
    }

    @Test
    public void auctionEndpointReturnsWaterfallResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String vastXmlWillBeCached = getVastXmlToCache(vastXml, "improvedigital", "1.13", "20220608");
        String uniqueId = "KCZEL1JSW8BT296EE1FYXTCKWNGWLVBJ";

        Response response = getWaterfallResponseFromAuction(
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_waterfall_20220608");
        assertBidPrice(responseJson, 0, 0, 0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");
        assertThat(vastAdTagUri.trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);

        // Hit the cache.
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=" + uniqueId, vastXmlWillBeCached);

        assertSSPSyncPixels(adm, "0");
    }

    @Test
    public void auctionEndpointReturnsWaterfallResponseWithMultipleBidder()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, we will cache improvedigital's 1st bid and generic's 2nd bid.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml1, "improvedigital", "1.17", "20220615");
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml2, "generic", "1.05", "20220615");
        String improveCacheId = "IHBWUJGAWHNKZMDAJYAKSVTGYSNV839Q";
        String genericCacheId = "AL3HQONPPWDWI57ZLRKBTMDJYFLRPSM8";

        Response response = getWaterfallResponseFromAuctionOfMultipleBidder(
                improveVastXml1, improveVastXml2, improveCacheId, improveVastXmlToCache,
                genericVastXml1, genericVastXml2, genericCacheId, genericVastXmlToCache
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_waterfall_multiple_bidder");
        assertBidPrice(responseJson, 0, 0, 0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag.
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

        // 1st tag should be improvedigital's because of hb_deal_improvedigit.
        // Note: Because of WireMock's scenario implementation, we must call /cache in this order.
        assertCachedContent(vastAdTagUri1.trim(), improveVastXmlToCache);
        assertCachedContent(vastAdTagUri2.trim(), genericVastXmlToCache);
    }

    @Test
    public void moreTests() {
        // gam + deal: In that case, auto-add: gam_no_hb
        // Using /prebid/bidder/improvedigital/keyValues.
        // On test=1, we get debug lines.
        // Resolving of "output".
        // Resolving of "iu".
        // Use gdpr_consent=BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA
        // For last ad, <Wrapper fallbackOnNoAd="true">
        // iab_cat
        // pbct
        // Bidder sends Wrapper.
        // Bid discarded when vastxml is not cached.
    }

    private String getVastTagUri(String adm, String adId) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because vast tag uri will only appear in Wrapper.
        return XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(adm)));
    }

    private void assertGamUrl(String uniqueId, String vastAdTagUri) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "improvedigital");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedig"), "improvedigital");

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), uniqueId);

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");

        assertQuerySingleValue(custParams.get("hb_pb"), "1.08");
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), "1.08");

        assertThat("http://"
                + custParams.get("hb_cache_host").get(0)
                + custParams.get("hb_cache_path").get(0)
                + "?uuid=" + custParams.get("hb_uuid").get(0)).isEqualTo(IT_TEST_CACHE_URL + "?uuid=" + uniqueId);

        assertThat(custParams.get("hb_cache_host").get(0))
                .isEqualTo(custParams.get("hb_cache_host_improv").get(0));
        assertThat(custParams.get("hb_cache_path").get(0))
                .isEqualTo(custParams.get("hb_cache_path_improv").get(0));
    }

    private void assertGamFirstLookUrl(String vastAdTagUri) throws MalformedURLException {
        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("fl"), "1");
        assertQuerySingleValue(custParams.get("tnl_wog"), "1");
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

    private Response getVastResponseFromAuction(String improveAdm) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-vast-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-vast-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM", improveAdm)
                        )))
        );

        return specWithPBSHeader(18080)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-vast-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response getGVastResponseFromAuction(
            String improveAdm,
            String cacheId,
            String vastXmlWillBeCached) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM", improveAdm)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-cache-request.json",
                                Map.of("IT_TEST_CACHE_VALUE", vastXmlWillBeCached)
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-cache-response.json",
                                Map.of("IT_TEST_CACHE_UUID", cacheId)
                        )))
        );
        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase(cacheId))
                        .willReturn(aResponse()
                                .withBody(vastXmlWillBeCached))
        );

        return specWithPBSHeader(18080)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-gvast-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response getGVastResponseFromAuctionWithWaterfallConfig(
            List<String> defaultWaterfalls,
            String improveAdm,
            String cacheId,
            String vastXmlWillBeCached) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM", improveAdm)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-cache-request.json",
                                Map.of("IT_TEST_CACHE_VALUE", vastXmlWillBeCached)
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-cache-response.json",
                                Map.of("IT_TEST_CACHE_UUID", cacheId)
                        )))
        );
        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase(cacheId))
                        .willReturn(aResponse()
                                .withBody(vastXmlWillBeCached))
        );

        return specWithPBSHeader(18080)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-gvast-auction-improvedigital-request-waterfall.json",
                        Map.of("IT_TEST_WATERFALL_DEFAULT_LIST", String.join("\",\"", defaultWaterfalls))
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response getWaterfallResponseFromAuction(
            String improveAdm,
            String cacheId,
            String vastXmlWillBeCached) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-waterfall-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-waterfall-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM", improveAdm)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-improvedigital-cache-request.json",
                                Map.of("IT_TEST_CACHE_VALUE", vastXmlWillBeCached)
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-improvedigital-cache-response.json",
                                Map.of("IT_TEST_CACHE_UUID", cacheId)
                        )))
        );
        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase(cacheId))
                        .willReturn(aResponse()
                                .withBody(vastXmlWillBeCached))
        );

        return specWithPBSHeader(18080)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-waterfall-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response getWaterfallResponseFromAuctionOfMultipleBidder(
            String improveAdm1,
            String improveAdm2,
            String improveCacheId,
            String improveVastXmlToCache,
            String genericAdm1,
            String genericAdm2,
            String genericCacheId,
            String genericVastXmlToCache) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-multiple-bidder-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-multiple-bidder-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM_1", improveAdm1, "IT_TEST_MACRO_ADM_2", improveAdm2)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/generic-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-multiple-bidder-generic-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-multiple-bidder-generic-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM_1", genericAdm1, "IT_TEST_MACRO_ADM_2", genericAdm2)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(new BidCacheRequestPattern(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-waterfall-multiple-bidder-cache-request.json",
                                Map.of(
                                        "IT_TEST_CACHE_VALUE_1", improveVastXmlToCache,
                                        "IT_TEST_CACHE_VALUE_2", genericVastXmlToCache
                                )
                        )))
                        .willReturn(aResponse()
                                .withTransformers("cache-response-transformer")
                                .withTransformerParameter("matcherName", createResourceFile(
                                        "com/improvedigital/prebid/server/it/"
                                                + "test-waterfall-multiple-bidder-cache-response.json",
                                        "{"
                                                + "\"" + improveVastXmlToCache + "\":\"" + improveCacheId + "\","
                                                + "\"" + genericVastXmlToCache + "\":\"" + genericCacheId + "\""
                                                + "}"
                                ))
                        )
        );

        // This mocked API should be called in the following order.
        String cacheStubScenario = "caching::get";
        String cacheStubNext = "next cache";
        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .inScenario(cacheStubScenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .withQueryParam("uuid", equalToIgnoreCase(improveCacheId))
                        .willReturn(aResponse()
                                .withBody(improveVastXmlToCache))
                        .willSetStateTo(cacheStubNext)
        );
        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .inScenario(cacheStubScenario)
                        .whenScenarioStateIs(cacheStubNext)
                        .withQueryParam("uuid", equalToIgnoreCase(genericCacheId))
                        .willReturn(aResponse()
                                .withBody(genericVastXmlToCache))
                        .willSetStateTo(Scenario.STARTED)
        );

        return specWithPBSHeader(18080)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/"
                                + "test-waterfall-multiple-bidder-auction-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private void assertGamCachedContent(String uniqueId, String expectedCachedContent) throws IOException {
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=" + uniqueId, expectedCachedContent);
    }

    private void assertCachedContent(String cacheUrl, String expectedCachedContent) throws IOException {
        assertThat(IOUtils.toString(
                HttpClientBuilder.create().build().execute(
                        new HttpGet(cacheUrl)
                ).getEntity().getContent(), "UTF-8"
        )).isEqualTo(expectedCachedContent);
    }

    private String getVastXmlToCache(String vastXml, String bidder, String cpm, String placementId) {
        return vastXml.replace(
                "</InLine>",
                "<Impression>"
                        + "<![CDATA[https://it.pbs.com/ssp_bids?bidder=" + bidder + "&cpm=" + cpm + "&pid=" + placementId + "]]>"
                        + "</Impression>"
                        + "</InLine>"
        );
    }
}
