package com.improvedigital.prebid.server.hooks.v1.gvast;

import ch.qos.logback.classic.Level;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.auction.model.VastResponseType;
import com.improvedigital.prebid.server.hooks.v1.HooksTestBase;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.mockito.Mockito.when;

public class ProcessedAuctionRequestHookTest extends HooksTestBase {

    @Mock
    CurrencyConversionService currencyConversionService;

    ProcessedAuctionRequestHook hook;

    private final BigDecimal bidFloorInEuro = BigDecimal.valueOf(1.5);
    private final BigDecimal usdToEuroConversionRate = BigDecimal.valueOf(1.07);
    private final BigDecimal bidFloorInUsd = bidFloorInEuro
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);
    private final Integer improvePlacementId = 12345;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        hook = new ProcessedAuctionRequestHook(
                requestUtils,
                currencyConversionService
        );
    }

    @Test
    public void shouldRejectWhenImprovedigitalPlacementIdIsMissingForAnyImp() {
        BidRequest bidRequest = getStoredRequest("minimal");
        final LogCaptor logCaptor = LogCaptor.forClass(this.hook.getClass());
        executeHookAndValidateRejectedInvocationResult(
                hook,
                AuctionRequestPayloadImpl.of(bidRequest),
                AuctionInvocationContextImpl.of(null, false, null, null),
                (initialPayload, invocationResult) -> {
                    final String message = "improvedigital placementId is not defined for one or more imp(s)";
                    Assertions.assertThat(invocationResult)
                            .isNotNull();
                    Assertions.assertThat(invocationResult.message())
                            .isEqualTo(message);
                    Assertions.assertThat(hasLogEventWith(logCaptor, message, Level.ERROR))
                            .isTrue();
                }
        );
    }

    @Test
    public void shouldNotUpdateExtInBidRequestWhenNoNonVastVideoImpIsPresent() {
        BidRequest bidRequest = getDefaultBidRequest();
        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> Assertions.assertThat(updatedBidRequest.getExt())
                        .isNull()
        );
    }

    @Test
    public void shouldUpdateExtInBidRequestWhenNonVastVideoImpIsPresent() {
        BidRequest waterfallBidRequest = getDefaultBidRequest(bidRequest1 -> {
            Imp imp = getStoredImp("video-basic",
                    imp1 -> setImpConfigProperties(
                            imp1, configNode -> configNode.put(
                                    "responseType",
                                    VastResponseType.waterfall.name())
                    ).toBuilder().id("1").build());

            return bidRequest1.toBuilder().imp(
                    new ArrayList<>(List.of(imp))
            ).build();
        });

        BiConsumer<BidRequest, BidRequest> defaultExtValidator = (originalBidRequest, updatedBidRequest) -> {
            Assertions.assertThat(updatedBidRequest.getExt())
                    .isNotNull();
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid())
                    .isNotNull();
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid().getCache())
                    .isNotNull();
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid().getCache().getVastxml())
                    .isNotNull();
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid().getTargeting())
                    .isNotNull();
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity())
                    .isNotNull();
        };

        executeHookAndValidateBidRequest(
                waterfallBidRequest,
                timeout,
                defaultExtValidator
        );

        BidRequest gVastBidRequest = waterfallBidRequest.toBuilder().build();

        gVastBidRequest.getImp().replaceAll(
                imp -> setImpConfigProperties(
                        imp, configNode -> configNode.put(
                                "responseType",
                                VastResponseType.gvast.name()
                        )
                )
        );

        BiConsumer<BidRequest, BidRequest> extValidatorForGVast = (originalBidRequest, updatedBidRequest) -> {
            defaultExtValidator.accept(originalBidRequest, updatedBidRequest);
            Assertions.assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludebidderkeys())
                    .describedAs("ext.prebid.targeting.includebidderkeys should be true")
                    .isTrue();
        };

        executeHookAndValidateBidRequest(
                gVastBidRequest,
                timeout,
                extValidatorForGVast
        );

        // set ext.prebid.targeting.includebidderkeys=false in gVastBidRequest
        ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder()
                        .includebidderkeys(false)
                        .build()
                ).build());

        gVastBidRequest = gVastBidRequest.toBuilder().ext(extRequest).build();

        executeHookAndValidateBidRequest(
                gVastBidRequest,
                timeout,
                extValidatorForGVast
        );
    }

    @Test
    public void testCode() throws Exception {
        String result = hook.code();
        Assertions.assertThat(result)
                .isEqualTo("improvedigital-gvast-hooks-processed-auction-request");
    }

    private void mockCurrencyConversionService(BidRequest bidRequest) {
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD"
        )).thenReturn(bidFloorInUsd);

        when(currencyConversionService.convertCurrency(
                bidFloorInUsd, bidRequest, "USD", "EUR"
        )).thenReturn(bidFloorInEuro);
    }

    @Override
    protected BidRequest getStoredRequest(String storedRequestId, Function<BidRequest, BidRequest> modifier) {
        final BidRequest request = super.getStoredRequest(storedRequestId, modifier);
        mockCurrencyConversionService(request);
        return request;
    }

    private BidRequest getDefaultBidRequest() {
        return getDefaultBidRequest(null);
    }

    private BidRequest getDefaultBidRequest(Function<BidRequest, BidRequest> modifier) {
        return getStoredRequest("minimal", bidRequest -> {
            if (modifier != null) {
                bidRequest = modifier.apply(bidRequest);
            }
            bidRequest.getImp().replaceAll(imp -> setImpBidderProperties(
                    imp,
                    "improvedigital",
                    bidderNode -> bidderNode.put(
                            "placementId",
                            improvePlacementId
                    )
            ));
            return bidRequest;
        });
    }

    private void executeHookAndValidateBidRequest(
            BidRequest originalBidRequest,
            Timeout timeout,
            BiConsumer<BidRequest/*originalBidRequest*/, BidRequest/*updatedBidRequest*/> validator
    ) {
        executeHookAndValidatePayloadUpdate(
                hook,
                AuctionRequestPayloadImpl.of(originalBidRequest),
                AuctionInvocationContextImpl.of(
                        InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                        false, null, null
                ),
                (initialPayload, updatedPayload) -> {
                    BidRequest updatedBidRequest = updatedPayload.bidRequest();
                    Assertions.assertThat(updatedBidRequest)
                            .isNotNull();
                    validator.accept(originalBidRequest, updatedBidRequest);
                }
        );
    }
}

