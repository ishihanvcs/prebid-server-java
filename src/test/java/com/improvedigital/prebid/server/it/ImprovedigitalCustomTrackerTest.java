package com.improvedigital.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(
        locations = {
                "/com/improvedigital/prebid/server/it/test-application-improvedigital-hooks.properties"
        },
        properties = {
                "admin.port=18061",
                "http.port=18081",
        }
)
@RunWith(SpringRunner.class)
public class ImprovedigitalCustomTrackerTest extends ImprovedigitalIntegrationTest {

    @Test
    public void shouldAddCustomTrackerOnBannerResponse() throws Exception {
        final Response response = doBannerRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", "<img src='banner.png'/>",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        assertThat(adm).isEqualTo("<img src='banner.png'/>"
                + "<img src=\"" + getCustomTrackerUrl("1.25", "13245") + "\">"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag1() throws Exception {
        final Response response = doBannerRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", "<body><img src='banner.png'/></body>",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        assertThat(adm).isEqualTo("<body>"
                + "<img src='banner.png'/>"
                + "<img src=\"" + getCustomTrackerUrl("1.25", "13245") + "\">"
                + "</body>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag2() throws Exception {
        final Response response = doBannerRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", "<   body   ><img src='banner.png'/><   /   body   >",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        assertThat(adm).isEqualTo("<   body   >"
                + "<img src='banner.png'/>"
                + "<img src=\"" + getCustomTrackerUrl("1.25", "13245") + "\">"
                + "</body>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag3() throws Exception {
        final Response response = doBannerRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", "<html>< body ><img src='banner.png'/><  /  body  ></html>",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        assertThat(adm).isEqualTo("<html>"
                + "< body >"
                + "<img src='banner.png'/>"
                + "<img src=\"" + getCustomTrackerUrl("1.25", "13245") + "\">"
                + "</body>"
                + "</html>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasImpression() throws Exception {
        String vastXmlResponse = getVastXmlInline("20220406", true)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // 1st pixel is what we had on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406");

        // 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasImpressionAndMultipleAds() throws Exception {
        String vastXmlResponse = getVastXmlInlineWithMultipleAds("20220406", true)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // Ad-1: 1st pixel is what we had on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406-1");

        // Ad-1: 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));

        // Ad-2: 1st pixel is what we had on creative.
        String existingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel2).isEqualTo("https://imp.pbs.improvedigital.com/20220406-2");

        // Ad-2: 2nd pixel is the custom tracker we added.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasNoImpression() throws Exception {
        String vastXmlResponse = getVastXmlInline("20220406", false)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // 1st pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndImpression() throws Exception {
        String vastXmlResponse = getVastXmlWrapper("20220406", true)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // 1st pixel is what we had on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406");

        // 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/Wrapper/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndImpressionAndMultipleAds() throws Exception {
        String vastXmlResponse = getVastXmlWrapperWithMultipleAds("20220406", true)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // Ad-1: 1st pixel is what we had on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406-1");

        // Ad-1: 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/Wrapper/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));

        // Ad-2: 1st pixel is what we had on creative.
        String existingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel2).isEqualTo("https://imp.pbs.improvedigital.com/20220406-2");

        // Ad-2: 2nd pixel is the custom tracker we added.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/Wrapper/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndNoImpression() throws Exception {
        String vastXmlResponse = getVastXmlWrapper("20220406", false)
                .replace("\"", "\\\"");
        final Response response = doVideoRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", vastXmlResponse,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm = getAdmOf1stBid(responseJson);

