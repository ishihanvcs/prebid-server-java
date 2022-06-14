package com.improvedigital.prebid.server.hooks.v1.gvast;

public class GVastHookException extends Exception {

    public GVastHookException() {
        super();
    }

    public GVastHookException(String message) {
        super(message);
    }

    public GVastHookException(String message, Throwable cause) {
        super(message, cause);
    }

    public GVastHookException(Throwable cause) {
        super(cause);
    }

    public GVastHookException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
