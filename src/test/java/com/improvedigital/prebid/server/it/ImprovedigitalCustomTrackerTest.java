package com.improvedigital.prebid.server.it;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.iab.openrtb.response.EventTracker;
import io.restassured.response.Response;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

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

    // The exchange rate is hard coded (src/test/resources/org/prebid/server/it/currency/latest.json).
    private static final double IT_TEST_USD_TO_EUR_RATE = 0.8777319407;

    @Test
    public void shouldAddCustomTrackerOnBannerResponse() throws Exception {
        final Response response = doBannerRequestAndGetResponse(Map.of(
                "IT_TEST_MACRO_ADM", "<img src='banner.png'/>",
                "IT_TEST_MACRO_CURRENCY", "USD"
        ));

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

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
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

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
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

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
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we have on creative.
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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // Ad-1: 1st pixel is what we have on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406-1");

        // Ad-1: 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));

        // Ad-2: 1st pixel is what we have on creative.
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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is what we have on creative.
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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // Ad-1: 1st pixel is what we have on creative.
        String existingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel).isEqualTo("https://imp.pbs.improvedigital.com/20220406-1");

        // Ad-1: 2nd pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-1']/Wrapper/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));

        // Ad-2: 1st pixel is what we have on creative.
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
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("1.25", "13245"));
    }

    @Test
    public void shouldAddCorrectPlacementIdInCustomTrackerOnVideoResponse() throws Exception {
        String vastXmlResponse1 = getVastXmlWrapper("20220601_1", false);
        String vastXmlResponse2 = getVastXmlInline("20220601_2", false);
        final Response response = doVideoMultiImpRequestAndGetResponse(vastXmlResponse1, vastXmlResponse2);

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertBidExtPrebidType(responseJson, 0, 1, "video");
        assertCurrency(responseJson, "USD");

        String adm1 = getAdm(responseJson, 0, 0);
        String adm2 = getAdm(responseJson, 0, 1);

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
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned no event trackers field and no imp trackers field.
                        createNativeResponse(3000, 2250, null, null)
                ))
                .improvePrice("1.25")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we didn't place the tracker in "eventtrackers"/"imptrackers".
        assertThat(adm.has("eventtrackers")).isFalse();
        assertThat(adm.has("imptrackers")).isFalse();
    }

    @Test
    public void shouldNotAddCustomTrackerOnNativeResponseWhenEmptyTrackersReturnedFromBidder() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned empty event trackers and empty imp trackers.
                        createNativeResponse(3000, 2250, Arrays.asList(), Arrays.asList())
                ))
                .improvePrice("1.25")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we didn't place the tracker in "eventtrackers"/"imptrackers".
        assertThat(adm.has("eventtrackers")).isTrue();
        assertThat(adm.getJSONArray("eventtrackers").length()).isEqualTo(0);
        assertThat(adm.has("imptrackers")).isTrue();
        assertThat(adm.getJSONArray("imptrackers").length()).isEqualTo(0);
    }

    @Test
    public void shouldNotAddCustomTrackerOnNativeResponseWhenOnlyEventTrackersReturnedFromBidder() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned 1 event tracker and no imp trackers field.
                        createNativeResponse(3000, 2250, Arrays.asList(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ), null)
                ))
                .improvePrice("1.25")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we placed the tracker in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "1.25", "20220830");

        // Check we didn't place the tracker in "imptrackers".
        assertThat(adm.has("imptrackers")).isFalse();

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInEventTrackers(adm, "https://existingtrakcer.bidder.com/event")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerOnNativeResponseWhenOnlyImpTrackersReturnedFromBidder() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned no event trackers field and 1 imp tracker.
                        createNativeResponse(3000, 2250, null, Arrays.asList(
                                "https://existingtrakcer.bidder.com/imp"
                        ))
                ))
                .improvePrice("1.25")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we didn't place the tracker in "eventtrackers".
        assertThat(adm.has("eventtrackers")).isFalse();

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "1.25", "20220830");

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInImpTrackers(adm, "https://existingtrakcer.bidder.com/imp")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerOnNativeResponseWhenBothTrackersReturnedFromBidder() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned 1 event tracker and 1 imp tracker.
                        createNativeResponse(3000, 2250, Arrays.asList(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ), Arrays.asList(
                                "https://existingtrakcer.bidder.com/imp"
                        ))
                ))
                .improvePrice("1.25")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we placed the tracker in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "1.25", "20220830");

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "1.25", "20220830");

        // Make sure, we didn't wipe out existing trackers.
        assertThat(findATrackerInEventTrackers(adm, "https://existingtrakcer.bidder.com/event")).isNotNull();
        assertThat(findATrackerInImpTrackers(adm, "https://existingtrakcer.bidder.com/imp")).isNotNull();
    }

    @Test
    public void shouldAddCustomTrackerWithCorrectCurrencyValueOnNativeResponse() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                .improveAdm(toJsonString(
                        // SSP returned 1 event tracker and 1 imp tracker.
                        createNativeResponse(3000, 2250, Arrays.asList(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ), Arrays.asList(
                                "https://existingtrakcer.bidder.com/imp"
                        ))
                ))
                .improvePrice("1.25")
                .improveCurrency("EUR")
                .storedImpId("2022083003")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD"); /* PBS responds in request currency */

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // The exchange rate is hard coded (src/test/resources/org/prebid/server/it/currency/latest.json).

        // Check we placed the correct currency value in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(
                customTrackerEvent, Double.toString(usdToEur(1.25)), "20220830"
        );

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(
                customTrackerUrl, Double.toString(usdToEur(1.25)), "20220830"
        );
    }

    @Test
    public void shouldGetErrorWhenNoPlacementIdIsProvidedInBidRequest() throws Exception {
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-no-placementid-bid-request.json",
                        null
                )))
                .willReturn(aResponse().withBody(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-no-placementid-bid-response.json",
                        null
                )))
        );

        final Response response = specWithPBSHeader(18081)
                .body(jsonFromFileWithMacro(
                        "/com/improvedigital/prebid/server/it/test-no-placementid-auction-request.json",
                        null
                ))
                .post(Endpoint.openrtb2_auction.value());

        // For this error, we get 200 with empty respose and proper error message (as we used test=1).
        assertThat(response.statusCode()).isEqualTo(200);

        JSONObject responseJson = new JSONObject(response.asString());
        assertThat(responseJson.getJSONArray("seatbid").length()).isEqualTo(0);
        assertThat(responseJson
                .getJSONObject("ext")
                .getJSONObject("prebid")
                .getJSONObject("modules")
                .getJSONObject("errors")
                .getJSONObject("improvedigital-custom-vast-hooks-module")
                .getJSONArray("improvedigital-custom-vast-hooks-processed-auction-request")
                .getString(0)
        ).isEqualTo("improvedigital placementId is not defined for one or more imp(s)");
    }

    @Test
    public void testTrackerIsAddedOnMultiformatRequestWhenNativeIsReturned() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                // Send request of both banner and native and we get response of native.
                .improveAdm(toJsonString(createNativeResponse(3000, 2250,
                        Arrays.asList(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ),
                        Arrays.asList(
                                "https://existingtrakcer.bidder.com/imp"
                        )
                )))
                .improvePrice("2.25")
                .storedImpId("2022083002")
                .improvePlacementId(20220830)
                .nativeData(NativeTestParam.builder()
                        // This native data is what we have in the stored imp.
                        .request(createNativeRequest("1.2", 90, 128, 128, 120))
                        .build())
                .bannerData(BannerTestParam.builder()
                        // This banner data is what we have in the stored imp.
                        .w(320)
                        .h(320)
                        .build())
                .bannerDataIsInStoredImp(true)
                .nativeDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "native");
        assertCurrency(responseJson, "USD");

        JSONObject adm = new JSONObject(getAdm(responseJson, 0, 0));

        // Check we placed the correct currency value in "eventtrackers" properly.
        JSONObject customTrackerEvent = findATrackerInEventTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInEventTrackers(customTrackerEvent, "2.25", "20220830");

        // Check we placed the tracker in "imptrackers" properly.
        String customTrackerUrl = findATrackerInImpTrackers(adm, "/ssp_bid");
        assertThatCustomTrackerExistsInImpTrackers(customTrackerUrl, "2.25", "20220830");
    }

    private Response doBannerRequestAndGetResponse(Map<String, String> responseMacroReplacers) throws IOException {
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
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
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
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

    private Response doVideoMultiImpRequestAndGetResponse(String vastXml1, String vastXml2) {
        String uniqueId = UUID.randomUUID().toString();

        final String stubScenario = "request:improvedigital";
        final String stubStateNextImp = "ssp_improvedigital_1";
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .inScenario(stubScenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(1.25)
                                .adm(vastXml1)
                                .build()
                )))
                .willSetStateTo(stubStateNextImp)
        );
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .inScenario(stubScenario)
                .whenScenarioStateIs(stubStateNextImp)
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_2")
                                .price(2.15)
                                .adm(vastXml2)
                                .build()
                )))
                .willSetStateTo(Scenario.STARTED)
        );

        return specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(Arrays.asList(
                                AuctionBidRequestImpTestData.builder()
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .videoData(VideoTestParam.getDefault())
                                                .build())
                                        .impExt(new AuctionBidRequestImpExt()
                                                .putBidder("improvedigital")
                                                .putBidderKeyValue("improvedigital", "placementId", 12345))
                                        .build(),
                                AuctionBidRequestImpTestData.builder()
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_2")
                                                .videoData(VideoTestParam.getDefault())
                                                .build())
                                        .impExt(new AuctionBidRequestImpExt()
                                                .putBidder("improvedigital")
                                                .putBidderKeyValue("improvedigital", "placementId", 54321))
                                        .build()
                        ))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private JSONObject doRequestAndGetResponse(CustomTrackerRequestTestParam param) throws JSONException {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", param.improvePlacementId)
                                                .putBidderKeyValue("size", param.toBannerDimension()))
                                        .bannerData(param.bannerData)
                                        .nativeData(param.nativeData)
                                        .build())
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital",
                        uniqueId,
                        StringUtils.defaultString(param.improveCurrency, "USD"),
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(Double.parseDouble(param.improvePrice))
                                .adm(param.improveAdm)
                                .build()
                )))
        );

        Response response = specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(Arrays.asList(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(param.storedImpId)
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", param.improvePlacementId))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .bannerData(param.bannerDataIsInStoredImp ? null : param.bannerData)
                                        .nativeData(param.nativeDataIsInStoredImp ? null : param.nativeData)
                                        .build())
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertBidCountIsOneOrMore(responseJson);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        if ("EUR".equalsIgnoreCase(param.improveCurrency)) {
            assertBidPrice(responseJson, 0, 0, usdToEur(Double.parseDouble(param.improvePrice)));
        } else {
            assertBidPrice(responseJson, 0, 0, Double.parseDouble(param.improvePrice));
        }
        assertSeat(responseJson, 0, "improvedigital");
        assertCurrency(responseJson, "USD");
        return responseJson;
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

    private double usdToEur(double val) {
        return BigDecimal.valueOf(val / IT_TEST_USD_TO_EUR_RATE)
                .setScale(3, RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    @Builder(toBuilder = true)
    public static class CustomTrackerRequestTestParam {
        NativeTestParam nativeData;
        BannerTestParam bannerData;
        boolean bannerDataIsInStoredImp;
        boolean nativeDataIsInStoredImp;
        String storedImpId;
        int improvePlacementId;
        String improveAdm;
        String improvePrice;
        String improveCurrency;

        public Map<String, ?> toBannerDimension() {
            return bannerData == null ? null : Map.of(
                    "w", bannerData.w,
                    "h", bannerData.h
            );
        }
    }
}
