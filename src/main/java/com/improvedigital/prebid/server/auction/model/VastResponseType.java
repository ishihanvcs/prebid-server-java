package com.improvedigital.prebid.server.auction.model;

public enum VastResponseType {

    vast,
    gvast,
    waterfall;

    private final String name;

    VastResponseType(String name) {
        this.name = name;
    }

    VastResponseType() {
        this.name = name();
    }

    @Override
    public String toString() {
        return name;
    }
}
