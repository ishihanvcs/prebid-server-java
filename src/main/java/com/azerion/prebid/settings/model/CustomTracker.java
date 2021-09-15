package com.azerion.prebid.settings.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CustomTracker {

    String id;
    String baseUrl;
    @Builder.Default
    String urlResolver = null;
    @Builder.Default
    String injector = null;
    @Builder.Default
    String currency = "USD";
}
