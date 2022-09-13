package com.improvedigital.prebid.server.it;

import com.improvedigital.prebid.server.customvast.model.Floor;
import io.restassured.response.Response;
import lombok.Builder;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                "price-floors.enabled=true",
        }
)
@RunWith(SpringRunner.class)
public class ImprovedigitalBidAdjustmentTest extends ImprovedigitalIntegrationTest {

    @Test
    public void testBidIsDecreasedToMultipleBidders() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                /* This account has "bidPriceAdjustment=0.95, bidPriceAdjustmentIncImprove=true" */ "2022081801",
                true,
                0.95,
                BidAdjustmentMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022081501")
                        .improvePlacementId(20220815)
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

        assertSeat(responseJson, 0, "generic");
        assertBidPrice(responseJson, 0, 0, 1.25 * 95 / 100); /* 95% */

        assertSeat(responseJson, 1, "improvedigital");
        assertBidPrice(responseJson, 1, 0, 1.15 * 95 / 100); /* 95% */
    }

    @Test
    public void testBidIsIncreasedWhenBidfloorIsNotSpecifiedInRequest() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has "bidPriceAdjustment=1.15, bidPriceAdjustmentIncImprove=true" */ "2022081802",
                true,
                1.15,
                "2022081502",
                20220815,
                null,
                2.25,
                null,
                2.25 * 1.15, /* Bid is increased: 2.5875. */
                true
        );
    }

    @Test
    public void testBidfloorIsDecreased() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has "bidPriceAdjustment=1.15, bidPriceAdjustmentIncImprove=true" */ "2022081802",
                true,
                1.15,
                "2022081502",
                20220815,
                BigDecimal.valueOf(0.5),
                2.25,
                BigDecimal.valueOf(0.5 / 1.15), /* Bidfloor is decreased. 0.4348. */
                2.25 * 1.15, /* Bid is increased: 2.5875. */
                true
        );
    }

    @Test
    public void testBidfloorIsIncreased() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has "bidPriceAdjustment=0.95, bidPriceAdjustmentIncImprove=true" */ "2022081801",
                true,
                0.95,
                "2022081502",
                20220815,
                BigDecimal.valueOf(0.5),
                1.15,
                BigDecimal.valueOf(0.5 / 0.95), /* Bidfloor is increased. 0.5263. */
                1.15 * 0.95, /* Bid is decreased. 1.0925. */
                true
        );
    }

    @Test
    public void testBidAdjustmentWhenImprovedigitalIsNotEnabledForBidAdjustment() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                /* This account has "bidPriceAdjustment=0.90, bidPriceAdjustmentIncImprove=false" */ "2022081803",
                false,
                0.90,
                BidAdjustmentMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022081501")
                        .improvePlacementId(20220815)
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

        assertSeat(responseJson, 0, "generic");
        assertBidPrice(responseJson, 0, 0, 1.25 * 90 / 100); /* 90% */

        assertSeat(responseJson, 1, "improvedigital");
        assertBidPrice(responseJson, 1, 0, 1.15); /* No adjustment */
    }

    @Test
    public void testBidAdjustmentWhenImprovedigitalIsNotEnabledByDefault() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has "bidPriceAdjustment=0.80" and no "bidPriceAdjustmentIncImprove" */ "2022081804",
                false,
                0.8,
                "2022081502",
                20220815,
                BigDecimal.valueOf(0.5),
                1.15,
                BigDecimal.valueOf(0.5), /* Bidfloor intact. */
                1.15, /* Bid intact. */
                true
        );
    }

    @Test
    public void testBidAdjustmentWhenAccountHasNoValueInExt() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has no "ext" */ "2022081805",
                false,
                null, /* No bidadjustment factors. */
                "2022081502",
                20220815,
                BigDecimal.valueOf(0.5),
                1.15,
                BigDecimal.valueOf(0.5), /* Bidfloor intact. */
                1.15, /* Bid intact. */
                true
        );
    }

    @Test
    public void testBidAdjustmentWhenAccountHasPriceFloorsModuleTurnedOff() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                /* This account has "auction.price-floors.enabled=false, bidPriceAdjustment=0.85" */ "2022081806",
                false,
                null, /* No bidadjustment factors. */
                "2022081502",
                20220815,
                BigDecimal.valueOf(0.4),
                1.12,
                BigDecimal.valueOf(0.4), /* Bidfloor intact. */
                1.12, /* Bid intact. */
                false
        );
    }

    private JSONObject doAuctionRequestToImprovedigitalBidder(
            String publisherId,
            boolean improveIsExpectedForAdjustment,
            Double expectedBidAdjustmentPct,
            String storedImpId,
            int placementIdOfStoredImp,
            BigDecimal dspBidFloor,
            double dspBid,
            BigDecimal expectedPbsBidFloor,
            double expectedPbsBid,
            boolean priceFloorEnabled
    ) throws JSONException {

        String uniqueId = UUID.randomUUID().toString();

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequestBanner(uniqueId,
                                SSPBidRequestBannerTestData.builder()
                                        .currency("USD")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", placementIdOfStoredImp))
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .reqExtBidAdjustmentFactors(expectedBidAdjustmentPct == null
                                                ? null : getAllBiddersExpectedBidAdjustmentFactors(
                                                expectedBidAdjustmentPct, improveIsExpectedForAdjustment)
                                        )
                                        .publisherId(publisherId)
                                        .floor(expectedPbsBidFloor == null ? null : Floor.of(
                                                expectedPbsBidFloor.setScale(4, RoundingMode.HALF_EVEN), "USD"
                                        ))
                                        .build(),
                                bidRequest -> bidRequest.toBuilder()
                                        .ext(ExtRequest.of(bidRequest.getExt().getPrebid().toBuilder()
                                                .floors(PriceFloorRules.builder()
                                                        .enabled(priceFloorEnabled)
                                                        .fetchStatus(priceFloorEnabled ? FetchStatus.none : null)
                                                        .location(priceFloorEnabled ? PriceFloorLocation.noData : null)
                                                        .build())
                                                .build()))
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
                        .publisherId(publisherId)
                        .floor(dspBidFloor == null ? null : Floor.of(
                                dspBidFloor.setScale(4, RoundingMode.HALF_EVEN), "USD"
                        ))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertSeat(responseJson, 0, "improvedigital");
        assertBidPrice(responseJson, 0, 0, expectedPbsBid);

        return responseJson;
    }

    private JSONObject doAuctionRequestToMultipleBidder(
            String publisherId,
            boolean improveIsExpectedForAdjustment,
            double expectedBidAdjustmentPct,
            BidAdjustmentMultipleBidderAuctionTestParam param) throws JSONException {

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
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .reqExtBidAdjustmentFactors(getAllBiddersExpectedBidAdjustmentFactors(
                                                expectedBidAdjustmentPct, improveIsExpectedForAdjustment
                                        ))
                                        .publisherId(publisherId)
                                        .build(),
                                bidRequest -> bidRequest.toBuilder()
                                        .ext(ExtRequest.of(bidRequest.getExt().getPrebid().toBuilder()
                                                .floors(PriceFloorRules.builder()
                                                        .enabled(true)
                                                        .fetchStatus(FetchStatus.none)
                                                        .location(PriceFloorLocation.noData)
                                                        .build())
                                                .build()))
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
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .reqExtBidAdjustmentFactors(getAllBiddersExpectedBidAdjustmentFactors(
                                                expectedBidAdjustmentPct, improveIsExpectedForAdjustment
                                        ))
                                        .publisherId(publisherId)
                                        .build(),
                                bidRequest -> bidRequest.toBuilder()
                                        .ext(ExtRequest.of(bidRequest.getExt().getPrebid().toBuilder()
                                                .floors(PriceFloorRules.builder()
                                                        .enabled(true)
                                                        .fetchStatus(FetchStatus.none)
                                                        .location(PriceFloorLocation.noData)
                                                        .build())
                                                .build()))
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

        Response response = specWithPBSHeader(18083)
                .body(getAuctionBidRequestBanner(param.auctionRequestId, AuctionBidRequestBannerTestData.builder()
                        .currency("USD")
                        .impExt(new AuctionBidRequestImpExt()
                                .putStoredRequest(param.storedImpId)
                        )
                        .publisherId(publisherId)
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

    private ExtRequestBidAdjustmentFactors getAllBiddersExpectedBidAdjustmentFactors(
            double expectedBidAdjustmentPct, boolean includeImprovedigital) {
        ExtRequestBidAdjustmentFactors expectedFactors = ExtRequestBidAdjustmentFactors.builder().build();
        getAllActiveBidders().stream().filter(b -> !"improvedigital".equalsIgnoreCase(b) || includeImprovedigital).forEach(
                b -> expectedFactors.addFactor(
                        b, BigDecimal.valueOf(expectedBidAdjustmentPct)
                )
        );
        return expectedFactors;
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
