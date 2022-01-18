package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsImpExt {

    public static final String DEFAULT_CONFIG_KEY = "default";

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("bidFloors")
    Map<String, BidFloor> bidFloors = Map.of(DEFAULT_CONFIG_KEY, BidFloor.of(0.0));

    @JsonProperty("gamAdUnit")
    String gamAdUnit;

    Map<String, List<String>> waterfall = Map.of(DEFAULT_CONFIG_KEY, List.of("gam"));

    private String resolveCountryCode(Map<String, ?> map, GeoInfo geoInfo) {
        return ObjectUtil.getIfNotNullOrDefault(geoInfo,
                gn -> map.containsKey(gn.getCountry())
                        ? gn.getCountry()
                        : DEFAULT_CONFIG_KEY,
                () -> DEFAULT_CONFIG_KEY
        );
    }

    public double getBidFloor(GeoInfo geoInfo) {
        final double defaultResult = 0.0;
        final Map<String, BidFloor> bidFloors = this.getBidFloors();
        if (bidFloors.isEmpty()) {
            return defaultResult;
        }
        final String countryCode = resolveCountryCode(bidFloors, geoInfo);

        if (bidFloors.containsKey(countryCode)) {
            return bidFloors.get(countryCode).getBidFloor();
        }

        return defaultResult;
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
