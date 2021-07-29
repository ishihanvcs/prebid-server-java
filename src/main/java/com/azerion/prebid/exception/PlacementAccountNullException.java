package com.azerion.prebid.exception;

import lombok.Getter;

public class PlacementAccountNullException extends RuntimeException {

    @Getter
    private final String placementId;

    public PlacementAccountNullException(String message, String placementId) {
        super(message);
        this.placementId = placementId;
    }
}
