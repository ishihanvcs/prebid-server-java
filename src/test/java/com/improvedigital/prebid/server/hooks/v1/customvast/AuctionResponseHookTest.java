package com.improvedigital.prebid.server.hooks.v1.customvast;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.improvedigital.prebid.server.UnitTestBase;
import com.improvedigital.prebid.server.customvast.CustomVastUtils;
import com.improvedigital.prebid.server.customvast.model.HooksModuleContext;
import com.improvedigital.prebid.server.customvast.model.VastResponseType;
import com.improvedigital.prebid.server.utils.MacroProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.hooks.execution.v1.InvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionInvocationContextImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AuctionResponseHookTest extends UnitTestBase {

    private static final String EXTERNAL_URL = "https://pbs-proto.360polaris.biz";
    private static final String GAM_NETWORK_CODE = "1015413";
    private static final String CACHE_HOST = "euw-pbc-proto.360polaris.biz";

    private final BigDecimal bidFloorInEuro = BigDecimal.valueOf(1.5);
    private final BigDecimal usdToEuroConversionRate = BigDecimal.valueOf(1.07);
    private final BigDecimal bidFloorInUsd = bidFloorInEuro
            .multiply(usdToEuroConversionRate)
            .setScale(3, RoundingMode.HALF_EVEN);

    static MacroProcessor macroProcessor = new MacroProcessor();

    @Mock
    CurrencyConversionService currencyConversionService;
    @Mock
    GeoLocationService geoLocationService;
    @Mock
    Metrics metrics;
    @Mock
    CountryCodeMapper countryCodeMapper;

    AuctionResponseHook hook;
    CustomVastUtils customVastUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        customVastUtils = new CustomVastUtils(
                requestUtils, merger, currencyConversionService, macroProcessor,
                geoLocationService, metrics, countryCodeMapper,
                EXTERNAL_URL, GAM_NETWORK_CODE, PROTO_CACHE_HOST
        );
        hook = new AuctionResponseHook(
                requestUtils, customVastUtils
        );
    }

    @Test
    public void shouldNotModifyBidResponseWhenModuleContextIsNull() {
        executeHookAndValidateBidResponse(
                getBidResponse(),
                null,
                timeout,
                (originalResponse, updatedResponse) -> {
                    assertThat(updatedResponse).isNotNull();
                    assertThat(updatedResponse).isEqualTo(originalResponse);
                }
        );
    }

    @Test
    public void shouldNotModifyBidResponseWhenModuleContextIsNotGVastHooksModuleContext() {
        executeHookAndValidateBidResponse(
                getBidResponse(),
                new Object(),
                timeout,
                (originalResponse, updatedResponse) -> {
                    assertThat(updatedResponse).isNotNull();
                    assertThat(updatedResponse).isEqualTo(originalResponse);
                }
        );
    }

    @Test
    public void shouldNotModifyBidResponseWhenNoImpOfCustomVastVideoIsPresentInRequest() {
        HooksModuleContext moduleContext = getModuleContext(bidRequest -> {
            bidRequest.getImp().replaceAll(imp -> setImpConfigProperties(imp, configNode -> {
                configNode.put("responseType", VastResponseType.vast.name());
            }));
            return bidRequest;
        });
        executeHookAndValidateBidResponse(
                getBidResponse(),
                moduleContext,
                timeout,
                (originalResponse, updatedResponse) -> {
                    assertThat(updatedResponse).isNotNull();
                    assertThat(updatedResponse).isEqualTo(originalResponse);
                }
        );
    }

    @Test
    public void testCode() {
        String result = hook.code();
        assertThat(result).isEqualTo("improvedigital-custom-vast-hooks-auction-response");
    }

    private void executeHookAndValidateBidResponse(
            BidResponse originalBidResponse,
            Object moduleContext,
            Timeout timeout,
            BiConsumer<BidResponse/*originalBidResponse*/, BidResponse/*updatedBidResponse*/> validator
    ) {
        executeHookAndValidatePayloadUpdate(
                hook,
                AuctionResponsePayloadImpl.of(originalBidResponse),
                AuctionInvocationContextImpl.of(
                        InvocationContextImpl.of(timeout, Endpoint.openrtb2_auction),
                        false, null, moduleContext
                ),
                (initialPayload, updatedPayload) -> {
                    BidResponse updatedBidResponse = updatedPayload.bidResponse();
                    assertThat(updatedBidResponse)
                            .isNotNull();
                    validator.accept(originalBidResponse, updatedBidResponse);
                }
        );
    }

    private BidResponse getBidResponse() {
        return getBidResponse(null);
    }

    private BidResponse getBidResponse(Function<BidResponse, BidResponse> modifier) {
        return getStoredResponse(defaultResponseId, bidResponse -> {
            if (modifier != null) {
                bidResponse = modifier.apply(bidResponse);
            }
            return bidResponse;
        });
    }

    private HooksModuleContext getModuleContext() {
        return getModuleContext(null);
    }

    private HooksModuleContext getModuleContext(Function<BidRequest, BidRequest> requestModifier) {
        BidRequest bidRequest = getBidRequest(requestModifier);
        return customVastUtils.createModuleContext(bidRequest, null);
    }

    private BidRequest getBidRequest() {
        return getBidRequest(null);
    }

    private BidRequest getBidRequest(Function<BidRequest, BidRequest> modifier) {
        return getStoredRequest(defaultRequestId, bidRequest -> {
            Imp imp = getStoredImp(defaultStoredImpId)
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

    @Override
    protected BidRequest getStoredRequest(String storedRequestId, Function<BidRequest, BidRequest> modifier) {
        final BidRequest bidRequest = super.getStoredRequest(storedRequestId, modifier);
        when(currencyConversionService.convertCurrency(
                bidFloorInEuro, bidRequest, "EUR", "USD"
        )).thenReturn(bidFloorInUsd);
        when(currencyConversionService.convertCurrency(
                bidFloorInUsd, bidRequest, "USD", "USD"
        )).thenReturn(bidFloorInUsd);
        return bidRequest;
    }
}
