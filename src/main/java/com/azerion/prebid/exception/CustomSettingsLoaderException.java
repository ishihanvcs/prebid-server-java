package com.azerion.prebid.exception;

public class CustomSettingsLoaderException extends RuntimeException {

    public CustomSettingsLoaderException(String message) {
        super(message);
    }

    public CustomSettingsLoaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomSettingsLoaderException(Throwable cause) {
        super(cause);
    }
}
