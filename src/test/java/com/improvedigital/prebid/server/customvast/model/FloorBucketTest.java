package com.improvedigital.prebid.server.customvast.model;

import org.junit.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class FloorBucketTest extends VertxTest {

    @Test
    public void testResolveGamFloorWithBucket1() {
        final FloorBucket bucket = FloorBucket.get(0);
        double bidFloor = 0.023;
        String value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.02");

        bidFloor = 0.028;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.03");

        bidFloor = 0.091;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.09");

        bidFloor = 0.09999;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.1");

        bidFloor = 0.1;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.1");

        bidFloor = 1.126;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("1.13");
    }

    @Test
    public void testResolveGamFloorWithBucket2() {
        final FloorBucket bucket = FloorBucket.get(1);
        double bidFloor = 0.23;
        String value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.25");

        bidFloor = 0.27;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.25");

        bidFloor = 0.29;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.3");
    }

    @Test
    public void testResolveGamFloorWithBucket3() {
        final FloorBucket bucket = FloorBucket.get(2);
        double bidFloor = 0.29;
        String value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("0.29");

        bidFloor = 3.0;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("3");

        bidFloor = 3.04;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("3");

        bidFloor = 3.05;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("3");

        bidFloor = 3.06;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("3.1");

        bidFloor = 9.99;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("10");

        bidFloor = 10.24;
        value = bucket.resolveGamFloor(bidFloor);
        assertThat(value).isEqualTo("10.24");
    }

    @Test
    public void testResolveGamFloorPrice() {
        String result = FloorBucket.resolveGamFloorPrice(-10.1);
        assertThat(result).isEqualTo("-10.1");

        result = FloorBucket.resolveGamFloorPrice(0.01);
        assertThat(result).isEqualTo("0.01");

        result = FloorBucket.resolveGamFloorPrice(0.012);
        assertThat(result).isEqualTo("0.01");

        result = FloorBucket.resolveGamFloorPrice(0.016);
        assertThat(result).isEqualTo("0.02");

        result = FloorBucket.resolveGamFloorPrice(0.092);
        assertThat(result).isEqualTo("0.09");

        result = FloorBucket.resolveGamFloorPrice(0.099);
        assertThat(result).isEqualTo("0.1");

        result = FloorBucket.resolveGamFloorPrice(0.1);
        assertThat(result).isEqualTo("0.1");

        result = FloorBucket.resolveGamFloorPrice(0.12);
        assertThat(result).isEqualTo("0.1");

        result = FloorBucket.resolveGamFloorPrice(0.13);
        assertThat(result).isEqualTo("0.15");

        result = FloorBucket.resolveGamFloorPrice(0.15);
        assertThat(result).isEqualTo("0.15");

        result = FloorBucket.resolveGamFloorPrice(0.1634);
        assertThat(result).isEqualTo("0.15");

        result = FloorBucket.resolveGamFloorPrice(2.96);
        assertThat(result).isEqualTo("2.95");

        result = FloorBucket.resolveGamFloorPrice(2.991);
        assertThat(result).isEqualTo("3");

        result = FloorBucket.resolveGamFloorPrice(3.00);
        assertThat(result).isEqualTo("3");

        result = FloorBucket.resolveGamFloorPrice(3.09);
        assertThat(result).isEqualTo("3.1");

        result = FloorBucket.resolveGamFloorPrice(9.99);
        assertThat(result).isEqualTo("10");

        result = FloorBucket.resolveGamFloorPrice(11.991);
        assertThat(result).isEqualTo("11.99");

        result = FloorBucket.resolveGamFloorPrice(11.996);
        assertThat(result).isEqualTo("12");
    }
}
