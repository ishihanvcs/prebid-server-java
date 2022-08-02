package com.improvedigital.prebid.server.customvast.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ImprovedigitalPbsImpExtGam {

    @JsonProperty("adUnit")
    String adUnit;

    @JsonProperty("networkCode")
    String networkCode;

    @JsonProperty("childNetworkCode")
    String childNetworkCode;
}
