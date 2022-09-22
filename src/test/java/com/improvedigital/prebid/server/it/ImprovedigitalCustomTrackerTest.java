package com.improvedigital.prebid.server.it;

import com.iab.openrtb.response.EventTracker;
import io.restassured.response.Response;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
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
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "admin.port=18061",
        "http.port=18081",
})
@RunWith(SpringRunner.class)
public class ImprovedigitalCustomTrackerTest extends ImprovedigitalIntegrationTest {

    @Test
    public void shouldAddCustomTrackerOnBannerResponse() throws Exception {
        final Response response = doBannerRequestAndGetResponse(
                1.25, "<img src='banner.png'/>"
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        assertThat(adm).isEqualTo("<img src='banner.png'/>"
                + getCustomTrackerPixel("improvedigital", "1.25", "13245")
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag1() throws Exception {
        final Response response = doBannerRequestAndGetResponse(
                1.25, "<body><img src='banner.png'/></body>"
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        assertThat(adm).isEqualTo("<body>"
                + "<img src='banner.png'/>"
                + getCustomTrackerPixel("improvedigital", "1.25", "13245")
                + "</body>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag2() throws Exception {
        final Response response = doBannerRequestAndGetResponse(
                1.25, "<   body   ><img src='banner.png'/><   /   body   >"
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        assertThat(adm).isEqualTo("<   body   >"
                + "<img src='banner.png'/>"
                + getCustomTrackerPixel("improvedigital", "1.25", "13245")
                + "</body>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnBannerResponseWhenAdmHasBodyTag3() throws Exception {
        final Response response = doBannerRequestAndGetResponse(
                1.25, "<html>< body ><img src='banner.png'/><  /  body  ></html>"
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        assertThat(adm).isEqualTo("<html>"
                + "< body >"
                + "<img src='banner.png'/>"
                + getCustomTrackerPixel("improvedigital", "1.25", "13245")
                + "</body>"
                + "</html>"
        );
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasImpression() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlInline("20220406", true)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasImpressionAndMultipleAds() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlInlineWithMultipleAds("20220406", true)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));

        // Ad-2: 1st pixel is what we have on creative.
        String existingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel2).isEqualTo("https://imp.pbs.improvedigital.com/20220406-2");

        // Ad-2: 2nd pixel is the custom tracker we added.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasNoImpression() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlInline("20220406", false)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndImpression() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlWrapper("20220406", true)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndImpressionAndMultipleAds() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlWrapperWithMultipleAds("20220406", true)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));

        // Ad-2: 1st pixel is what we have on creative.
        String existingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(existingImpPixel2).isEqualTo("https://imp.pbs.improvedigital.com/20220406-2");

        // Ad-2: 2nd pixel is the custom tracker we added.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406-2']/Wrapper/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCustomTrackerOnVideoResponseWhenXmlHasWrapperAndNoImpression() throws Exception {
        final Response response = doVideoRequestAndGetResponse(
                1.25, getVastXmlWrapper("20220406", false)
        );

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        // 1st pixel is the custom tracker we added.
        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220406']/Wrapper/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "13245"));
    }

    @Test
    public void shouldAddCorrectPlacementIdInCustomTrackerOnVideoResponse() throws Exception {
        String vastXmlResponse1 = getVastXmlWrapper("20220601_1", false);
        String vastXmlResponse2 = getVastXmlInline("20220601_2", false);
        final Response response = doVideoMultiImpRequestAndGetResponse(vastXmlResponse1, vastXmlResponse2);

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
        assertThat(trackingImpPixel1).isEqualTo(getCustomTrackerUrl("improvedigital", "1.25", "12345"));

        // 2nd imp's tracker.
        String trackingImpPixel2 = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='20220601_2']/InLine/Impression[1]")
                .evaluate(new InputSource(new StringReader(adm2)));
        assertThat(trackingImpPixel2).isEqualTo(getCustomTrackerUrl("improvedigital", "2.15", "54321"));
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
                        createNativeResponse(3000, 2250, List.of(), List.of())
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
                        createNativeResponse(3000, 2250, List.of(
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
                        createNativeResponse(3000, 2250, null, List.of(
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
                        createNativeResponse(3000, 2250, List.of(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ), List.of(
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
                        createNativeResponse(3000, 2250, List.of(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ), List.of(
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
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putBidder()
                                                .putBidderKeyValue("exampleProperty", "examplePropertyValue"))
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse("generic", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(1.25)
                                .adm("<html></html>")
                                .build()
                )))
        );

        final Response response = specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putBidder("generic")
                                        .putBidderKeyValue("generic", "exampleProperty", "examplePropertyValue"))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .build()))
                        .test(1)
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        // For this error, we get 200 with empty respose and proper error message (as we used test=1).
        assertThat(response.statusCode()).isEqualTo(200);

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
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
                        List.of(
                                EventTracker.builder()
                                        .event(1)
                                        .method(1)
                                        .url("https://existingtrakcer.bidder.com/event")
                                        .build()
                        ),
                        List.of(
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

    @Test
    public void testTrackerIsAddedOnMultiformatRequestWhenVideoIsReturned() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                // Send request of both banner and native and we get response of native.
                .improveAdm(getVastXmlInline("ad_1", true))
                .improvePrice("2.15")
                .storedImpId("2022083004")
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
                .videoData(VideoTestParam.builder()
                        // This video data is what we have in the stored imp.
                        .w(640)
                        .h(480)
                        .mimes(List.of("video/mp4"))
                        .build())
                .bannerDataIsInStoredImp(true)
                .nativeDataIsInStoredImp(true)
                .videoDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "video");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);

        String trackingImpPixel = XPathFactory.newInstance().newXPath()
                .compile("/VAST/Ad[@id='ad_1']/InLine/Impression[2]")
                .evaluate(new InputSource(new StringReader(adm)));
        assertThat(trackingImpPixel).isEqualTo(getCustomTrackerUrl("improvedigital", "2.15", "20220830"));
    }

    @Test
    public void testTrackerIsAddedOnMultiformatRequestWhenBannerIsReturned() throws Exception {
        JSONObject responseJson = doRequestAndGetResponse(CustomTrackerRequestTestParam.builder()
                // Send request of both banner and native and we get response of native.
                .improveAdm("<img src='banner-1.png' />")
                .improvePrice("1.45")
                .storedImpId("2022083004")
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
                .videoData(VideoTestParam.builder()
                        // This video data is what we have in the stored imp.
                        .w(640)
                        .h(480)
                        .mimes(List.of("video/mp4"))
                        .build())
                .bannerDataIsInStoredImp(true)
                .nativeDataIsInStoredImp(true)
                .videoDataIsInStoredImp(true)
                .build());

        assertBidExtPrebidType(responseJson, 0, 0, "banner");
        assertCurrency(responseJson, "USD");

        String adm = getAdm(responseJson, 0, 0);
        assertThat(adm).isEqualTo("<img src='banner-1.png' />"
                + getCustomTrackerPixel("improvedigital", "1.45", "20220830")
        );
    }

    private Response doBannerRequestAndGetResponse(double bidPrice, String adm) {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt(false)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", 13245))
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(bidPrice)
                                .adm(adm)
                                .build()
                )))
        );

        return specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", 13245))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response doVideoRequestAndGetResponse(double bidPrice, String adm) {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt(false)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", 13245))
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(bidPrice)
                                .adm(adm)
                                .build()
                )))
        );

        return specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putBidder("improvedigital")
                                        .putBidderKeyValue("improvedigital", "placementId", 13245))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .videoData(VideoTestParam.getDefault())
                                        .build())
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());
    }

    private Response doVideoMultiImpRequestAndGetResponse(String vastXml1, String vastXml2) {
        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .willReturn(aResponse()
                        .withTransformers("it-test-bid-response-by-impid")
                        .withTransformerParameter("imp_id_1", getSSPBidResponse(
                                "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                        .impId("imp_id_1")
                                        .price(1.25)
                                        .adm(vastXml1)
                                        .build()
                        ))
                        .withTransformerParameter("imp_id_2", getSSPBidResponse(
                                "improvedigital", uniqueId, "USD", BidResponseTestData.builder()
                                        .impId("imp_id_2")
                                        .price(2.15)
                                        .adm(vastXml2)
                                        .build()
                        ))
                )
        );

        return specWithPBSHeader(18081)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(
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
                                        .videoData(param.videoData)
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
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
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
        assertNoExtErrors(responseJson);
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
                .isEqualTo(getCustomTrackerUrl("improvedigital", cpmInUsd, pid));
    }

    private void assertThatCustomTrackerExistsInImpTrackers(String customTrackerUrl, String cpmInUsd, String pid) {
        assertThat(customTrackerUrl).isNotNull();
        assertThat(customTrackerUrl)
                .isEqualTo(getCustomTrackerUrl("improvedigital", cpmInUsd, pid));
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

    @Builder(toBuilder = true)
    public static class CustomTrackerRequestTestParam {
        NativeTestParam nativeData;
        BannerTestParam bannerData;
        VideoTestParam videoData;
        boolean bannerDataIsInStoredImp;
        boolean nativeDataIsInStoredImp;
        boolean videoDataIsInStoredImp;
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
