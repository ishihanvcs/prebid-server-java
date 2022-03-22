package com.improvedigital.prebid.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FluentMap<K, V> {

    private static final String ENCODED_COMMA = HttpUtil.encodeUrl(",");

    private final Map<K, V> map;

    private FluentMap(Map<K, V> map) {
        this.map = map;
    }

    public static <A, B> FluentMap<A, B> create() {
        return new FluentMap<>(new HashMap<>());
    }

    public static <A, B> FluentMap<A, B> from(Map<A, B> map) {
        return new FluentMap<>(map);
    }

    public static FluentMap<String, Set<String>> fromQueryString(String queryString) {
        return FluentMap.fromQueryString(queryString, new HashMap<>());
    }

    public static FluentMap<String, Set<String>> fromQueryString(String queryString, Map<String, Set<String>> map) {
        return fromQueryString(queryString, map, (key, oldValues, newValues) -> concatValues(oldValues, newValues));
    }

    public static FluentMap<String, Set<String>> fromQueryString(String queryString, Map<String, Set<String>> map, SetValueMergeFunction<String> valueMerger) {
        if (StringUtils.isBlank(queryString)) {
            return FluentMap.from(map);
        }
        for (String kv : queryString.split("&")) {
            String[] pair = kv.split("=");
            if (pair.length != 2) {
                continue;
            }
            final String name = decodeAndStrip(pair[0]);
            final String value = decodeAndStrip(pair[1]);

            if (StringUtils.isBlank(name) || StringUtils.isBlank(value)) {
                continue;
            }

            final Set<String> oldValueSet = map.getOrDefault(name, new HashSet<>());

            final Set<String> newValueSet =
                    Arrays.stream(value.split(","))
                            .map(String::strip)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.toSet());

            if (!newValueSet.isEmpty()) {
                Set<String> valueToPut = valueMerger.merge(name, oldValueSet, newValueSet);
                if (valueToPut != null) {
                    map.put(name, valueToPut);
                }
            }
        }
        return FluentMap.from(map);
    }

    public static Set<String> concatValues(Set<String> values1, Set<String> values2) {
        return Stream.concat(
                values1 == null ? Stream.empty() : values1.stream(),
                values2 == null ? Stream.empty() : values2.stream()
        ).collect(Collectors.toSet());
    }

    private static String decodeAndStrip(String value) {
        if (StringUtils.isNotEmpty(value)) {
            value = HttpUtil.decodeUrl(value.strip());
            if (StringUtils.isNotEmpty(value)) {
                return value.strip();
            }
        }
        return value;
    }

    public FluentMap<K, V> put(K key, V value) {
        map.put(key, value);
        return this;
    }

    public FluentMap<K, V> putAll(Map<? extends K, ? extends V> source) {
        map.putAll(source);
        return this;
    }

    public FluentMap<K, V> remove(K key) {
        map.remove(key);
        return this;
    }

    public FluentMap<K, V> replace(K key, V value) {
        map.replace(key, value);
        return this;
    }

    public FluentMap<K, V> clear() {
        map.clear();
        return this;
    }

    public Map<K, V> result() {
        return map;
    }

    public String queryString() {
        return this.map.entrySet().stream()
                .sorted((o1, o2) -> StringUtils.compareIgnoreCase(
                        String.valueOf(o1.getKey()), String.valueOf(o2.getKey())
                ))
                .map(entry -> {
                    final String encodedValue;
                    final Object value = entry.getValue();
                    if (value instanceof Map<?, ?>) {
                        encodedValue = FluentMap.from((Map<?, ?>) value).queryString();
                    } else if (value.getClass().isArray()) {
                        encodedValue = encodeStream(Arrays.stream((Object[]) value));
                    } else if (value instanceof Iterable<?>) {
                        encodedValue = encodeStream(StreamSupport.stream(((Iterable<?>) value).spliterator(), false));
                    } else {
                        encodedValue = encodeStream(Stream.of(value));
                    }
                    if (StringUtils.isBlank(encodedValue)) {
                        return null;
                    }
                    return entry.getKey() + "=" + encodedValue;
                })
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("&"));
    }

    private static String encodeStream(Stream<?> stream) {
        return stream.map(String::valueOf)
                .filter(StringUtils::isNotBlank)
                .map(HttpUtil::encodeUrl)
                .collect(Collectors.joining(ENCODED_COMMA));
    }
}
