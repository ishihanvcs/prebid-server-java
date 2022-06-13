package com.improvedigital.prebid.server.it;

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
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(response.asString())));

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
        assertThat(getExtPrebidTypeOfBid(responseJson, 0, 0)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we had on creative.
        String mediaUrl = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220608']/InLine/Creatives" +
                        "/Creative[@AdID='20220608']/Linear/MediaFiles/MediaFile[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(mediaUrl.trim()).isEqualTo("https://media.pbs.improvedigital.com/20220608.mp4");
    }

    @Test
    public void auctionEndpointReturnsGVastResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlWrapper("20220608", true);
        String vastXmlWillBeCached = vastXml.replace(
                "</Wrapper>",
                "<Impression>" +
                        "<![CDATA[https://it.pbs.com/ssp_bids?bidder=improvedigital&cpm=1.08&pid=20220608]]>" +
                        "</Impression>" +
                        "</Wrapper>"
        );
        String uniqueId = "R3WOPUPAGZYVYLA02ONHOGPANWYVX2D";

        Response response = getGVastResponseFromAuction(
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getSeat(responseJson, 0)).isEqualTo("improvedigital");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='0']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(adm)));

        assertThat(vastAdTagUri.startsWith("https://pubads.g.doubleclick.net/gampad/ads")).isTrue();

        Map<String, List<String>> vastQueryParams = splitQuery(new URL(vastAdTagUri).getQuery());
        assertThat(vastQueryParams.get("cust_params")).isNotNull();
        assertThat(vastQueryParams.get("cust_params").size()).isEqualTo(1);

        Map<String, List<String>> custParams = splitQuery(vastQueryParams.get("cust_params").get(0));
        assertQuerySingleValue(custParams.get("hb_bidder"), "improvedigital");
        assertQuerySingleValue(custParams.get("hb_bidder_improvedigital"), "improvedigital");

        assertQuerySingleValue(custParams.get("hb_uuid"), uniqueId);
        assertQuerySingleValue(custParams.get("hb_uuid_improvedigital"), uniqueId);

        assertQuerySingleValue(custParams.get("hb_format"), "video");
        assertQuerySingleValue(custParams.get("hb_format_improvedigital"), "video");

        assertQuerySingleValue(custParams.get("hb_pb"), "1.08");
        assertQuerySingleValue(custParams.get("hb_pb_improvedigital"), "1.08");

        // Make sure we cached the content.
        String cacheUrl = ("http://"
                + custParams.get("hb_cache_host").get(0)
                + custParams.get("hb_cache_path").get(0)
                + "?uuid=" + custParams.get("hb_uuid").get(0)
        );
        assertCachedContent(cacheUrl, vastXmlWillBeCached);
    }

    @Test
    public void auctionEndpointReturnsWaterfallResponse() throws XPathExpressionException, IOException, JSONException {
        String vastXml = getVastXmlWrapper("20220608", true);
        String vastXmlWillBeCached = vastXml.replace(
                "</Wrapper>",
                "<Impression>" +
                        "<![CDATA[https://it.pbs.com/ssp_bids?bidder=improvedigital&cpm=1.13&pid=20220608]]>" +
                        "</Impression>" +
                        "</Wrapper>"
        );
        String uniqueId = "KCZEL1JSW8BT296EE1FYXTCKWNGWLVBJ";

        Response response = getWaterfallResponseFromAuction(
                vastXml, uniqueId, vastXmlWillBeCached
        );
        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOfBid(responseJson, 0, 0)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        String vastAdTagUri = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220608']/Wrapper/VASTAdTagURI")
                .evaluate(new InputSource(new StringReader(adm)));

        assertThat(vastAdTagUri.trim()).isEqualTo("https://vast.pbs.improvedigital.com/20220608");

        // Check we got correct cache url.
        JSONObject vastXmlCache = getExtPrebidOfBid(responseJson, 0, 0)
                .getJSONObject("cache")
                .getJSONObject("vastXml");
        assertThat(vastXmlCache.getString("cacheId")).isEqualTo(uniqueId);
        assertThat(vastXmlCache.getString("url")).endsWith("/cache?uuid=" + uniqueId);

        // Hit the cache.
        assertCachedContent(vastXmlCache.getString("url"), vastXmlWillBeCached);
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

    private Response getVastResponseFromAuction(String improveMockAdm) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-vast-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-vast-improvedigital-bid-response.json",
                                Map.of("IT_TEST_MACRO_ADM", improveMockAdm)
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
            String improveMockAdm,
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
                                Map.of("IT_TEST_MACRO_ADM", improveMockAdm)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-gvast-improvedigital-cache-request.json",
                                Map.of("IT_TEST_CACHE_VALUE", vastXmlWillBeCached)
                        )))
                        .willReturn(aResponse()
                                .withBody(jsonFromFileWithMacro(
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

    private Response getWaterfallResponseFromAuction(
            String improveMockAdm,
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
                                Map.of("IT_TEST_MACRO_ADM", improveMockAdm)
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/cache"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-waterfall-improvedigital-cache-request.json",
                                Map.of("IT_TEST_CACHE_VALUE", vastXmlWillBeCached)
                        )))
                        .willReturn(aResponse()
                                .withBody(jsonFromFileWithMacro(
                                        "/com/improvedigital/prebid/server/it/test-waterfall-improvedigital-cache-response.json",
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

    private void assertCachedContent(String cacheUrl, String expectedCachedContent) throws IOException {
        assertThat(IOUtils.toString(
                HttpClientBuilder.create().build().execute(
                        new HttpGet(cacheUrl)
                ).getEntity().getContent(), "UTF-8"
        )).isEqualTo(expectedCachedContent);
    }

    private Map<String, List<String>> splitQuery(String queryParam) {
        return Arrays.stream(queryParam.split("&"))
                .map(this::splitQueryParameter)
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleImmutableEntry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
                );
    }

    private AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        try {
            final String[] idx = it.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(
                    URLDecoder.decode(idx[0], "UTF-8"),
                    URLDecoder.decode(idx[1], "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void assertQuerySingleValue(List<String> paramValues, String expectedValue) {
        assertThat(paramValues.size()).isEqualTo(1);
        assertThat(paramValues.get(0)).isEqualTo(expectedValue);
    }
}
