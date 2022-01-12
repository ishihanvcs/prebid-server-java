package com.improvedigital.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CustomTracker {

    String id;
    String urlTemplate;
    @Builder.Default
    String macroResolver = null;
    @Builder.Default
    String injector = null;
}
