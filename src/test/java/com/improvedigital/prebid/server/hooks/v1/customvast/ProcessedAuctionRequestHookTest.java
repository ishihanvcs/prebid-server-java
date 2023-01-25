package com.improvedigital.prebid.server.hooks.v1.customvast;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.customvast.model.ImprovedigitalPbsImpExt;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import com.improvedigital.prebid.server.utils.PbsEndpointInvoker;
import com.improvedigital.prebid.server.settings.SettingsLoader;
import com.improvedigital.prebid.server.utils.RequestUtils;
import io.vertx.core.Future;
import nl.altindag.log.LogCaptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ProcessedAuctionRequestHookTest extends UnitTestBase {

    @Mock
    PbsEndpointInvoker pbsEndpointInvoker;
    @Mock
    CurrencyConversionService currencyConversionService;
    @Mock
    GeoLocationService geoLocationService;
    @Mock
    Metrics metrics;
    @Mock
    CountryCodeMapper countryCodeMapper;

    @Mock
    SettingsLoader settingsLoader;

    ProcessedAuctionRequestHook hook;

    private final BigDecimal bidFloorInEuro = BigDecimal.valueOf(1.5);
    private final BigDecimal bidFloorInEuro2 = BigDecimal.valueOf(3.5);
    private final BigDecimal usdToEuroConversionRate = BigDecimal.valueOf(1.07);
    private final BigDecimal bidFloorInUsd = bidFloorInEuro
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);
    private final BigDecimal bidFloorInUsd2 = bidFloorInEuro2
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);

    private final Integer improvePlacementId = 12345;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(settingsLoader.getAccountFuture(any(BidRequest.class), any(Timeout.class)))
                .thenReturn(Future.succeededFuture(Account.empty("1")));
        hook = new ProcessedAuctionRequestHook(
                settingsLoader,
                requestUtils,
                new CustomVastUtils(
                        pbsEndpointInvoker, requestUtils, merger,
                        currencyConversionService, macroProcessor,
                        geoLocationService, metrics, countryCodeMapper,
                        EXTERNAL_URL, GAM_NETWORK_CODE, PROTO_CACHE_HOST
                )
        );
    }

    @Test
    public void shouldRejectWhenImprovedigitalPlacementIdIsMissingForAnyImp() {
        BidRequest bidRequest = getStoredRequest(defaultRequestId);
        final LogCaptor logCaptor = LogCaptor.forClass(this.hook.getClass());
        executeHookAndValidateRejectedInvocationResult(
                hook,
                AuctionRequestPayloadImpl.of(bidRequest),
                AuctionInvocationContextImpl.of(
                        InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction), false, null, null
                ),
                (initialPayload, invocationResult) -> {
                    final String message = "improvedigital placementId is not defined in any of the imp(s)";
                    assertThat(invocationResult)
                            .isNotNull();
                    assertThat(invocationResult.message())
                            .isEqualTo(message);
                    assertThat(hasLogEventWith(logCaptor, message, Level.ERROR))
                            .isTrue();
                }
        );
    }

    @Test
    public void shouldNotUpdateExtInBidRequestWhenNoCustomVastVideoImpIsPresent() {
        BidRequest bidRequest = getDefaultBidRequest();
        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> assertThat(updatedBidRequest.getExt())
                        .isNull()
        );
    }

    @Test
    public void shouldUpdateExtInBidRequestWhenCustomVastVideoImpIsPresent() {
        BidRequest waterfallBidRequest = getDefaultBidRequest(bidRequest1 -> {
            Imp imp = getStoredImp(defaultStoredImpId,
                    imp1 -> setImpConfigProperties(
                            imp1, configNode -> configNode.put(
                                    "responseType",
                                    VastResponseType.waterfall.name())
                    ).toBuilder().id("1").build());

            return bidRequest1.toBuilder().imp(
                    new ArrayList<>(List.of(imp))
            ).build();
        });

        BiConsumer<BidRequest, BidRequest> commonValidator = (originalBidRequest, updatedBidRequest) -> {
            assertThat(updatedBidRequest.getExt())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getCache())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getCache().getVastxml())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getCache().getVastxml().getReturnCreative())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getCache().getVastxml().getReturnCreative())
                    .isTrue();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity()
                            .at("/precision").isInt()
                    ).isTrue();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity()
                    .at("/precision").asInt()
            ).isEqualTo(2);
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity()
                    .at("/ranges").isArray()
            ).isTrue();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getPricegranularity()
                    .at("/ranges").size()
            ).isEqualTo(5);
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludeformat())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludeformat())
                    .isTrue();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludewinners())
                    .isNotNull();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludebidderkeys())
                    .isNotNull();
        };

        BiConsumer<BidRequest, BidRequest> defaultExtValidator = (originalBidRequest, updatedBidRequest) -> {
            commonValidator.accept(originalBidRequest, updatedBidRequest);
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludewinners())
                    .isTrue();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludebidderkeys())
                    .isTrue();
        };

        BiConsumer<BidRequest, BidRequest> mergedExtValidator = (originalBidRequest, updatedBidRequest) -> {
            commonValidator.accept(originalBidRequest, updatedBidRequest);
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludewinners())
                    .isFalse();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludebidderkeys())
                    .isFalse();
        };

        BiConsumer<BidRequest, BidRequest> mergedExtValidatorGVast = (originalBidRequest, updatedBidRequest) -> {
            commonValidator.accept(originalBidRequest, updatedBidRequest);
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludewinners())
                    .isFalse();
            assertThat(updatedBidRequest.getExt().getPrebid().getTargeting().getIncludebidderkeys())
                    .isTrue();
        };

        executeHookAndValidateBidRequest(
                waterfallBidRequest,
                timeout,
                defaultExtValidator
        );

        // set ext.prebid.targeting.includebidderkeys=false
        ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder()
                        .includewinners(false)
                        .includebidderkeys(false)
                        .build()
                ).build());

        BidRequest waterfallBidRequestWithExtRequest = waterfallBidRequest
                .toBuilder()
                .ext(extRequest)
                .build();

        executeHookAndValidateBidRequest(
                waterfallBidRequestWithExtRequest,
                timeout,
                mergedExtValidator
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

        executeHookAndValidateBidRequest(
                gVastBidRequest,
                timeout,
                defaultExtValidator
        );

        gVastBidRequest = gVastBidRequest.toBuilder().ext(extRequest).build();

        executeHookAndValidateBidRequest(
                gVastBidRequest,
                timeout,
                mergedExtValidatorGVast
        );
    }

    @Test
    public void shouldUpdateImpsWithBidFloorInUsdIfNeeded() {
        // imp does not have bidfloor defined, neither in request, nor in config
        BidRequest bidRequest = getBidRequestForBidFloorTest(null, null, null, null);

        executeHookAndValidateBidRequest(bidRequest, timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, null)
        );

        // bidfloor defined in imp, but not in config
        bidRequest = getBidRequestForBidFloorTest(bidFloorInUsd, null, null, null);

        executeHookAndValidateBidRequest(bidRequest, timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor & bidfloorcur defined in imp, but not in config (EUR)
        bidRequest = getBidRequestForBidFloorTest(bidFloorInEuro, "EUR", null, null);

        executeHookAndValidateBidRequest(bidRequest, timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor not defined in imp, but defined in config (USD)
        bidRequest = getBidRequestForBidFloorTest(null, null, bidFloorInUsd, null);

        executeHookAndValidateBidRequest(bidRequest, timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor not defined in imp, but defined in config (EUR)
        bidRequest = getBidRequestForBidFloorTest(null, null, bidFloorInEuro, "EUR");

        executeHookAndValidateBidRequest(bidRequest, timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor defined in both imp and config (no currency set anywhere)
        bidRequest = getBidRequestForBidFloorTest(bidFloorInUsd, null, bidFloorInEuro, null);

        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor defined in both imp and config (currency set in imp: USD)
        bidRequest = getBidRequestForBidFloorTest(bidFloorInUsd, "USD", bidFloorInEuro2, null);

        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor defined in both imp and config (currency set in imp: EUR)
        bidRequest = getBidRequestForBidFloorTest(bidFloorInEuro, "EUR", bidFloorInEuro2, null);

        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );

        // bidfloor defined in both imp and config (currency set in config should be ignored)
        bidRequest = getBidRequestForBidFloorTest(bidFloorInUsd, null, bidFloorInEuro2, "EUR");

        executeHookAndValidateBidRequest(
                bidRequest,
                timeout,
                (originalBidRequest, updatedBidRequest) -> bidFloorValidator(updatedBidRequest, bidFloorInUsd)
        );
    }

    private void bidFloorValidator(BidRequest updatedBidRequest, BigDecimal floorInUsd) {
        Imp imp = updatedBidRequest.getImp().get(0);
        if (floorInUsd == null) {
            assertThat(imp.getBidfloor())
                    .isNull();
            assertThat(imp.getBidfloorcur())
                    .isNull();
        } else {
            assertThat(imp.getBidfloor())
                    .isEqualTo(floorInUsd);
            assertThat(imp.getBidfloorcur())
                    .isEqualTo("USD");
        }
    }

    private BidRequest getBidRequestForBidFloorTest(
            BigDecimal impBidFloor, String impBidFloorCur, BigDecimal configBidFloor, String configBidFloorCur
    ) {
        return getDefaultBidRequest(bidRequest1 -> {
            Imp.ImpBuilder impBuilder = getStoredImp(defaultStoredImpId)
                    .toBuilder()
                    .id("1");
            if (impBidFloor != null) {
                impBuilder.bidfloor(impBidFloor);
            }

            if (StringUtils.isNotBlank(impBidFloorCur)) {
                impBuilder.bidfloorcur(impBidFloorCur);
            }

            Imp imp = impBuilder.build();

            if (configBidFloor != null || StringUtils.isNotBlank(configBidFloorCur)) {
                imp = setImpConfigProperties(imp, configNode -> {
                    ObjectNode floor = mapper.createObjectNode();
                    if (configBidFloor != null) {
                        floor.put("bidFloor", configBidFloor);
                    }

                    if (StringUtils.isNotBlank(configBidFloorCur)) {
                        floor.put("bidFloorCur", configBidFloorCur);
                    }
                    ObjectNode floors = configNode.putObject("floors");
                    floors.set(ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, floor);
                });
            }
            return bidRequest1.toBuilder().imp(
                    new ArrayList<>(List.of(imp))
            ).build();
        });
    }

    @Test
    public void testCode() throws Exception {
        String result = hook.code();
        assertThat(result)
                .isEqualTo("improvedigital-custom-vast-hooks-processed-auction-request");
    }

    private void mockCurrencyConversionService(BidRequest bidRequest) {
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD"
        )).thenReturn(bidFloorInUsd);

        when(currencyConversionService.convertCurrency(
                bidFloorInEuro2, bidRequest, "EUR", "USD"
        )).thenReturn(bidFloorInUsd2);
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
        return getStoredRequest(defaultRequestId, bidRequest -> {
            if (modifier != null) {
                bidRequest = modifier.apply(bidRequest);
            }
            bidRequest.getImp().replaceAll(imp -> setImpBidderProperties(
                    imp,
                    RequestUtils.IMPROVE_DIGITAL_BIDDER_NAME,
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
                    assertThat(updatedBidRequest)
                            .isNotNull();
                    validator.accept(originalBidRequest, updatedBidRequest);
                }
        );
    }
}

