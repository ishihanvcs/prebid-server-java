package com.improvedigital.prebid.server.it;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import io.restassured.response.Response;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Builder;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                        .build(),
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "headerlift.com",
                                "hl-2022072201",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        )
                )
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

        doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072202") /* This stored imp has only partner id. */
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build(),
                List.of(),
                List.of(
                        SupplyChainNode.of(
                                "headerlift.com", /* We expect this to be default. */
                                "hl-2022072202",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsNotAddedWhenEmptySchainNodesAreConfigured() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072203") /* This stored imp has partner id and empty schain nodes. */
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build(),
                List.of(),
                List.of() /* Expecting no schain added. */
        );
    }

    @Test
    public void testSupplyChainIsNotAddedWhenNoHeaderliftPartnerIdIsConfigured() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072204") /* This stored imp has no partner id and some schain nodes. */
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build(),
                List.of(),
                List.of() /* Expecting no schain added. */
        );
    }

    @Test
    public void testSupplyChainIsAddedWhenSchainNodesAreAlreadyPresent() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072205") /* This stored imp has both partner id and schain nodes. */
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build(),
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "secondsite.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "firstsite.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "secondsite.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "headerlift.com",
                                "hl-2022072205",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsAddedWhenSameSchainNodesAreAlreadyPresent() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToMultipleBidder(
                SupplyChainMultipleBidderAuctionTestParam.builder()
                        .auctionRequestId(uniqueId)
                        .storedImpId("2022072205") /* This stored imp has both partner id and schain nodes. */
                        .improvePlacementId(20220722)
                        .improveAdm1("<img src='banner-1.png'/>")
                        .improvePrice1("1.10")
                        .improveAdm2("<img src='banner-2.png'/>")
                        .improvePrice2("1.15")
                        .genericAdm1("<img src='banner-3.png'/>")
                        .genericPrice1("1.20")
                        .genericAdm2("<img src='banner-4.png'/>")
                        .genericPrice2("1.25")
                        .build(),
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "headerlift.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "headerlift.com",
                                "firstsite-1",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                )
        );
    }

    @Test
    public void testSupplyChainIsAddedForMultiImp() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToGenericBidderWithMultiImp(
                uniqueId,
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        ),
                        SupplyChainNode.of(
                                "headerlift.com",
                                "hl-2022092701",
                                "request_id_" + uniqueId,
                                null,
                                "headerlift.com",
                                1,
                                null
                        )
                ),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_1")
                        .price(1.12)
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_2")
                        .price(1.23)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(List.of("headerlift.com", "pubgalaxy.com"))
                        .build()
        );
    }

    @Test
    public void testSupplyChainIsAddedForMultiImpWithDifferentHeaderPartnerId() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToGenericBidderWithMultiImp(
                uniqueId,
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_1")
                        .price(1.12)
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_2")
                        .price(1.23)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(List.of("headerlift.com", "pubgalaxy.com"))
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_3")
                        .price(1.34)
                        .headerliftPartnerId("hl-2022092702") /* Different partner id */
                        .schainNodes(List.of("headerlift.com", "pubgalaxy.com"))
                        .build()
        );
    }

    @Test
    public void testSupplyChainIsAddedForMultiImpWithDifferentSchainNodes() throws Exception {
        String uniqueId = UUID.randomUUID().toString();

        doAuctionRequestToGenericBidderWithMultiImp(
                uniqueId,
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_1")
                        .price(1.12)
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_2")
                        .price(1.23)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(List.of("headerlift.com", "pubgalaxy.com"))
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_3")
                        .price(1.34)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(List.of("headerlift.com", "newdomain.com")) /* Different schain nodes */
                        .build()
        );

        doAuctionRequestToGenericBidderWithMultiImp(
                uniqueId,
                /* Incoming request has these schain nodes already */
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                List.of(
                        SupplyChainNode.of(
                                "pubgalaxy.com",
                                "secondsite-1",
                                "request_id_" + uniqueId,
                                null,
                                null,
                                1,
                                null
                        )
                ),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_1")
                        .price(1.12)
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_2")
                        .price(1.23)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(List.of("headerlift.com", "pubgalaxy.com"))
                        .build(),
                SupplyChainMultiImpTestParam.builder()
                        .impId("imp_id_3")
                        .price(1.34)
                        .headerliftPartnerId("hl-2022092701")
                        .schainNodes(null) /* Different schain nodes */
                        .build()
        );
    }

    private JSONObject doAuctionRequestToGenericBidderWithMultiImp(
            String uniqueId,
            List<SupplyChainNode> existingSchainNodes,
            List<SupplyChainNode> expectedSchainNodes,
            SupplyChainMultiImpTestParam... params
    ) throws JSONException {

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .willReturn(aResponse()
                        .withTransformers("it-test-bid-response-function-by-impid")
                        .withTransformerParameters(Arrays.stream(params).collect(Collectors.toMap(
                                param -> param.impId,
                                param -> (Function<BidRequest, String>) request -> {
                                    SupplyChain schain = request.getSource().getExt().getSchain();
                                    // For improvedigital, we only get what we had on initial request.
                                    if (!Objects.equals(schain.getNodes(), existingSchainNodes)) {
                                        logger.error("Expected schain nodes didn't match. expected="
                                                + expectedSchainNodes + ", found=" + schain.getNodes());
                                        return null;
                                    }

                                    return getSSPBidResponse("improvedigital", uniqueId, "USD",
                                            BidResponseTestData.builder()
                                                    .impId(param.impId)
                                                    .price(param.price)
                                                    .adm("<img src='banner-improvedigital-" + param.impId + ".png' />")
                                                    .build()
                                    );
                                }))))
        );

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(uniqueId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .imps(Arrays.stream(params)
                                        .map(param -> SSPBidRequestImpTestData.builder()
                                                .impExt(new SSPBidRequestImpExt()
                                                        .putBidder())
                                                .impData(SingleImpTestData.builder()
                                                        .id(param.impId)
                                                        .bannerData(BannerTestParam.getDefault())
                                                        .build())
                                                .build())
                                        .collect(Collectors.toList())
                                )
                                .schain(SupplyChain.of(
                                        1, expectedSchainNodes, "1.0", null
                                ))
                                .channel(ExtRequestPrebidChannel.of("web"))
                                .test(1)
                                .build()
                )))
                .willReturn(aResponse().withBody(getSSPBidResponse("generic", uniqueId, "USD",
                        Arrays.stream(params)
                                .map(param -> BidResponseTestData.builder()
                                        .impId(param.impId)
                                        .price(param.price)
                                        .adm("<img src='banner-generic-" + param.impId + ".png' />")
                                        .build())
                                .collect(Collectors.toList())
                )))
        );

        Response response = specWithPBSHeader(18082)
                .body(getAuctionBidRequest(uniqueId, AuctionBidRequestTestData.builder()
                        .currency("USD")
                        .imps(Arrays.stream(params)
                                .map(param -> AuctionBidRequestImpTestData.builder()
                                        .impExt(new AuctionBidRequestImpExt()
                                                .putImprovedigitalPbs()
                                                .putImprovedigitalPbsKeyValue(
                                                        "headerliftPartnerId", param.headerliftPartnerId
                                                )
                                                .putImprovedigitalPbsKeyValue("schainNodes", param.schainNodes)
                                                .putBidder("improvedigital")
                                                .putBidderKeyValue("improvedigital", "placementId", 20220923)
                                                .putBidder("generic"))
                                        .impData(SingleImpTestData.builder()
                                                .id(param.impId)
                                                .bannerData(BannerTestParam.getDefault())
                                                .build())
                                        .build())
                                .collect(Collectors.toList()))
                        .schain(SupplyChain.of(
                                1, existingSchainNodes, "1.0", null
                        ))
                        .test(1) /* To get clear error message in debug key of response. */
                        .build()
                ))
                .post(Endpoint.openrtb2_auction.value());

        JSONObject responseJson = new JSONObject(response.asString());
        assertNoExtErrors(responseJson);
        assertCurrency(responseJson, "USD");
        assertBidCount(responseJson, 2, params.length, params.length);
        assertSeat(responseJson, 0, "generic");
        assertSeat(responseJson, 1, "improvedigital");

        // The response will be as we sent. The only check here is, the schain nodes in SSP requests.
        // If those expected schain nodes don't match, response will be empty.
        for (int i = 0; i < params.length; i++) {
            SupplyChainMultiImpTestParam param = params[i];

            assertBidIdExists(responseJson, 0, i);
            assertBidImpId(responseJson, 0, i, param.impId);
            assertBidPrice(responseJson, 0, i, param.price);
            assertThat(getAdm(responseJson, 0, i)).contains(
                    "<img src='banner-generic-" + param.impId + ".png' />"
            );
            assertThat(getBidExtPrebidType(responseJson, 0, i)).isEqualTo("banner");

            assertBidIdExists(responseJson, 1, i);
            assertBidImpId(responseJson, 1, i, param.impId);
            assertBidPrice(responseJson, 1, i, param.price);
            assertThat(getAdm(responseJson, 1, i)).contains(
                    "<img src='banner-improvedigital-" + param.impId + ".png' />"
            );
            assertThat(getBidExtPrebidType(responseJson, 1, i)).isEqualTo("banner");
        }

        return responseJson;
    }

    private JSONObject doAuctionRequestToMultipleBidder(
            SupplyChainMultipleBidderAuctionTestParam param,
            List<SupplyChainNode> existingSchainNodes,
            List<SupplyChainNode> expectedSchainNodes
    ) throws JSONException {

        double improvePrice1Value = Double.parseDouble(param.improvePrice1);
        double improvePrice2Value = Double.parseDouble(param.improvePrice2);

        double genericPrice1Value = Double.parseDouble(param.genericPrice1);
        double genericPrice2Value = Double.parseDouble(param.genericPrice2);

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(getSSPBidRequest(param.auctionRequestId,
                        SSPBidRequestTestData.builder()
                                .currency("USD")
                                .imps(List.of(SSPBidRequestImpTestData.builder()
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("placementId", param.improvePlacementId))
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .bannerData(BannerTestParam.builder()
                                                        .w(300)
                                                        .h(250)
                                                        .build())
                                                .build())
                                        .build()
                                ))
                                .schain(SupplyChain.of(
                                        1, existingSchainNodes, "1.0", null
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
                                .imps(List.of(SSPBidRequestImpTestData.builder()
                                        .impExt(new SSPBidRequestImpExt()
                                                .putStoredRequest(param.storedImpId)
                                                .putBidder()
                                                .putBidderKeyValue("exampleProperty", "examplePropertyValue"))
                                        .impData(SingleImpTestData.builder()
                                                .id("imp_id_1")
                                                .bannerData(BannerTestParam.builder()
                                                        .w(300)
                                                        .h(250)
                                                        .build())
                                                .build())
                                        .build()
                                ))
                                .schain(SupplyChain.of(
                                        1, expectedSchainNodes, "1.0", null
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
                        .schain(SupplyChain.of(
                                1, existingSchainNodes, "1.0", null
                        ))
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
    private static class SupplyChainMultipleBidderAuctionTestParam {
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
    private static class SupplyChainMultiImpTestParam {
        String impId;
        String headerliftPartnerId;
        List<String> schainNodes;
        double price;
    }
}
