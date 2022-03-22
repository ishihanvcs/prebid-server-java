package com.improvedigital.prebid.server.utils;

import java.util.Set;

@FunctionalInterface
public interface SetValueMergeFunction<V> {
    Set<V> merge(String key, Set<V> oldValues, Set<V> newValues);
}
