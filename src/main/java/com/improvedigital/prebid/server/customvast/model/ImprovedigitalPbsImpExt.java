package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class ImprovedigitalPbsImpExt {

    public static final String DEFAULT_CONFIG_KEY = "default";
    public static final BigDecimal DEFAULT_BID_FLOOR_PRICE = BigDecimal.ZERO;
    public static final String DEFAULT_BID_FLOOR_CUR = "USD";
    public static final Floor DEFAULT_BID_FLOOR = Floor.of(DEFAULT_BID_FLOOR_PRICE, DEFAULT_BID_FLOOR_CUR);
    public static final List<String> DEFAULT_WATERFALL = List.of();

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

    @JsonProperty("waterfall")
    Map<String, List<String>> waterfalls;

    @JsonProperty("schainNodes")
    List<String> schainNodes;

    @JsonProperty("headerliftPartnerId")
    String headerliftPartnerId;

    private String resolveMapKeyForCountry(Map<String, ?> map, String alpha3Country) {
        return ObjectUtil.getIfNotNullOrDefault(alpha3Country,
                countryCode -> StringUtils.isNotBlank(countryCode) && map.containsKey(countryCode)
                        ? countryCode
                        : DEFAULT_CONFIG_KEY,
                () -> DEFAULT_CONFIG_KEY
        );
    }

    public VastResponseType responseTypeOrDefault() {
        return responseType == null ? VastResponseType.vast : responseType;
    }

    private Map<String, Floor> defaultFloorsIfNull() {
        return floors == null ? Map.of() : floors;
    }

    private Map<String, List<String>> defaultWaterfallsIfNull() {
        return waterfalls == null ? Map.of() : waterfalls;
    }

    public Floor getFloor(String alpha3Country) {
        return getFloor(alpha3Country, DEFAULT_BID_FLOOR);
    }

    public Floor getFloor(String alpha3Country, Floor defaultFloor) {
        final Map<String, Floor> floors = this.defaultFloorsIfNull();
        if (floors.isEmpty()) {
            return defaultFloor;
        }
        final String mapKey = resolveMapKeyForCountry(floors, alpha3Country);
        Floor floor = floors.get(mapKey);
        return ObjectUtils.defaultIfNull(floor, defaultFloor);
    }

    public List<String> getWaterfall(String alpha3Country) {
        return getWaterfall(alpha3Country, DEFAULT_WATERFALL);
    }

    public List<String> getWaterfall(String alpha3Country, List<String> defaultWaterfall) {
        final Map<String, List<String>> waterfalls = this.defaultWaterfallsIfNull();
        if (waterfalls.isEmpty()) {
            return defaultWaterfall;
        }
        final String mapKey = resolveMapKeyForCountry(waterfalls, alpha3Country);
        List<String> waterfall = waterfalls.get(mapKey);
        return ObjectUtils.defaultIfNull(waterfall, defaultWaterfall);
    }
}
