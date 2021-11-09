package com.azerion.prebid.exception;

public class SettingsLoaderException extends RuntimeException {

    public SettingsLoaderException(String message) {
        super(message);
    }

    public SettingsLoaderException(String message, Throwable cause) {
        super(message, cause);
    }

    public SettingsLoaderException(Throwable cause) {
        super(cause);
    }
}
