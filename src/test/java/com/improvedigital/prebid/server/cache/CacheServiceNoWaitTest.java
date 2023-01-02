package com.improvedigital.prebid.server.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.vast.VastModifier;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public class CacheServiceNoWaitTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final CacheTtl mediaTypeCacheTtl = CacheTtl.of(null, null);
    @Mock
    private HttpClient httpClient;
    @Mock
    private EventsService eventsService;
    @Mock
    private VastModifier vastModifier;
    @Mock
    private Metrics metrics;
    @Mock
    private UUIDIdGenerator idGenerator;

    @Captor
    ArgumentCaptor<String> httpRequestCaptor;

    private CacheService cacheService;

    private EventsContext eventsContext;

    private Timeout timeout;

    @Before
    public void setUp() throws MalformedURLException, JsonProcessingException {
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        cacheService = new CacheService(
                mediaTypeCacheTtl,
                httpClient,
                new URL("http://cache-service/cache"),
                "http://cache-service-host/cache?uuid=",
                100L,
                2000L,
                false,
                vastModifier,
                eventsService,
                metrics,
                clock,
                idGenerator,
                jacksonMapper);

        eventsContext = EventsContext.builder().auctionId("auctionId").build();

        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBidsWithoutWaitingForValidHttpResponse()
            throws Exception {
        verifyCacheBidsOpenrtbForBidsAndVideoBidsWithoutWaiting(
                200, mapper.writeValueAsString(
                        BidCacheResponse.of(asList(
                                CacheObject.of("uuid1"),
                                CacheObject.of("uuid2"),
                                CacheObject.of("videoUuid1")
                        ))
                )
        );
    }

    @Test
    public void cacheBidsOpenrtbShouldReturnExpectedResultForBidsAndVideoBidsWithoutWaitingForInvalidHttpResponse()
            throws Exception {
        verifyCacheBidsOpenrtbForBidsAndVideoBidsWithoutWaiting(
                200, mapper.writeValueAsString(
                        BidCacheResponse.of(asList(
                                CacheObject.of("uuid3"),
                                CacheObject.of("uuid4"),
                                CacheObject.of("videoUuid2")
                        ))
                )
        );
        verifyCacheBidsOpenrtbForBidsAndVideoBidsWithoutWaiting(
                400, "invalid_response"
        );
    }

    private void verifyCacheBidsOpenrtbForBidsAndVideoBidsWithoutWaiting(
            int statusCode, String response
    ) throws IOException {

        String[] uuids = new String[]{"uuid1", "uuid2", "videoUuid1"};

        given(idGenerator.generateId()).willReturn(uuids[0], Arrays.stream(uuids).skip(1).toArray(String[]::new));

        givenHttpClientReturnsResponse(statusCode, response);

        final BidInfo bidInfo1 = givenBidInfo(builder -> builder.id("bidId1"), BidType.video, "bidder1");
        final BidInfo bidInfo2 = givenBidInfo(builder -> builder.id("bidId2"), BidType.banner, "bidder2");

        // when
        final Future<CacheServiceResult> future = cacheService.cacheBidsOpenrtb(
                asList(bidInfo1, bidInfo2),
                givenAuctionContext(),
                CacheContext.builder()
                        .shouldCacheBids(true)
                        .shouldCacheVideoBids(true)
                        .build(),
                eventsContext);

        // then
        assertThat(future.result().getCacheBids())
                .hasSize(2)
                .containsOnly(
                        entry(bidInfo1.getBid(), CacheInfo.of("uuid1", "videoUuid1", null, null)),
                        entry(bidInfo2.getBid(), CacheInfo.of("uuid2", null, null, null))
                );

        verifyHttpRequest(uuids);
    }

    @Test
    public void cachePutObjectsShouldModifyVastAndCachePutObjects() throws IOException {
        // given
        final PutObject firstPutObject = PutObject.builder()
                .type("json")
                .bidid("bidId1")
                .bidder("bidder1")
                .timestamp(1L)
                .value(new TextNode("vast"))
                .build();
        final PutObject secondPutObject = PutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder("bidder2")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .build();
        final PutObject thirdPutObject = PutObject.builder()
                .type("text")
                .bidid("bidId3")
                .bidder("bidder3")
                .timestamp(1L)
                .value(new TextNode("VAST"))
                .build();

        given(vastModifier.modifyVastXml(any(), any(), any(), any(), anyString()))
                .willReturn(new TextNode("modifiedVast"))
                .willReturn(new TextNode("VAST"))
                .willReturn(new TextNode("updatedVast"));

        String[] uuids = new String[]{"uuid1", "uuid2", "uuid3"};

        given(idGenerator.generateId()).willReturn(uuids[0], Arrays.stream(uuids).skip(1).toArray(String[]::new));

        givenHttpClientReturnsResponse(200, null);

        // when
        Future<BidCacheResponse> future = cacheService.cachePutObjects(
                asList(firstPutObject, secondPutObject, thirdPutObject),
                true,
                singleton("bidder1"),
                "account",
                "pbjs",
                timeout);

        // then
        assertThat(future.result().getResponses())
                .hasSize(3)
                .containsExactly(
                        CacheObject.of(uuids[0]),
                        CacheObject.of(uuids[1]),
                        CacheObject.of(uuids[2])
                );

        BidCacheRequest bidCacheRequest = verifyHttpRequest(uuids);

        // then
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(12), eq(MetricName.json));
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(4), eq(MetricName.xml));
        verify(metrics).updateCacheCreativeSize(eq("account"), eq(11), eq(MetricName.unknown));

        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), firstPutObject, "account", "pbjs");
        verify(vastModifier).modifyVastXml(true, singleton("bidder1"), secondPutObject, "account", "pbjs");

        final PutObject modifiedFirstPutObject = firstPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .key(uuids[0])
                .timestamp(null)
                .value(new TextNode("modifiedVast"))
                .build();
        final PutObject modifiedSecondPutObject = secondPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .key(uuids[1])
                .timestamp(null)
                .build();
        final PutObject modifiedThirdPutObject = thirdPutObject.toBuilder()
                .bidid(null)
                .bidder(null)
                .key(uuids[2])
                .timestamp(null)
                .value(new TextNode("updatedVast"))
                .build();

        assertThat(bidCacheRequest.getPuts())
                .containsExactly(modifiedFirstPutObject, modifiedSecondPutObject, modifiedThirdPutObject);
    }

    private BidCacheRequest verifyHttpRequest(String... expectedUuids) throws IOException {
        BidCacheRequest request = captureBidCacheRequest();
        assertThat(request.getPuts().stream().map(PutObject::getKey).toList())
                .hasSize(3)
                .containsExactly(expectedUuids);
        return request;
    }

    private AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> accountCustomizer,
                                               UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        final Account.AccountBuilder accountBuilder = Account.builder()
                .id("accountId");
        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .id("auctionId")
                .imp(singletonList(givenImp(identity())));
        return AuctionContext.builder()
                .account(accountCustomizer.apply(accountBuilder).build())
                .bidRequest(bidRequestCustomizer.apply(bidRequestBuilder).build())
                .timeout(timeout)
                .build();
    }

    private AuctionContext givenAuctionContext() {
        return givenAuctionContext(identity(), identity());
    }

    private static BidInfo givenBidInfo(UnaryOperator<Bid.BidBuilder> bidCustomizer,
                                        BidType bidType,
                                        String bidder) {
        return BidInfo.builder()
                .bid(bidCustomizer.apply(Bid.builder()).build())
                .correspondingImp(givenImp(UnaryOperator.identity()))
                .bidder(bidder)
                .bidType(bidType)
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private void givenHttpClientReturnsResponse(int statusCode, String responseBody) {
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(statusCode, null, responseBody)
                ));
    }

    private BidCacheRequest captureBidCacheRequest() throws IOException {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient, atLeastOnce()).post(anyString(), any(), captor.capture(), anyLong());
        return mapper.readValue(captor.getValue(), BidCacheRequest.class);
    }
}
