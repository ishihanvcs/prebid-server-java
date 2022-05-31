package com.improvedigital.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Geo;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class ImprovedigitalPbsImpExt {

    public static final String DEFAULT_CONFIG_KEY = "default";
    public static final BigDecimal DEFAULT_BID_FLOOR_PRICE = BigDecimal.valueOf(0.0);
    public static final String DEFAULT_BID_FLOOR_CUR = "USD";
    private static final Floor DEFAULT_BID_FLOOR = Floor.of(DEFAULT_BID_FLOOR_PRICE, DEFAULT_BID_FLOOR_CUR);
    private static final List<String> DEFAULT_WATERFALL = List.of("gam");

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("requestId")
    String requestId;

    @JsonProperty("floors")
    Map<String, Floor> floors;

    @JsonProperty("gam")
    ImprovedigitalPbsImpExtGam improvedigitalPbsImpExtGam;

    @JsonProperty("responseType")
    VastResponseType responseType;

    Map<String, List<String>> waterfall;

    public VastResponseType getResponseType() {
        return responseType == null ? VastResponseType.vast : responseType;
    }

    private String resolveCountryCode(Map<String, ?> map, Geo geo) {
        return ObjectUtil.getIfNotNullOrDefault(geo,
                gn -> map.containsKey(gn.getCountry())
                        ? gn.getCountry()
                        : DEFAULT_CONFIG_KEY,
                () -> DEFAULT_CONFIG_KEY
        );
    }

    public Map<String, Floor> getFloors() {
        return floors == null ? Map.of(DEFAULT_CONFIG_KEY, DEFAULT_BID_FLOOR) : floors;
    }

    public Floor getFloor(Geo geo) {
        final Map<String, Floor> floors = this.getFloors();
        if (floors.isEmpty()) {
            return DEFAULT_BID_FLOOR;
        }
        final String countryCode = resolveCountryCode(floors, geo);

        if (floors.containsKey(countryCode)) {
            return floors.get(countryCode);
        }

        return DEFAULT_BID_FLOOR;
    }

    public Map<String, List<String>> getWaterfall() {
        return waterfall == null ? Map.of(DEFAULT_CONFIG_KEY, DEFAULT_WATERFALL) : waterfall;
    }

    public List<String> getWaterfall(Geo geo) {
        final Map<String, List<String>> waterfall = this.getWaterfall();
        if (waterfall.isEmpty()) {
            return DEFAULT_WATERFALL;
        }
        final String countryCode = resolveCountryCode(waterfall, geo);

        if (waterfall.containsKey(countryCode)) {
            return waterfall.get(countryCode);
        }
        return DEFAULT_WATERFALL;
    }
}
