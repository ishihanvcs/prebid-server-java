package com.improvedigital.prebid.server.utils;

import java.util.function.Function;

public class Nullable<V> {

    final V value;

    private Nullable(V value) {
        this.value = value;
    }

    public static <T> Nullable<T> of(T source) {
        return new Nullable<>(source);
    }

    public V value() {
        return value;
    }

    public V value(V defaultValue) {
        return value == null ? defaultValue : value;
    }

    public <P> Nullable<P> get(Function<V, P> getter) {
        return new Nullable<>(value != null ? getter.apply(value) : null);
    }

    public boolean isNull() {
        return value == null;
    }
}
