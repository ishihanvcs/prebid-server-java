package com.azerion.prebid.auction.model;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

public class CustParams extends HashedMap<String, String[]> {

    private void setDefaults() {
        // tnl_asset_id KV is used in HeaderLift reporting
        if (!this.containsKey("tnl_asset_id")) {
            this.put("tnl_asset_id", new String[] {"prebidserver"});
        }
    }

    public CustParams() {
        super();
        setDefaults();
    }

    public CustParams(int initialCapacity) {
        super(initialCapacity);
        setDefaults();
    }

    public CustParams(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        setDefaults();
    }

    public CustParams(Map<? extends String, ? extends String[]> map) {
        super(map);
        setDefaults();
    }

    public CustParams(String paramString) {
        this();
        if (!StringUtils.isBlank(paramString)) {
            for (String kv : paramString.split("&")) {
                String[] pair = kv.split("=");
                if (pair.length != 2) {
                    continue;
                }
                this.put(pair[0], pair[1].split(","));
            }
        }
    }

    @Override
    public String toString() {
        return entrySet().stream()
            .sorted((o1, o2) -> o1.getKey().compareToIgnoreCase(o2.getKey()))
            .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this.toString().equals(obj.toString());
    }
}
