package com.azerion.prebid.exception;

import java.util.Set;

public class TokenNotFoundException extends Exception {

    public TokenNotFoundException(String template, Set<String> invalidTokens, Set<String> validTokens) {
        super(String.format("Template '%s' contains invalid token(s): '%s'!\nValid tokens are: '%s'",
                template,
                String.join(", ", invalidTokens),
                String.join(", ", validTokens)
        ));
    }
}