        // 1st pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCorrectPlacementIdInCustomTrackerOnVideoResponse() throws Exception {
        String vastXmlResponse1 = getVastXmlWrapper("20220601_1", false)
                .replace("\"", "\\\"");
        String vastXmlResponse2 = getVastXmlInline("20220601_2", false)
                .replace("\"", "\\\"");
        final Response response = doVideoMultiImpRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM_1", vastXmlResponse1,
                "IT_TEST_MACRO_ADM_2", vastXmlResponse2,
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOfNthBid(responseJson, 0)).isEqualTo("video");
        assertThat(getExtPrebidTypeOfNthBid(responseJson, 1)).isEqualTo("video");
        assertCurrency(responseJson, "USD");

        String adm1 = getAdmOfNthBid(responseJson, 0);
        String adm2 = getAdmOfNthBid(responseJson, 1);

        // Swapping the multi responses so that adm1 contains the imp_1's and adm2 contains imp_2's response.
        if (adm2.contains("20220601_1")) {
            String temp = adm1;
            adm1 = adm2;
            adm2 = temp;
        }

        // 1st imp's tracker.
        String trackingImpPixel1 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220601_1']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm1)));
        assertThat(trackingImpPixel1).isEqualTo(getCustomTrackerUrl("1.25", "12345"));

        // 2nd imp's tracker.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220601_2']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm2)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("2.15", "54321"));
    }

    @Test
    public void shouldNotAddCustomTrackerOnNativeResponseWhenNoTrackersReturnedFromBidder() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", "",
                "IT_TEST_MACRO_IMP_TRACKERS", "",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Check we didn't place the tracker in "eventtrackers"/"imptrackers".
        assertThat(adm.has("eventtrackers")).isFalse();
        assertThat(adm.has("imptrackers")).isFalse();
    }

    @Test
    public void shouldNotAddCustomTrackerOnNativeResponseWhenEmptyTrackersReturnedFromBidder() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", ",\\\"eventtrackers\\\":[]",
                "IT_TEST_MACRO_IMP_TRACKERS", ",\\\"imptrackers\\\":[]",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Check we didn't place the tracker in "eventtrackers"/"imptrackers".
        assertThat(adm.has("eventtrackers")).isTrue();
        assertThat(adm.getJSONArray("eventtrackers").length()).isEqualTo(0);
        assertThat(adm.has("imptrackers")).isTrue();
        assertThat(adm.getJSONArray("imptrackers").length()).isEqualTo(0);
    }

    @Test
    public void shouldNotAddCustomTrackerOnNativeResponseWhenOnlyEventTrackersReturnedFromBidder() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", ",\\\"eventtrackers\\\":[{\\\"event\\\": 1, \\\"method\\\": 1, \\\"url\\\": \\\"https://existingtrakcer.bidder.com/event\\\"}]",
                "IT_TEST_MACRO_IMP_TRACKERS", "",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Check we placed the tracker in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "1.25", "13245");

        // Check we didn't place the tracker in "imptrackers".
        assertThat(adm.has("imptrackers")).isFalse();

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInEventTrackers(adm, "https://existingtrakcer.bidder.com/event")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerOnNativeResponseWhenOnlyImpTrackersReturnedFromBidder() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", "",
                "IT_TEST_MACRO_IMP_TRACKERS", ",\\\"imptrackers\\\":[\\\"https://existingtrakcer.bidder.com/imp\\\"]",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Check we didn't place the tracker in "eventtrackers".
        assertThat(adm.has("eventtrackers")).isFalse();

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "1.25", "13245");

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInImpTrackers(adm, "https://existingtrakcer.bidder.com/imp")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerOnNativeResponseWhenBothTrackersReturnedFromBidder() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", ",\\\"eventtrackers\\\":[{\\\"event\\\": 1, \\\"method\\\": 1, \\\"url\\\": \\\"https://existingtrakcer.bidder.com/event\\\"}]",
                "IT_TEST_MACRO_IMP_TRACKERS", ",\\\"imptrackers\\\":[\\\"https://existingtrakcer.bidder.com/imp\\\"]",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Check we placed the tracker in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "1.25", "13245");

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "1.25", "13245");

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInEventTrackers(adm, "https://existingtrakcer.bidder.com/event")).isNotNull();
        assertThat(findATrackerInImpTrackers(adm, "https://existingtrakcer.bidder.com/imp")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerWithCorrectCurrencyValueOnNativeResponse() throws Exception {
        final Response response = doNativeRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_EVENT_TRACKERS", ",\\\"eventtrackers\\\":[{\\\"event\\\": 1, \\\"method\\\": 1, \\\"url\\\": \\\"https://existingtrakcer.bidder.com/event\\\"}]",
                "IT_TEST_MACRO_IMP_TRACKERS", ",\\\"imptrackers\\\":[\\\"https://existingtrakcer.bidder.com/imp\\\"]",
                "IT_TEST_MACRO_CURRENCY", "EUR"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(getExtPrebidTypeOf1stBid(responseJson)).isEqualTo("native");
        assertCurrency(responseJson, "USD"); /* PBS responds in request currency */

        JSONObject adm = new JSONObject(getAdmOf1stBid(responseJson));

        // Bid request was made with USD and bidder returned 1.25 EUR. Hence, we will get ~1.424 USD finally.
        // The exchange rate is hard coded (src/test/resources/org/prebid/server/it/currency/latest.json).

        // Check we placed the correct currency value in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "1.424", "13245");

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "1.424", "13245");
    }

    private Response doBannerRequestAndGetResponse(Map<String, String> responseMacroReplacers) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-banner-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-banner-improvedigital-bid-response.json",
                                responseMacroReplacers
                        )))
        );

        return specWithPBSHeader(18081)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-banner-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response doVideoRequestAndGetResponse(Map<String, String> responseMacroReplacers) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-video-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-video-improvedigital-bid-response.json",
                                responseMacroReplacers
                        )))
        );

        return specWithPBSHeader(18081)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-video-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response doVideoMultiImpRequestAndGetResponse(
            Map<String, String> responseMacroReplacers) throws IOException {
        final String stubScenario = "Multi imp";
        final String stubStateNextImp = "Next imp";
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .inScenario(stubScenario)
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-video-multiimp-improvedigital-bid-response-1.json",
                                responseMacroReplacers
                        )))
                        .willSetStateTo(stubStateNextImp)
        );
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .inScenario(stubScenario)
                        .whenScenarioStateIs(stubStateNextImp)
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/"
                                        + "test-video-multiimp-improvedigital-bid-response-2.json",
                                responseMacroReplacers
                        )))
                        .willSetStateTo(Scenario.STARTED)
        );

        return specWithPBSHeader(18081)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-video-multiimp-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response doNativeRequestAndGetResponse(Map<String, String> responseMacroReplacers) throws IOException {
        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-native-improvedigital-bid-request.json",
                                null
                        )))
                        .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                                "/com/improvedigital/prebid/server/it/test-native-improvedigital-bid-response.json",
                                responseMacroReplacers
                        )))
        );

        return specWithPBSHeader(18081)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-native-auction-improvedigital-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private void assertThatCustomTrackerExistsInEventTrackers(
            JSONObject customTrackerEvent, String cpmInUsd, String pid) throws JSONException {
        assertThat(customTrackerEvent).isNotNull();
        assertThat(customTrackerEvent.getInt("event")).isEqualTo(1);
        assertThat(customTrackerEvent.getInt("method")).isEqualTo(1);
        assertThat(customTrackerEvent.getString("url"))
                .isEqualTo(getCustomTrackerUrl(cpmInUsd, pid));
    }

    private void assertThatCustomTrackerExistsInImpTrackers(String customTrackerUrl, String cpmInUsd, String pid) {
        assertThat(customTrackerUrl).isNotNull();
        assertThat(customTrackerUrl)
                .isEqualTo(getCustomTrackerUrl(cpmInUsd, pid));
    }

    private void assertCurrency(JSONObject responseJson, String expectedCurrency) throws JSONException {
        assertThat(responseJson.getString("cur")).isEqualTo(expectedCurrency);
    }

    @NotNull
    private String getCustomTrackerUrl(String cpmInUsd, String pid) {
        return "https://it.pbs.com/ssp_bids?bidder=improvedigital&cpm=" + cpmInUsd + "&pid=" + pid;
    }

    @Nullable
    private String findATrackerInImpTrackers(JSONObject adm, String urlPattern) throws JSONException {
        assertThat(adm.has("imptrackers")).isTrue();
        JSONArray impTrackers = adm.getJSONArray("imptrackers");
        assertThat(impTrackers.length()).isGreaterThanOrEqualTo(1);

        for (int i = 0; i < impTrackers.length(); i++) {
            if (impTrackers.getString(i).contains(urlPattern)) {
                return impTrackers.getString(i);
            }
        }

        return null;
    }

    @Nullable
    private JSONObject findATrackerInEventTrackers(JSONObject adm, String urlPattern) throws JSONException {
        assertThat(adm.has("eventtrackers")).isTrue();
        JSONArray eventTrackers = adm.getJSONArray("eventtrackers");
        assertThat(eventTrackers.length()).isGreaterThanOrEqualTo(1);

        for (int i = 0; i < eventTrackers.length(); i++) {
            if (eventTrackers.getJSONObject(i).getString("url").contains(urlPattern)) {
                return eventTrackers.getJSONObject(i);
            }
        }

        return null;
    }

    private String getAdmOf1stBid(JSONObject responseJson) throws JSONException {
        return getAdmOfNthBid(responseJson, 0);
    }

    @NotNull
    private String getAdmOfNthBid(JSONObject responseJson, int bidIndex) throws JSONException {
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isGreaterThanOrEqualTo(1);

        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isGreaterThanOrEqualTo(1);

        return responseJson
                .getJSONArray("seatbid").getJSONObject(0)
                .getJSONArray("bid").getJSONObject(bidIndex)
                .getString("adm");
    }

    private String getExtPrebidTypeOf1stBid(JSONObject responseJson) throws JSONException {
        return getExtPrebidTypeOfNthBid(responseJson, 0);
    }

    private String getExtPrebidTypeOfNthBid(JSONObject responseJson, int bidIndex) throws JSONException {
        return responseJson
                .getJSONArray("seatbid").getJSONObject(0)
                .getJSONArray("bid").getJSONObject(bidIndex)
                .getJSONObject("ext")
                .getJSONObject("prebid")
                .getString("type");
    }

    private static String jsonFromFileWithMacro(String file, Map<String, String> macrosInFileContent)
            throws IOException {
        // workaround to clear formatting
        String fileContent = mapper.writeValueAsString(
                mapper.readTree(ImprovedigitalIntegrationTest.class.getResourceAsStream(file))
        );

        // Replace all occurrences of <key>s by it's <value> of map.
        if (macrosInFileContent != null) {
            return macrosInFileContent.entrySet().stream()
                    .map(m -> (Function<String, String>) s -> s.replace(m.getKey(), m.getValue()))
                    .reduce(Function.identity(), Function::andThen)
                    .apply(fileContent);
        }

        return fileContent;
    }

    private String getVastXmlInline(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"20220406\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>";
    }

    private String getVastXmlInlineWithMultipleAds(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-1" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:60</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-1" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-1" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <InLine>"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 2</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <Creatives>"
                + "        <Creative AdID=\"" + adId + "-2" + "\">"
                + "          <Linear>"
                + "            <Duration>00:00:30</Duration>"
                + "            <VideoClicks>"
                + "              <ClickThrough>https://click.pbs.improvedigital.com/" + adId + "-2" + "</ClickThrough>"
                + "            </VideoClicks>"
                + "            <MediaFiles>"
                + "              <MediaFile type=\"video/mp4\" width=\"640\" height=\"480\">"
                + "                https://media.pbs.improvedigital.com/" + adId + "-2" + ".mp4"
                + "              </MediaFile>"
                + "            </MediaFiles>"
                + "          </Linear>"
                + "        </Creative>"
                + "      </Creatives>"
                + "    </InLine>"
                + "  </Ad>"
                + "</VAST>";
    }

    private String getVastXmlWrapper(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>";
    }

    private String getVastXmlWrapperWithMultipleAds(String adId, boolean hasImpPixel) {
        return "<VAST version=\"2.0\">"
                + "  <Ad id=\"" + adId + "-1" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-1" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-1" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-1" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "  <Ad id=\"" + adId + "-2" + "\">"
                + "    <Wrapper fallbackOnNoAd=\"true\">"
                + "      <AdSystem>PBS IT Test Case</AdSystem>"
                + "      <AdTitle>VAST 2.0 - Ad 1</AdTitle>"
                + "      <Description>PBS IT Test Case - VAST 2.0</Description>"
                + "      <Error>https://error.pbs.improvedigital.com/" + adId + "-2" + "</Error>"
                + (hasImpPixel ? "<Impression>https://imp.pbs.improvedigital.com/" + adId + "-2" + "</Impression>" : "")
                + "      <VASTAdTagURI>"
                + "        <![CDATA[https://vast.pbs.improvedigital.com/" + adId + "-2" + "]]>"
                + "      </VASTAdTagURI>"
                + "    </Wrapper>"
                + "  </Ad>"
                + "</VAST>";
    }
}
