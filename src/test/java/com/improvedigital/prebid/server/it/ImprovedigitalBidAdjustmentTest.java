package com.improvedigital.prebid.server.it;

import com.improvedigital.prebid.server.customvast.model.Floor;
import io.restassured.response.Response;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.floors.model.PriceFloorLocation;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@TestPropertySource(properties = {
        "admin.port=18063",
        "http.port=18083"
})
@RunWith(SpringRunner.class)
public class ImprovedigitalBidAdjustmentTest extends ImprovedigitalIntegrationTest {

    @Test
    public void testBidIsDecreasedToMultipleBidders() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                /* This account has "bidPriceAdjustment=0.95, bidPriceAdjustmentIncImprove=true" */ "2022081801",
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
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081802") /* It has "bidPriceAdjustment=1.15, bidPriceAdjustmentIncImprove=true" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(null)
                .sspBid(2.25)
                .expectedSspBidFloor(null)
                .expectedAuctionBid(2.25 * 1.15) /* Bid is increased: 2.5875. */
                .publisherFloorEnabled(true)
                .build());
    }

    @Test
    public void testBidfloorIsDecreased() throws Exception {
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081802") /* It has "bidPriceAdjustment=1.15, bidPriceAdjustmentIncImprove=true" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(BigDecimal.valueOf(0.5))
                .sspBid(2.25)
                .expectedSspBidFloor(BigDecimal.valueOf(0.5 / 1.15)) /* Bidfloor is decreased. 0.4348. */
                .expectedAuctionBid(2.25 * 1.15) /* Bid is increased: 2.5875. */
                .publisherFloorEnabled(true)
                .build());
    }

    @Test
    public void testBidfloorIsIncreased() throws Exception {
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081801") /* It has "bidPriceAdjustment=0.95, bidPriceAdjustmentIncImprove=true" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(BigDecimal.valueOf(0.5))
                .sspBid(1.15)
                .expectedSspBidFloor(BigDecimal.valueOf(0.5 / 0.95)) /* Bidfloor is increased. 0.5263. */
                .expectedAuctionBid(1.15 * 0.95) /* Bid is decreased. 1.0925. */
                .publisherFloorEnabled(true)
                .build());
    }

    @Test
    public void testBidAdjustmentWhenImprovedigitalIsNotEnabledForBidAdjustment() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                /* This account has "bidPriceAdjustment=0.90, bidPriceAdjustmentIncImprove=false" */ "2022081803",
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
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081804") /* It has "bidPriceAdjustment=0.80" and no "bidPriceAdjustmentIncImprove" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(BigDecimal.valueOf(0.5))
                .sspBid(1.15)
                .expectedSspBidFloor(BigDecimal.valueOf(0.5)) /* Bidfloor intact. */
                .expectedAuctionBid(1.15) /* Bid intact. */
                .publisherFloorEnabled(true)
                .build());
    }

    @Test
    public void testBidAdjustmentWhenAccountHasNoValueInExt() throws Exception {
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081805") /* It has no "ext" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(BigDecimal.valueOf(0.5))
                .sspBid(1.15)
                .expectedSspBidFloor(BigDecimal.valueOf(0.5)) /* Bidfloor intact. */
                .expectedAuctionBid(1.15) /* Bid intact. */
                .publisherFloorEnabled(true)
                .build());
    }

    @Test
    public void testBidAdjustmentWhenAccountHasPriceFloorsModuleTurnedOff() throws Exception {
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081806") /* It has "auction.price-floors.enabled=false, bidPriceAdjustment=0.85" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionBidFloor(BigDecimal.valueOf(0.4))
                .sspBid(1.12)
                .expectedSspBidFloor(BigDecimal.valueOf(0.4)) /* Bidfloor intact. */
                .expectedAuctionBid(1.12) /* Bid intact. */
                .publisherFloorEnabled(false)
                .build());
    }

    @Test
    @Ignore
    public void testBidAdjustmentWithNonUsdCurrency() throws Exception {
        doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam.builder()
                .publisherId("2022081802") /* It has "bidPriceAdjustment=1.15, bidPriceAdjustmentIncImprove=true" */
                .storedImpId("2022081502")
                .placementIdOfStoredImp(20220815)
                .auctionCurrency("EUR")
                .auctionBidFloor(BigDecimal.valueOf(0.5))
                .sspCurrency("USD")
                .sspBid(2.25)
                /* SSP will see bidfloor as decreased and in USD. */
                .expectedSspBidFloor(BigDecimal.valueOf(eurToUsd(0.5 / 1.15)))
                /* SSP bid is increased and in EUR. */
                .expectedAuctionBid(usdToEur(2.25 * 1.15))
                .publisherFloorEnabled(true)
                .build());
    }

    private JSONObject doAuctionRequestToImprovedigitalBidder(BidAdjustmentAuctionTestParam param)
            throws JSONException {

        String uniqueId = UUID.randomUUID().toString();
        String auctionCurrency = StringUtils.defaultString(param.auctionCurrency, "USD");
        String sspCurrency = StringUtils.defaultString(param.sspCurrency, "USD");

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                                SSPBidRequestTestData.builder()
                                        .currency(auctionCurrency)
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putStoredRequest(param.storedImpId)
                                                        .putBidder()
                                                        .putBidderKeyValue("placementId", param.placementIdOfStoredImp))
                                                .bannerData(BannerTestParam.getDefault())
                                                .build())
                                        .publisherId(param.publisherId)
                                        .floor(param.expectedSspBidFloor == null ? null : Floor.of(
                                                param.expectedSspBidFloor.setScale(4, RoundingMode.HALF_EVEN),
                                                "USD" /* Regardless of auction currency, it will be USD always. */
                                        ))
                                        .channel(ExtRequestPrebidChannel.of("web"))
                                        .build(),
                                bidRequest -> bidRequest.toBuilder()
                                        .ext(ExtRequest.of(bidRequest.getExt().getPrebid().toBuilder()
                                                .floors(param.getPriceFloorRules())
                                                .build()))
                                        .build()
                        )))
                        .willReturn(aResponse().withBody(getSSPBidResponse(
                                "improvedigital", uniqueId, sspCurrency,
                                BidResponseTestData.builder()
                                        .impId("imp_id_1")
                                        .price(param.sspBid)
                                        .adm("<img src='banner-1.jpg' />")
                                        .build()
                        )))
        );

        Response response = specWithPBSHeader(18083)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency(auctionCurrency)
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(param.storedImpId))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .build()
                        ))
                        .publisherId(param.publisherId)
                        .floor(param.auctionBidFloor == null ? null : Floor.of(
                                param.auctionBidFloor.setScale(4, RoundingMode.HALF_EVEN), auctionCurrency
                        ))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, auctionCurrency);
        assertSeat(responseJson, 0, "improvedigital");
        assertBidPrice(responseJson, 0, 0, param.expectedAuctionBid);

        return responseJson;
    }

    private JSONObject doAuctionRequestToMultipleBidder(
            String publisherId,
            BidAdjustmentMultipleBidderAuctionTestParam param) throws JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/improvedigital-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(param.auctionRequestId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putStoredRequest(param.storedImpId)
                                                        .putBidder()
                                                        .putBidderKeyValue("placementId", param.improvePlacementId))
                                                .bannerData(BannerTestParam.getDefault())
                                                .build())
                                        .publisherId(publisherId)
                                        .channel(ExtRequestPrebidChannel.of("web"))
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
                        .willReturn(aResponse().withBody(getSSPBidResponse(
                                "improvedigital", param.auctionRequestId, "USD",
                                BidResponseTestData.builder()
                                        .impId("imp_id_1")
                                        .price(improvePrice1Value)
                                        .adm(param.improveAdm1)
                                        .build(),
                                BidResponseTestData.builder()
                                        .impId("imp_id_1")
                                        .price(improvePrice2Value)
                                        .adm(param.improveAdm2)
                                        .build()
                        )))
        );

        WIRE_MOCK_RULE.stubFor(
                post(urlPathEqualTo("/generic-exchange"))
                        .withRequestBody(equalToJson(getSSPBidRequest(param.auctionRequestId,
                                SSPBidRequestTestData.builder()
                                        .currency("USD")
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putStoredRequest(param.storedImpId)
                                                        .putBidder()
                                                        .putBidderKeyValue("exampleProperty", "examplePropertyValue"))
                                                .bannerData(BannerTestParam.getDefault())
                                                .build())
                                        .publisherId(publisherId)
                                        .channel(ExtRequestPrebidChannel.of("web"))
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
                        .willReturn(aResponse().withBody(getSSPBidResponse(
                                "generic", param.auctionRequestId, "USD",
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

        Response response = specWithPBSHeader(18083)
                .body(getAuctionBidRequest(param.auctionRequestId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(param.storedImpId)
                                )
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .bannerData(BannerTestParam.getDefault())
                                        .build())
                                .build()))
                        .publisherId(publisherId)
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 2, 1, 1);

        return responseJson;
    }

    @Builder(toBuilder = true)
    private static class BidAdjustmentMultipleBidderAuctionTestParam {
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

    @Builder(toBuilder = true)
    private static class BidAdjustmentAuctionTestParam {
        String publisherId;
        boolean publisherFloorEnabled;
        String storedImpId;
        int placementIdOfStoredImp;
        String auctionCurrency;
        BigDecimal auctionBidFloor;
        double expectedAuctionBid;
        String sspCurrency;
        double sspBid;
        BigDecimal expectedSspBidFloor;

        public PriceFloorRules getPriceFloorRules() {
            if (publisherFloorEnabled) {
                return PriceFloorRules.builder()
                        .enabled(true)
                        .fetchStatus(FetchStatus.none)
                        .location(PriceFloorLocation.noData)
                        .build();
            }
            return PriceFloorRules.builder()
                    .enabled(false)
                    .build();
        }
    }
}
