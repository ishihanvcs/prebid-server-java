package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsImpExt {

    public static final String DEFAULT_CONFIG_KEY = "default";
    public static final BigDecimal DEFAULT_BID_FLOOR_PRICE = BigDecimal.valueOf(0.0);
    public static final String DEFAULT_BID_FLOOR_CUR = "USD";
    private static final Floor DEFAULT_BID_FLOOR = Floor.of(DEFAULT_BID_FLOOR_PRICE, DEFAULT_BID_FLOOR_CUR);

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("floors")
    Map<String, Floor> floors = Map.of(DEFAULT_CONFIG_KEY, DEFAULT_BID_FLOOR);

    @JsonProperty("gam")
    ImprovedigitalPbsImpExtGam improvedigitalPbsImpExtGam;

    Map<String, List<String>> waterfall = Map.of(DEFAULT_CONFIG_KEY, List.of("gam"));

    private String resolveCountryCode(Map<String, ?> map, GeoInfo geoInfo) {
        return ObjectUtil.getIfNotNullOrDefault(geoInfo,
                gn -> map.containsKey(gn.getCountry())
                        ? gn.getCountry()
                        : DEFAULT_CONFIG_KEY,
                () -> DEFAULT_CONFIG_KEY
        );
    }

    public Floor getFloor(GeoInfo geoInfo) {
        final Map<String, Floor> floors = this.getFloors();
        if (floors.isEmpty()) {
            return DEFAULT_BID_FLOOR;
        }
        final String countryCode = resolveCountryCode(floors, geoInfo);

        if (floors.containsKey(countryCode)) {
            return floors.get(countryCode);
        }

        return DEFAULT_BID_FLOOR;
    }

    public List<String> getWaterfall(GeoInfo geoInfo) {
        final Map<String, List<String>> waterfall = this.getWaterfall();
        final List<String> defaultResult = List.of("gam");
        if (waterfall.isEmpty()) {
            return defaultResult;
        }
        final String countryCode = resolveCountryCode(waterfall, geoInfo);

        if (waterfall.containsKey(countryCode)) {
            return waterfall.get(countryCode);
        }
        return defaultResult;
    }
}
