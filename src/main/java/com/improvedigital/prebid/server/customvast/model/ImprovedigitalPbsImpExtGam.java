package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsImpExtGam {

    public static final ImprovedigitalPbsImpExtGam DEFAULT = ImprovedigitalPbsImpExtGam.of(null, null, null);

    @JsonProperty("adUnit")
    String adUnit;

    @JsonProperty("networkCode")
    String networkCode;

    @JsonProperty("childNetworkCode")
    String childNetworkCode;
}
