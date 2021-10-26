package com.azerion.prebid.utils;

import java.util.HashMap;
import java.util.Map;

public class FluentMap<K, V> {

    private final Map<K, V> map;

    private FluentMap(Map<K, V> map) {
        this.map = map;
    }

    public static <A, B> FluentMap<A, B> create() {
        return new FluentMap<>(new HashMap<A, B>());
    }

    public static <A, B> FluentMap<A, B> from(Map<A, B> map) {
        return new FluentMap<>(map);
    }

    public FluentMap<K, V> put(K key, V value) {
        map.put(key, value);
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

    public Map<K, V> result() {
        return map;
    }
}
