package com.improvedigital.prebid.server.it;

import io.restassured.response.Response;
import lombok.Builder;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

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
                "admin.port=18063",
                "http.port=18083",
        }
)
@RunWith(SpringRunner.class)
public class ImprovedigitalBidAdjustmentTest extends ImprovedigitalIntegrationTest {

    @Test
    public void testBidIsDecreasedToMultipleBidders() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                BidAdjustmentMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072201")
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build()
        );

        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, 1.12);
        assertSeat(responseJson, 0, "generic");
        assertThat(getAdm(responseJson, 0, 0)).contains("<img src='banner-4.png'/>");
        assertThat(getBidExtPrebidType(responseJson, 0, 0)).isEqualTo("banner");

        assertBidIdExists(responseJson, 1, 0);
        assertBidImpId(responseJson, 1, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 1, 0, 1.15);
        assertSeat(responseJson, 1, "improvedigital");
        assertThat(getAdm(responseJson, 1, 0)).contains("<img src='banner-2.png'/>");
        assertThat(getBidExtPrebidType(responseJson, 1, 0)).isEqualTo("banner");
    }

    @Test
    public void testBidIsIncreased() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToImprovedigitalBidder(
                uniqueId,
                "2022072202",
                20220722,
                1.15,
                1.25
        );
    }

    private JSONObject doAuctionRequestToImprovedigitalBidder(
            String uniqueId,
            String storedImpId,
            int placementIdOfStoredImp,
            double dspBid,
            double expectedPbsBid
    ) throws JSONException {

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequestBanner(uniqueId,
                                SSPBidRequestBannerTestData.builder()
                                        .currency("USD")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", placementIdOfStoredImp))
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", uniqueId, "USD",
                                BidResponseTestData.builder()
                                        .price(dspBid)
                                        .adm("<img src='banner-1.jpg' />")
                                        .build()
                        )))
        );

        Response response = specWithPBSHeader(18083)
                .body(getAuctionBidRequestBanner(uniqueId, AuctionBidRequestBannerTestData.builder()
                        .currency("USD")
                        .impExt(new AuctionBidRequestImpExt()
                                .putStoredRequest(storedImpId)
                        )
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isEqualTo(1);
        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isEqualTo(1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_" + uniqueId);
        assertBidPrice(responseJson, 0, 0, expectedPbsBid);
        assertSeat(responseJson, 0, "improvedigital");
        assertThat(getAdm(responseJson, 0, 0)).contains("<img src='banner-1.jpg' />");
        assertThat(getBidExtPrebidType(responseJson, 0, 0)).isEqualTo("banner");

        return responseJson;
    }

    private JSONObject doAuctionRequestToMultipleBidder(BidAdjustmentMultipleBidderAuctionTestParam param)
            throws JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequestBanner(param.auctionRequestId,
                                SSPBidRequestBannerTestData.builder()
                                        .currency("USD")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", param.improvePlacementId))
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "improvedigital", param.auctionRequestId, "USD",
                                BidResponseTestData.builder()
                                        .price(improvePrice1Value)
                                        .adm(param.improveAdm1)
                                        .build(),
                                BidResponseTestData.builder()
                                        .price(improvePrice2Value)
                                        .adm(param.improveAdm2)
                                        .build()
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/generic-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequestBanner(param.auctionRequestId,
                                SSPBidRequestBannerTestData.builder()
                                        .currency("USD")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("exampleProperty", "examplePropertyValue"))
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getBidResponse(
                                "generic", param.auctionRequestId, "USD",
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

        Response response = specWithPBSHeader(18082)
                .body(getAuctionBidRequestBanner(param.auctionRequestId, AuctionBidRequestBannerTestData.builder()
                        .currency("USD")
                        .impExt(new AuctionBidRequestImpExt()
                                .putStoredRequest(param.storedImpId)
                        )
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertThat(responseJson.getJSONArray("seatbid").length())
                .isEqualTo(2);
        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(0).getJSONArray("bid").length())
                .isEqualTo(1);
        assertThat(responseJson.getJSONArray("seatbid").getJSONObject(1).getJSONArray("bid").length())
                .isEqualTo(1);

        return responseJson;
    }

    /**
     * Class to deal with many permutation/combination of request/response parameters.
     * This is to avoid long method parameter names code smell.
     */
    @Builder(toBuilder = true)
    public static class BidAdjustmentMultipleBidderAuctionTestParam {
        String auctionRequestId;
        String storedImpId;
        int improvePlacementId;
        String improveAdm1;
        String improvePrice1;
        String improveAdm2;
        String improvePrice2;
        String genericAdm1;
        String genericPrice1;
        String genericAdm2;
        String genericPrice2;
    }
}
