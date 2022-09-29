package com.improvedigital.prebid.server.utils;

import org.junit.Ignore;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Ignore
public class TestUtils {

    private TestUtils() {
    }

    public static Map<String, List<String>> splitQuery(String queryParam) {
        return Arrays.stream(queryParam.split("&"))
                .map(TestUtils::splitQueryParameter)
                .collect(Collectors.groupingBy(
                        AbstractMap.SimpleImmutableEntry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
                );
    }

    protected static AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        try {
            final String[] idx = it.split("=");
            return new AbstractMap.SimpleImmutableEntry<>(
                    URLDecoder.decode(idx[0], "UTF-8"),
                    URLDecoder.decode(idx.length > 1 ? idx[1] : "", "UTF-8")
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
