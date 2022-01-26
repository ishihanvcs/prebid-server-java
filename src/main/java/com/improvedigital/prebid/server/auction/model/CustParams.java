package com.improvedigital.prebid.server.auction.model;

import com.improvedigital.prebid.server.utils.FluentMap;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CustParams extends HashedMap<String, Set<String>> {

    private static final String DEFAULT_TNL_ASSET_ID = "prebidserver";

    @Override
    protected void init() {
        // tnl_asset_id KV is used in HeaderLift reporting
        if (!this.containsKey("tnl_asset_id")) {
            this.put("tnl_asset_id", Arrays.stream(
                    new String[]{ DEFAULT_TNL_ASSET_ID })
                    .collect(Collectors.toSet())
            );
        }
    }

    public CustParams() {
        super();
    }

    public CustParams(int initialCapacity) {
        super(initialCapacity);
    }

    public CustParams(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public CustParams(Map<? extends String, ? extends Set<String>> map) {
        super(map);
    }

    public CustParams(String paramString) {
        this();
        if (!StringUtils.isBlank(paramString)) {
            FluentMap.fromQueryString(paramString, this);
        }
    }

    @Override
    public String toString() {
        return FluentMap.from(this).queryString();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CustParams) {
            return this.toString().equals(obj.toString());
        }
        return false;
    }
}
