package com.improvedigital.prebid.server.hooks.v1.customvast;

public class CustomVastHooksException extends Exception {

    public CustomVastHooksException() {
        super();
    }

    public CustomVastHooksException(String message) {
        super(message);
    }

    public CustomVastHooksException(String message, Throwable cause) {
        super(message, cause);
    }

    public CustomVastHooksException(Throwable cause) {
        super(cause);
    }

    public CustomVastHooksException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
