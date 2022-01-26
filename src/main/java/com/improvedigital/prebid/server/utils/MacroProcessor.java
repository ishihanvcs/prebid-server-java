package com.improvedigital.prebid.server.utils;

import com.improvedigital.prebid.server.exception.TokenNotFoundException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MacroProcessor.class);
    private final Pattern pattern;

    public MacroProcessor() {
        this(false);
    }

    public MacroProcessor(boolean ignoreSpaces) {
        this("{{", "}}", ignoreSpaces);
    }

    public MacroProcessor(CharSequence tokenStart, CharSequence tokenEnd) {
        this(tokenStart, tokenEnd, false);
    }

    public MacroProcessor(CharSequence tokenStart, CharSequence tokenEnd, boolean ignoreSpaces) {
        final StringBuilder sb = new StringBuilder();
        sb.append(Pattern.quote(tokenStart.toString()));
        if (ignoreSpaces) {
            sb.append("\\s*");
        }
        sb.append("(\\w+)");
        if (ignoreSpaces) {
            sb.append("\\s*");
        }
        sb.append(Pattern.quote(tokenEnd.toString()));
        this.pattern = Pattern.compile(sb.toString());
    }

    public String process(String template, Map<String, String> tokenValues) throws TokenNotFoundException {
        return this.process(template, tokenValues, false);
    }

    public String process(String template, Map<String, String> tokenValues, boolean ignoreNotFoundTokens)
            throws TokenNotFoundException {
        final Matcher matcher = pattern.matcher(template);
        final Set<String> notFoundTokens = new java.util.HashSet<>();
        final String expanded = matcher.replaceAll(matchResult -> {
            final String token = matchResult.group(1);
            if (tokenValues.containsKey(token)) {
                return tokenValues.get(token);
            }
            notFoundTokens.add(token);
            return matchResult.group(0);
        });

        if (!notFoundTokens.isEmpty()) {
            TokenNotFoundException ex = new TokenNotFoundException(template, notFoundTokens, tokenValues.keySet());
            if (BooleanUtils.isTrue(ignoreNotFoundTokens)) {
                logger.warn(ex);
            } else {
                throw ex;
            }
        }
        return expanded;
    }
}
