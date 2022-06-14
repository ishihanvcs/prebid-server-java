package com.improvedigital.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import com.improvedigital.prebid.server.utils.RequestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class GVastBidCreatorTest extends UnitTestBase {

    private static final String EXTERNAL_URL = "https://pbs-proto.360polaris.biz";
    private static final String GAM_NETWORK_CODE = "1015413";
    private static final String PROTO_CACHE_HOST = "euw-pbc-proto.360polaris.biz";
    private static final String PRODUCTION_CACHE_HOST = "euw-pbc.360yield.com";
    private static final Integer IMPROVE_PLACEMENT_ID = 12345;
    private static final double IMPROVE_DIGITAL_DEAL_FLOOR = 1.5;

    static MacroProcessor macroProcessor = new MacroProcessor();

    public GVastBidCreatorTest() {
        this.resourceDir = "com/improvedigital/prebid/server/hooks/v1/gvast";
    }

    @Test
    public void testConstructorParams() {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                null,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                null,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                null,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                null,
                null
        ));

        assertThatNullPointerException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                null
        ));

        assertThatNoException().isThrownBy(() -> new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                PROTO_CACHE_HOST
        ));
    }

    @Test
    public void testGVastWithNoBid() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        bidResponse.getSeatbid().removeIf(seatBid -> true);
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        SeatBid improveSeatBid = createImproveSeatBid();
        Bid result = gVastBidCreator.create(bidRequest.getImp().get(0), improveSeatBid, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testGVastWithBids() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testGVastWithImproveDeal() {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testGVastWithDebugMode() {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testGVastWithCustParams() {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testWaterfallWithNoBid() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testWaterfallWithBids() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testWaterfallWithImproveDeal() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testWaterfallWithDebugMode() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    @Test
    public void testWaterfallWithCustParams() throws Exception {
        BidRequest bidRequest = getBidRequest();
        BidResponse bidResponse = getBidResponse();
        GVastBidCreator gVastBidCreator = createBidCreator(bidRequest, bidResponse);
        Bid result = gVastBidCreator.create(null, null, true);
        assertThat(result).isNotNull();
    }

    private SeatBid createImproveSeatBid() {
        return createImproveSeatBid(null);
    }

    private SeatBid createImproveSeatBid(Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> builderFn) {
        SeatBid.SeatBidBuilder builder = SeatBid.builder();
        builder.seat(RequestUtils.IMPROVE_BIDDER_NAME)
                .bid(new ArrayList<>());
        if (builderFn != null) {
            builder = builderFn.apply(builder);
        }
        return builder.build();
    }

    private GVastBidCreator createBidCreator(BidRequest bidRequest, BidResponse bidResponse) {
        return createBidCreator(bidRequest, bidResponse, PROTO_CACHE_HOST);
    }

    private GVastBidCreator createBidCreator(BidRequest bidRequest, BidResponse bidResponse, String cacheHost) {
        return new GVastBidCreator(
                macroProcessor,
                jsonUtils,
                requestUtils,
                bidRequest,
                bidResponse,
                EXTERNAL_URL,
                GAM_NETWORK_CODE,
                cacheHost
        );
    }

    private BidResponse getBidResponse() {
        return getBidResponse(null);
    }

    private BidResponse getBidResponse(Function<BidResponse, BidResponse> modifier) {
        return getStoredResponse("basic-video", bidResponse -> {
            if (modifier != null) {
                bidResponse = modifier.apply(bidResponse);
            }
            return bidResponse;
        });
    }

    private BidRequest getBidRequest() {
        return getBidRequest(null);
    }

    private BidRequest getBidRequest(Function<BidRequest, BidRequest> modifier) {
        return getStoredRequest("minimal", bidRequest -> {
            Imp imp = getStoredImp("video-basic")
                    .toBuilder().id("video").build();
            bidRequest = bidRequest.toBuilder().imp(
                    new ArrayList<>(List.of(imp))
            ).build();
            if (modifier != null) {
                bidRequest = modifier.apply(bidRequest);
            }
            return bidRequest;
        });
    }
}
