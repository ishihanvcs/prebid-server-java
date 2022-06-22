package com.improvedigital.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.improvedigital.prebid.server.handler.GVastHandler;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.it.util.BidCacheRequestPattern;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
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
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public static final String GAM_NETWORK_CODE = "1015413";

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
        JSONObject responseJson = getVastResponseFromAuction(
                getVastXmlInline("20220608", true), "1.25", "20220608", 20220608
        );
        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we had on creative.
        String mediaUrl = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220608']/InLine/Creatives"
                        + "/Creative[@AdID='20220608']/Linear/MediaFiles/MediaFile[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(mediaUrl.trim()).isEqualTo("https://media.pbs.improvedigital.com/20220608.mp4");
    }

    private JSONObject getVastResponseFromAuction(
            String improveAdm, String price, String storedImpId, int placementIdOfStoredImp
    ) throws IOException, JSONException {
        String uniqueId = UUID.randomUUID().toString();
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementIdOfStoredImp, storedImpId, false, null, null, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementIdOfStoredImp)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideoWithStoredImp(
                        uniqueId, "vast", storedImpId, null, null
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, Double.parseDouble(price));
        assertSeat(responseJson, 0, "improvedigital");
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    @Test
    public void auctionEndpointReturnsGvastResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = getGVastResponseFromAuction(
                uniqueId, "20220608", 20220608, "1.08", vastXml, null
        );

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");

        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri, "cache_id_" + uniqueId, "20220608", "1.08"
        );
        assertCachedContentFromCacheId("cache_id_" + uniqueId, getVastXmlToCache(
                vastXml, "improvedigital", "1.08", 20220608
        ));
        assertSSPSyncPixels(adm, "0");
        assertNoCreative(adm, "0");
        assertNoExtensions(adm, "0");
    }

    @Test
    public void auctionEndpointReturnsGvastResponseWithMultipleWaterfallConfig()
            throws XPathExpressionException, IOException, JSONException {

        String vastXml = getVastXmlInline("20220608", true);
        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = getGVastResponseFromAuction(
                uniqueId,
                "20220608",
                20220608,
                "1.09",
                vastXml,
                Arrays.asList(
                        "gam_first_look",
                        "gam",
                        "gam_first_look",
                        "https://my.customvast.xml",
                        "gam_no_hb",
                        "gam_improve_deal"
                )
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
                vastAdTagUri2, "cache_id_" + uniqueId, "20220608", "1.09"
        );
        assertCachedContentFromCacheId("cache_id_" + uniqueId, getVastXmlToCache(
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

        // 5th tag = gam_no_hb
        String vastAdTagUri5 = getVastTagUri(adm, "4");
        assertGamNoHbUrl(vastAdTagUri5, "20220608");
        assertSSPSyncPixels(adm, "4");
        assertNoCreative(adm, "4");
        assertExtensions(adm, "4", 4);

        // 6th tag = gam_improve_deal
        String vastAdTagUri6 = getVastTagUri(adm, "5");
        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri6, "cache_id_" + uniqueId, "20220608", "1.09"
        );
        assertSSPSyncPixels(adm, "5");
        assertNoCreative(adm, "5");
        assertExtensions(adm, "5", 5);
    }

    @Test
    public void auctionEndpointReturnsGvastResponseWithMacroReplacement()
            throws XPathExpressionException, IOException, JSONException {

        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = getGVastResponseFromAuction(
                uniqueId,
                "20220608",
                20220608,
                "1.11",
                getVastXmlInline("20220608", true),
                Arrays.asList("gam", "https://my.customvast.xml"
                        + "?gdpr={{gdpr}}"
                        + "&gdpr_consent={{gdpr_consent}}"
                        + "&referrer={{referrer}}"
                        + "&t={{timestamp}}")
        );

        String adm = getAdm(responseJson, 0, 0);

        // 1st tag.
        String vastAdTagUri1 = getVastTagUri(adm, "0");
        assertGamUrlWithImprovedigitalAsSingleBidder(
                vastAdTagUri1, "cache_id_" + uniqueId, "20220608", "1.11"
        );

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
    public void auctionEndpointReturnsGvastResponseWithMultipleBidder()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 2nd bid and generic's 1st bid will be picked and cached.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml2, "improvedigital", "1.75", 20220617);
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml1, "generic", "1.95", 20220617);
        String improveCacheId = "SIKOI8GL6PHU5LEV01ZLBGNZPPDX6ZZF";
        String genericCacheId = "YXUZCNMFFG0GYSAYSMTLXNI1BMSS3J5Y";

        Response response = getGVastResponseFromAuctionOfMultipleBidder(
                Arrays.asList("gam_first_look", "gam"),
                improveVastXml1.replace("\"", "\\\""), improveVastXml2.replace("\"", "\\\""), improveCacheId, improveVastXmlToCache,
                genericVastXml1.replace("\"", "\\\""), genericVastXml2.replace("\"", "\\\""), genericCacheId, genericVastXmlToCache,
                false
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_gvast_multiple_bidder");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

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
    public void auctionEndpointReturnsGvastResponseWithMultipleBidderAndDeal()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 2nd bid and generic's 1st bid will be picked and cached.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml2, "improvedigital", "1.75", 20220617);
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml1, "generic", "1.95", 20220617);
        String improveCacheId = "SIKOI8GL6PHU5LEV01ZLBGNZPPDX6ZZF";
        String genericCacheId = "YXUZCNMFFG0GYSAYSMTLXNI1BMSS3J5Y";

        Response response = getGVastResponseFromAuctionOfMultipleBidder(
                Arrays.asList("gam_first_look", "gam"),
                improveVastXml1.replace("\"", "\\\""), improveVastXml2.replace("\"", "\\\""), improveCacheId, improveVastXmlToCache,
                genericVastXml1.replace("\"", "\\\""), genericVastXml2.replace("\"", "\\\""), genericCacheId, genericVastXmlToCache,
                true
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_gvast_multiple_bidder");
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

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
    public void auctionEndpointReturnsWaterfallResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlInline("20220608", true);
        String uniqueId = UUID.randomUUID().toString();

        JSONObject responseJson = getWaterfallResponseFromAuction(
                uniqueId, "20220608", 20220608, "1.13", vastXml
        );

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = getVastTagUri(adm, "0");
        assertThat(vastAdTagUri.trim()).isEqualTo(IT_TEST_CACHE_URL + "?uuid=cache_id_" + uniqueId);

        // Hit the cache.
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=cache_id_" + uniqueId, getVastXmlToCache(
                vastXml, "improvedigital", "1.13", 20220608
        ));

        assertSSPSyncPixels(adm, "0");
    }

    @Test
    public void auctionEndpointReturnsWaterfallResponseWithMultipleBidderAndDeal()
            throws XPathExpressionException, IOException, JSONException {
        String improveVastXml1 = getVastXmlInline("improve_ad_1", true);
        String improveVastXml2 = getVastXmlInline("improve_ad_2", true);

        String genericVastXml1 = getVastXmlInline("generic_ad_1", false);
        String genericVastXml2 = getVastXmlInline("generic_ad_2", false);

        // Prebid core will select 1 bid, having the highest price, from each bidder.
        // Hence, improvedigital's 1st bid and generic's 2nd bid will be picked and cached.
        String improveVastXmlToCache = getVastXmlToCache(improveVastXml1, "improvedigital", "1.17", 20220615);
        String genericVastXmlToCache = getVastXmlToCache(genericVastXml2, "generic", "1.05", 20220615);
        String improveCacheId = "IHBWUJGAWHNKZMDAJYAKSVTGYSNV839Q";
        String genericCacheId = "AL3HQONPPWDWI57ZLRKBTMDJYFLRPSM8";

        Response response = getWaterfallResponseFromAuctionOfMultipleBidder(
                improveVastXml1.replace("\"", "\\\""), improveVastXml2.replace("\"", "\\\""), improveCacheId, improveVastXmlToCache,
                genericVastXml1.replace("\"", "\\\""), genericVastXml2.replace("\"", "\\\""), genericCacheId, genericVastXmlToCache
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_it_waterfall_multiple_bidder");
        assertBidPrice(responseJson, 0, 0, 0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");

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
    public void auctionEndpointReturnsGvastResponseWithProperAdUnit() throws XPathExpressionException, IOException, JSONException {
        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/" + GAM_NETWORK_CODE + "/pbs/20220618");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "", "childNetworkCode", "DEF")
        )).isEqualTo("/" + GAM_NETWORK_CODE + ",DEF/pbs/20220618");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "ABC", "childNetworkCode", "")
        )).isEqualTo("/ABC/pbs/20220618");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/ABC,DEF/pbs/20220618");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "/XYZ", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/XYZ");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "/XYZ", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/XYZ");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "", "childNetworkCode", "")
        )).isEqualTo("/" + GAM_NETWORK_CODE + "/XYZ");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "", "childNetworkCode", "DEF")
        )).isEqualTo("/" + GAM_NETWORK_CODE + ",DEF/XYZ");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "ABC", "childNetworkCode", "")
        )).isEqualTo("/ABC/XYZ");

        assertThat(doGvastRequestAndGetAdUnitParam(
                getVastXmlInline("20220618", true), "1.12", 20220618,
                Map.of("adUnit", "XYZ", "networkCode", "ABC", "childNetworkCode", "DEF")
        )).isEqualTo("/ABC,DEF/XYZ");
    }

    private String doGvastRequestAndGetAdUnitParam(
            String improveAdm, String price, int placementId,
            Map<String, String> gamParams
    ) throws IOException, JSONException, XPathExpressionException {
        String uniqueId = UUID.randomUUID().toString();
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementId, null, true, null, null, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementId)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideo(
                        uniqueId, "gvast", placementId, gamParams, null, null, null, null)
                )
                .post(Endpoint.openrtb2_auction.value());

        String adm = getAdm(new JSONObject(response.asString()), 0, 0);

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("iu").size()).isEqualTo(1);
        return vastQueryParams.get("iu").get(0);
    }

    @Test
    public void auctionEndpointReturnsGvastResponseWithProtocol() throws XPathExpressionException, IOException, JSONException {
        assertThat(doGvastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("20220620", true), "0.97", 20220620,
                Arrays.asList(2, 3, 7)
        )).isEqualTo("xml_vast4");

        assertThat(doGvastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("20220620", true), "0.97", 20220620,
                Arrays.asList(2, 3)
        )).isEqualTo("xml_vast3");

        assertThat(doGvastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("20220620", true), "0.97", 20220620,
                Arrays.asList(2)
        )).isEqualTo("xml_vast2");

        assertThat(doGvastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("20220620", true), "0.97", 20220620,
                Arrays.asList(1)
        )).isEqualTo("vast");

        assertThat(doGvastRequestWithProtocolAndGetOutputParam(
                getVastXmlInline("20220620", true), "0.97", 20220620,
                Arrays.asList(1, 4, 5, 6, 8, 9, 10)
        )).isEqualTo("vast");
    }

    private String doGvastRequestWithProtocolAndGetOutputParam(
            String improveAdm, String price, int placementId, List<Integer> protocols
    ) throws IOException, JSONException, XPathExpressionException {
        String uniqueId = UUID.randomUUID().toString();
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementId, null, true, protocols, null, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementId)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideo(
                        uniqueId, "gvast", placementId, null, null, protocols, null, null)
                )
                .post(Endpoint.openrtb2_auction.value());

        String adm = getAdm(new JSONObject(response.asString()), 0, 0);

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("output").size()).isEqualTo(1);
        return vastQueryParams.get("output").get(0);
    }

    @Test
    public void testGvastResponseWithSiteCategory() throws XPathExpressionException, IOException, JSONException {
        assertThat(doGvastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("20220620", true), "0.85", 20220620,
                Arrays.asList()
        )).isNull();

        assertThat(doGvastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("20220620", true), "0.85", 20220620,
                Arrays.asList("IAB1")
        )).isEqualTo("IAB1");

        assertThat(doGvastRequestWithSiteCategoryAndGetCategoryOfCustParam(
                getVastXmlInline("20220620", true), "0.85", 20220620,
                Arrays.asList("IAB1", "IAB2")
        )).isEqualTo("IAB1,IAB2");
    }

    private String doGvastRequestWithSiteCategoryAndGetCategoryOfCustParam(
            String improveAdm, String price, int placementId, List<String> siteIabCategories
    ) throws IOException, JSONException, XPathExpressionException {
        String uniqueId = UUID.randomUUID().toString();
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementId, null, true, null, siteIabCategories, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementId)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideo(
                        uniqueId, "gvast", placementId, null, null, null, siteIabCategories, null
                ))
                .post(Endpoint.openrtb2_auction.value());

        String adm = getAdm(new JSONObject(response.asString()), 0, 0);

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
        Map<String, List<String>> custParams = doGvastRequestWithCustomKeyValuesAndGetCustParam(
                getVastXmlInline("20220620", false), "0.75", 20220620, Map.of(
                        "key1", "value1", "key2", "value2"
                )
        );
        assertThat(custParams.get("key1").size()).isEqualTo(1);
        assertThat(custParams.get("key1").get(0)).isEqualTo("value1");

        assertThat(custParams.get("key2").size()).isEqualTo(1);
        assertThat(custParams.get("key2").get(0)).isEqualTo("value2");
    }

    private Map<String, List<String>> doGvastRequestWithCustomKeyValuesAndGetCustParam(
            String improveAdm, String price, int placementId, Map<String, String> customKeyValues
    ) throws IOException, JSONException, XPathExpressionException {
        String uniqueId = UUID.randomUUID().toString();
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementId, null, true, null, null, customKeyValues
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                getVastXmlToCache(improveAdm, "improvedigital", price, placementId)
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideo(
                        uniqueId, "gvast", placementId, null, null, null, null, customKeyValues
                ))
                .post(Endpoint.openrtb2_auction.value());

        String adm = getAdm(new JSONObject(response.asString()), 0, 0);

        Map<String, List<String>> vastQueryParams = splitQuery(getVastTagUri(adm, "0"));
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);
        return splitQuery(vastQueryParams.get("cust_params").get(0));
    }

    @Test
    public void testGvastResponseWithFallbackOnNoAd() {
    }

    @Test
    public void moreTests() {
        // Use gdpr_consent=BOEFEAyOEFEAyAHABDENAI4AAAB9vABAASA
        // For last ad, <Wrapper fallbackOnNoAd="true">
        // Bidder sends Wrapper.
        // Bid discarded when vastxml is not cached.
    }

    private String getVastTagUri(String adm, String adId) throws XPathExpressionException {
        // Looking only for Wrapper (not InLine) because vast tag uri will only appear in Wrapper.
        return XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='" + adId + "']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(adm)));
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

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), uniqueId);

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");

        assertQuerySingleValue(custParams.get("hb_pb"), price);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), price);

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

        assertQuerySingleValue(custParams.get("hb_uuid"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_generic"), genericCacheId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigit"), improveCacheId);

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_generic"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedig"), "video");

        assertQuerySingleValue(custParams.get("hb_pb"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_generic"), genericPrice);
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), improvePrice);

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

    private JSONObject getGVastResponseFromAuction(
            String uniqueId,
            String storedImpId,
            int placementIdOfStoredImp,
            String price,
            String improveAdm,
            List<String> defaultWaterfalls
    ) throws IOException, JSONException {

        String cachedContent = getVastXmlToCache(improveAdm, "improvedigital", price, placementIdOfStoredImp);
        Map<String, List<String>> waterfalls = defaultWaterfalls == null ? null : Map.of("default", defaultWaterfalls);

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementIdOfStoredImp, storedImpId, true, null, null, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                cachedContent
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase("cache_id_" + uniqueId))
                        .willReturn(aResponse()
                                .withBody(cachedContent))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideoWithStoredImp(
                        uniqueId, "gvast", storedImpId, null, waterfalls
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 0.0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
    }

    private Response getGVastResponseFromAuctionOfMultipleBidder(
            List<String> defaultWaterfalls,
            String improveAdm1,
            String improveAdm2,
            String improveCacheId,
            String improveVastXmlToCache,
            String genericAdm1,
            String genericAdm2,
            String genericCacheId,
            String genericVastXmlToCache,
            boolean improveReturnsDeal) throws IOException {

        String improveBidResponseFile = improveReturnsDeal
                ? "test-gvast-multiple-bidder-improvedigital-bid-response-deal.json"
                : "test-gvast-multiple-bidder-improvedigital-bid-response.json";

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-gvast-multiple-bidder-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/" + improveBidResponseFile,
                                Map.of("IT_TEST_MACRO_ADM_1", improveAdm1, "IT_TEST_MACRO_ADM_2", improveAdm2)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/generic-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-gvast-multiple-bidder-generic-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-gvast-multiple-bidder-generic-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM_1", genericAdm1, "IT_TEST_MACRO_ADM_2", genericAdm2)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                                "request_id_it_gvast_multiple_bidder",
                                improveVastXmlToCache,
                                genericVastXmlToCache
                        )))
                        .willReturn(aResponse()
                                .withTransformers("cache-response-transformer")
                                .withTransformerParameter("matcherName", createResourceFile(
                                        "com/improvedigital/prebid/server/it/"
                                                + "test-gvast-multiple-bidder-cache-response.json",
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
                                + "test-gvast-multiple-bidder-auction-request.json",
                        Map.of("IT_TEST_WATERFALL_DEFAULT_LIST", String.join("\",\"", defaultWaterfalls))
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private JSONObject getWaterfallResponseFromAuction(
            String uniqueId,
            String storedImpId,
            int placementIdOfStoredImp,
            String price,
            String improveAdm) throws IOException, JSONException {

        String cachedContent = getVastXmlToCache(improveAdm, "improvedigital", price, placementIdOfStoredImp);

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getBidRequestVideoForSSPImprovedigital(
                                uniqueId, "USD", placementIdOfStoredImp, storedImpId, true, null, null, null
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                uniqueId, "USD", Double.parseDouble(price), improveAdm
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(createCacheRequest(
                                "request_id_" + uniqueId,
                                cachedContent
                        )))
                        .willReturn(aResponse().withBody(createCacheResponse(
                                "cache_id_" + uniqueId
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                get(urlPathEqualTo("/cache"))
                        .withQueryParam("uuid", equalToIgnoreCase("cache_id_" + uniqueId))
                        .willReturn(aResponse()
                                .withBody(cachedContent))
        );

        Response response = specWithPBSHeader(18080)
                .body(getBidRequestVideoWithStoredImp(
                        uniqueId, "waterfall", storedImpId, null, null
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountSingle(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 0);
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
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
                        .withRequestBody(new BidCacheRequestPattern(createCacheRequest(
                                "request_id_it_waterfall_multiple_bidder",
                                improveVastXmlToCache,
                                genericVastXmlToCache
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

    protected String getBidRequestVideoWithStoredImp(
            String uniqueId,
            String responseType,
            String storedImpId,
            Map<String, String> gamParams,
            Map<String, ?> waterfalls) {

        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(new AuctionBidRequestImpExt()
                                .putStoredRequest(storedImpId)
                                .putImprovedigitalPbs()
                                .putImprovedigitalPbsKeyValue("responseType", responseType)
                                .putImprovedigitalPbsKeyValue("gam", gamParams)
                                .putImprovedigitalPbsKeyValue("waterfall", waterfalls).getImpExt())
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .build())
                        .build()
        );
    }

    protected String getBidRequestVideo(
            String uniqueId,
            String responseType,
            Integer placementId,
            Map<String, String> gamParams,
            Map<String, ?> waterfalls,
            List<Integer> protocols,
            List<String> siteIabCategories,
            Map<String, String> customKeyValues) {

        AuctionBidRequestImpExt impExtBuilder = new AuctionBidRequestImpExt()
                .putImprovedigitalPbs()
                .putImprovedigitalPbsKeyValue("responseType", responseType)
                .putImprovedigitalPbsKeyValue("gam", gamParams)
                .putImprovedigitalPbsKeyValue("waterfall", waterfalls)
                .putBidder("improvedigital")
                .putBidderKeyValue("improvedigital", "placementId", placementId)
                .putBidderKeyValue("improvedigital", "keyValues", customKeyValues);

        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(impExtBuilder.getImpExt())
                        .video(Video.builder()
                                .protocols(CollectionUtils.isEmpty(protocols) ? Arrays.asList(2) : protocols)
                                .w(640)
                                .h(480)
                                .mimes(Arrays.asList("video/mp4"))
                                .minduration(1)
                                .maxduration(60)
                                .linearity(1)
                                .placement(5)
                                .build())
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .cat(siteIabCategories)
                                .build())
                        .build()
        );
    }

    protected String getBidRequestVideoForSSPImprovedigital(
            String uniqueId,
            String currency,
            int placementId,
            String storedImpId,
            boolean isGvast,
            List<Integer> protocols,
            List<String> siteIabCategories,
            Map<String, String> customKeyValues
    ) {
        ExtRequestPrebid.ExtRequestPrebidBuilder extPrebidBuilder = ExtRequestPrebid.builder()
                .channel(ExtRequestPrebidChannel.of("web"))
                .pbs(ExtRequestPrebidPbs.of("/openrtb2/auction"))
                .server(ExtRequestPrebidServer.of(
                        "http://localhost:8080", 1, "local"
                ));

        if (isGvast) {
            extPrebidBuilder.targeting(getExtPrebidTargetingForGvast())
                    .cache(getExtPrebidCacheForGvast());
        }

        return getBidRequestWeb(uniqueId,
                imp -> imp.toBuilder()
                        .ext(new SSPBidRequestImpExt()
                                .putStoredRequest(storedImpId)
                                .putBidder()
                                .putBidderKeyValue("placementId", placementId)
                                .putBidderKeyValue("keyValues", customKeyValues /* nullable */)
                                .getImpExt())
                        .video(Video.builder()
                                .protocols(CollectionUtils.isEmpty(protocols) ? Arrays.asList(2) : protocols)
                                .w(640)
                                .h(480)
                                .mimes(Arrays.asList("video/mp4"))
                                .minduration(1)
                                .maxduration(60)
                                .linearity(1)
                                .placement(5)
                                .build())
                        .bidfloor(new BigDecimal(0).setScale(1, RoundingMode.HALF_EVEN))
                        .bidfloorcur(currency)
                        .build(),
                bidRequest -> bidRequest.toBuilder()
                        .site(Site.builder()
                                .domain(IT_TEST_DOMAIN)
                                .page("http://" + IT_TEST_DOMAIN)
                                .publisher(Publisher.builder()
                                        .domain(IT_TEST_MAIN_DOMAIN)
                                        .build())
                                .ext(ExtSite.of(0, null))
                                .cat(siteIabCategories)
                                .build())
                        .device(Device.builder()
                                .ua(IT_TEST_USER_AGENT)
                                .ip(IT_TEST_IP)
                                .build())
                        .at(1)
                        .tmax(5000L)
                        .cur(Arrays.asList(currency))
                        .regs(Regs.of(null, ExtRegs.of(0, null)))
                        .ext(ExtRequest.of(extPrebidBuilder.build()))
                        .build()
        );
    }

    private ExtRequestPrebidCache getExtPrebidCacheForGvast() {
        return ExtRequestPrebidCache.of(
                null,
                ExtRequestPrebidCacheVastxml.of(null, true),
                false
        );
    }

    private ExtRequestTargeting getExtPrebidTargetingForGvast() {
        return ExtRequestTargeting.builder()
                .includewinners(true)
                .includebidderkeys(true)
                .includeformat(true)
                .pricegranularity(BID_REQUEST_MAPPER.valueToTree(
                        ExtPriceGranularity.of(2, Arrays.asList(
                                ExtGranularityRange.of(new BigDecimal(2), new BigDecimal(0.01)),
                                ExtGranularityRange.of(new BigDecimal(5), new BigDecimal(0.05)),
                                ExtGranularityRange.of(new BigDecimal(10), new BigDecimal(0.1)),
                                ExtGranularityRange.of(new BigDecimal(40), new BigDecimal(0.5)),
                                ExtGranularityRange.of(new BigDecimal(100), new BigDecimal(1))
                        ))
                ))
                .build();
    }

    private String getCustomParamCacheUrl(Map<String, List<String>> custParams, String bidderName) {
        if (StringUtils.isNotEmpty(bidderName)) {
            return "http://"
                    + custParams.get(("hb_cache_host_" + bidderName).substring(0, 20)).get(0)
                    + custParams.get(("hb_cache_path_" + bidderName).substring(0, 20)).get(0)
                    + "?uuid=" + custParams.get(("hb_uuid_" + bidderName).substring(0, 20)).get(0);
        }
        return "http://"
                + custParams.get("hb_cache_host").get(0)
                + custParams.get("hb_cache_path").get(0)
                + "?uuid=" + custParams.get("hb_uuid").get(0);
    }

    private void assertCachedContentFromCacheId(String uniqueId, String expectedCachedContent) throws IOException {
        assertCachedContent(IT_TEST_CACHE_URL + "?uuid=" + uniqueId, expectedCachedContent);
    }

    private void assertCachedContent(String cacheUrl, String expectedCachedContent) throws IOException {
        assertThat(IOUtils.toString(
                HttpClientBuilder.create().build().execute(
                        new HttpGet(cacheUrl)
                ).getEntity().getContent(), "UTF-8"
        )).isEqualTo(expectedCachedContent);
    }

    private String getVastXmlToCache(String vastXml, String bidder, String cpm, int placementId) {
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
}
