package com.improvedigital.prebid.server.customvast.model;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ImprovedigitalPbsImpExtTest extends VertxTest {

    private final BigDecimal bidFloorDefault = BigDecimal.valueOf(1.5);
    private final BigDecimal bidFloorUSA = BigDecimal.valueOf(2.5);

    private final Floor floorDefault = Floor.of(bidFloorDefault, "EUR");
    private final Floor floorUSA = Floor.of(bidFloorUSA, "USD");

    private final List<String> waterfallDefault = List.of("gam", "gam_first_look");
    private final List<String> waterfallUSA = List.of("gam_no_hb", "blah.com");

    private final String alpha3USA = "USA";
    private final String alpha3BGD = "BGD";

    private ImprovedigitalPbsImpExt target;

    @Before
    public void setup() {
        target = ImprovedigitalPbsImpExt.builder()
                .floors(Map.of(
                        ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, floorDefault,
                        "USA", floorUSA
                ))
                .waterfalls(Map.of(
                        ImprovedigitalPbsImpExt.DEFAULT_CONFIG_KEY, waterfallDefault,
                        "USA", waterfallUSA
                ))
                .build();
    }

    @Test
    public void responseTypeOrDefaultShouldReturnVastForNull() {
        VastResponseType result = target.responseTypeOrDefault();
        assertThat(result).isEqualTo(VastResponseType.vast);
    }

    @Test
    public void responseTypeOrDefaultShouldReturnActualValueWhenValueIsNonNull() {
        target = target.toBuilder().responseType(VastResponseType.gvast).build();
        VastResponseType result = target.responseTypeOrDefault();
        assertThat(result).isEqualTo(VastResponseType.gvast);
    }

    @Test
    public void getFloorShouldReturnConfiguredValueForConfiguredCountry() {
        Floor result = target.getFloor(alpha3USA);
        assertThat(result).usingRecursiveComparison().isEqualTo(floorUSA);
    }

    @Test
    public void getFloorShouldReturnDefaultValueForNotConfiguredCountry() {
        Floor result = target.getFloor(alpha3BGD);
        assertThat(result).usingRecursiveComparison().isEqualTo(floorDefault);
    }

    @Test
    public void getWaterfallShouldReturnConfiguredValueForConfiguredCountry() {
        List<String> result = target.getWaterfall(alpha3USA);
        assertThat(result).usingRecursiveComparison().isEqualTo(waterfallUSA);
    }

    @Test
    public void getWaterfallShouldReturnConfiguredValueForNotConfiguredCountry() {
        List<String> result = target.getWaterfall(alpha3BGD);
        assertThat(result).usingRecursiveComparison().isEqualTo(waterfallDefault);
    }
}
