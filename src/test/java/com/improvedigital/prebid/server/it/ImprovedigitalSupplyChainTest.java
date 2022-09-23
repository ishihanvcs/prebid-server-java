package com.improvedigital.prebid.server.it;

import com.iab.openrtb.request.BidRequest;
import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Builder;
import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtSourceSchain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "admin.port=18062",
        "http.port=18082",
})
@RunWith(SpringRunner.class)
public class ImprovedigitalSupplyChainTest extends ImprovedigitalIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ImprovedigitalSupplyChainTest.class);

    @Test
    public void testSupplyChainIsAddedToMultipleBidders() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        final JSONObject responseJson = doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072201") /* This stored imp has both partner id and schain nodes. */
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
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 1.25);
        assertSeat(responseJson, 0, "generic");
        assertThat(getAdm(responseJson, 0, 0)).contains("<img src='banner-4.png'/>");
        assertThat(getBidExtPrebidType(responseJson, 0, 0)).isEqualTo("banner");

        assertBidIdExists(responseJson, 1, 0);
        assertBidImpId(responseJson, 1, 0, "imp_id_1");
        assertBidPrice(responseJson, 1, 0, 1.15);
        assertSeat(responseJson, 1, "improvedigital");
        assertThat(getAdm(responseJson, 1, 0)).contains("<img src='banner-2.png'/>");
        assertThat(getBidExtPrebidType(responseJson, 1, 0)).isEqualTo("banner");
    }

    @Test
    public void testSupplyChainIsAddedWhenNoSchainNodesAreConfigured() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToImprovedigitalBidder(
                uniqueId,
                /* This stored imp has only partner id. */
                "2022072202",
                20220722,
                List.of(),
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "headerlift.com", /* We expect this to be default. */
                                "hl-2022072202",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsNotAddedWhenEmptySchainNodesAreConfigured() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                UUID.randomUUID().toString(),
                /* This stored imp has partner id and empty schain nodes. */
                "2022072203",
                20220722,
                List.of(),
                List.of() /* Expecting no schain added. */
        );
    }

    @Test
    public void testSupplyChainIsNotAddedWhenNoHeaderliftPartnerIdIsConfigured() throws Exception {
        doAuctionRequestToImprovedigitalBidder(
                UUID.randomUUID().toString(),
                /* This stored imp has no partner id and some schain nodes. */
                "2022072204",
                20220722,
                List.of(),
                List.of() /* Expecting no schain added. */
        );
    }

    @Test
    public void testSupplyChainIsAddedWhenSchainNodesAreAlreadyPresent() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToImprovedigitalBidder(
                uniqueId,
                /* This stored imp has both partner id and schain nodes. */
                "2022072205",
                20220722,
                /* Incoming request has these schain nodes already */
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "secondsite.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        )
                ),
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "secondsite.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "headerlift.com",
                                "hl-2022072205",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsAddedWhenSameSchainNodesAreAlreadyPresent() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToImprovedigitalBidder(
                uniqueId,
                /* This stored imp has both partner id and schain nodes. */
                "2022072205",
                20220722,
                /* Incoming request has these schain nodes already */
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "headerlift.com",
                                "firstsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        )
                ),
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "headerlift.com",
                                "firstsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsAddedForMultiImp() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToImprovedigitalBidderWithMultiImp(
                uniqueId,
                /* This stored imp has both partner id and schain nodes. */
                "2022072205",
                /* Incoming request has these schain nodes already */
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        )
                ),
                List.of(
                        ExtRequestPrebidSchainSchainNode.of(
                                "headerlift.com",
                                "hl-2022072205",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                null
                        ),
                        ExtRequestPrebidSchainSchainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                1,
                                "request_id_" + uniqueId,
                                null,
                                null,
                                null
                        )
                )
        );
    }

    private JSONObject doAuctionRequestToImprovedigitalBidder(
            String uniqueId,
            String storedImpId,
            int placementIdOfStoredImp,
            List<ExtRequestPrebidSchainSchainNode> existingSchainNodes,
            List<ExtRequestPrebidSchainSchainNode> expectedSchainNodes
    ) throws JSONException {

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", placementIdOfStoredImp))
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .build())
                                .schain(ExtSourceSchain.of(
                                        "1.0", 1, expectedSchainNodes, null
                                ))
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse(
                        "improvedigital", uniqueId, "USD",
                        BidResponseTestData.builder()
                                .impId("imp_id_1")
                                .price(1.15)
                                .adm("<img src='banner-1.jpg' />")
                                .build()
                )))
        );

        Response response = specWithPBSHeader(18082)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(storedImpId))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .build())
                                .build()))
                        .schain(ExtSourceSchain.of(
                                "1.0", 1, existingSchainNodes, null
                        ))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 1, 1);
        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 1.15);
        assertSeat(responseJson, 0, "improvedigital");
        assertThat(getAdm(responseJson, 0, 0)).contains("<img src='banner-1.jpg' />");
        assertThat(getBidExtPrebidType(responseJson, 0, 0)).isEqualTo("banner");

        return responseJson;
    }

    private JSONObject doAuctionRequestToImprovedigitalBidderWithMultiImp(
            String uniqueId,
            String storedImpId,
            List<ExtRequestPrebidSchainSchainNode> existingSchainNodes,
            List<ExtRequestPrebidSchainSchainNode> expectedSchainNodes
    ) throws JSONException {

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .willReturn(aResponse()
                        .withTransformers("it-test-bid-response-function-by-impid")
                        .withTransformerParameter("imp_id_1", (Function<BidRequest, String>) request -> {
                            ExtSourceSchain schain = request.getSource().getExt().getSchain();
                            if (!CollectionUtils.isEqualCollection(schain.getNodes(), expectedSchainNodes)) {
                                logger.error("Expected schain nodes didn't match. expected="
                                        + existingSchainNodes + ", found=" + schain.getNodes());
                                return null;
                            }

                            return getSSPBidResponse("improvedigital", uniqueId, "USD",
                                    BidResponseTestData.builder()
                                            .impId("imp_id_1")
                                            .price(1.18)
                                            .adm(getVastXmlInline("ad_1", true))
                                            .build()
                            );
                        })
                        .withTransformerParameter("imp_id_2", (Function<BidRequest, String>) request -> {
                            ExtSourceSchain schain = request.getSource().getExt().getSchain();
                            if (!CollectionUtils.isEqualCollection(schain.getNodes(), expectedSchainNodes)) {
                                logger.error("Expected schain nodes didn't match. expected="
                                        + existingSchainNodes + ", found=" + schain.getNodes());
                                return null;
                            }

                            return getSSPBidResponse("improvedigital", uniqueId, "USD",
                                    BidResponseTestData.builder()
                                            .impId("imp_id_2")
                                            .price(1.12)
                                            .adm("<img src='banner-1.png' />")
                                            .build()
                            );
                        }))
        );

        Response response = specWithPBSHeader(18082)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(
                                AuctionBidRequestImpTestData.builder()
                                        .impExt(new AuctionBidRequestImpExt()
                                                .putBidder("improvedigital")
                                                .putBidderKeyValue("improvedigital", "placementId", 20220923))
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .videoData(VideoTestParam.getDefault())
                                                .build())
                                        .build(),
                                AuctionBidRequestImpTestData.builder()
                                        .impExt(new AuctionBidRequestImpExt()
                                                .putStoredRequest(storedImpId))
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_2")
                                                .build())
                                        .build()
                        ))
                        .schain(ExtSourceSchain.of(
                                "1.0", 1, existingSchainNodes, null
                        ))
                        .test(1) /* To get clear error message in debug key of response. */
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 1, 2);
        assertSeat(responseJson, 0, "improvedigital");

        assertBidIdExists(responseJson, 0, 0);
        assertBidImpId(responseJson, 0, 0, "imp_id_1");
        assertBidPrice(responseJson, 0, 0, 1.18);
        assertThat(getAdm(responseJson, 0, 0)).contains("<VAST");
        assertThat(getBidExtPrebidType(responseJson, 0, 0)).isEqualTo("video");

        assertBidIdExists(responseJson, 0, 1);
        assertBidImpId(responseJson, 0, 1, "imp_id_2");
        assertBidPrice(responseJson, 0, 1, 1.12);
        assertThat(getAdm(responseJson, 0, 1)).contains("<img src='banner-1.png' />");
        assertThat(getBidExtPrebidType(responseJson, 0, 1)).isEqualTo("banner");

        return responseJson;
    }

    private JSONObject doAuctionRequestToMultipleBidder(SupplyChainMultipleBidderAuctionTestParam param)
            throws JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(param.auctionRequestId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", param.improvePlacementId))
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .build())
                                .schain(ExtSourceSchain.of(
                                        "1.0", 1, List.of(
                                                ExtRequestPrebidSchainSchainNode.of(
                                                        "headerlift.com",
                                                        "hl-2022072201",
                                                        1,
                                                        "request_id_" + param.auctionRequestId,
                                                        null,
                                                        "headerlift.com",
                                                        null
                                                )
                                        ), null
                                ))
                                .channel(ExtRequestPrebidChannel.of("web"))
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

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(param.auctionRequestId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("exampleProperty", "examplePropertyValue"))
                                        .bannerData(BannerTestParam.builder()
                                                .w(300)
                                                .h(250)
                                                .build())
                                        .build())
                                .schain(ExtSourceSchain.of(
                                        "1.0", 1, List.of(
                                                ExtRequestPrebidSchainSchainNode.of(
                                                        "headerlift.com",
                                                        "hl-2022072201",
                                                        1,
                                                        "request_id_" + param.auctionRequestId,
                                                        null,
                                                        "headerlift.com",
                                                        null
                                                )
                                        ), null
                                ))
                                .channel(ExtRequestPrebidChannel.of("web"))
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

        Response response = specWithPBSHeader(18082)
                .body(getAuctionBidRequest(param.auctionRequestId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(List.of(AuctionBidRequestImpTestData.builder()
                                .impExt(new AuctionBidRequestImpExt()
                                        .putStoredRequest(param.storedImpId))
                                .impData(SingleImpTestData.builder()
                                        .id("imp_id_1")
                                        .build())
                                .build()))
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 2, 1, 1);

        return responseJson;
    }

    /**
     * Class to deal with many permutation/combination of request/response parameters.
     * This is to avoid long method parameter names code smell.
     */
    @Builder(toBuilder = true)
    public static class SupplyChainMultipleBidderAuctionTestParam {
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
