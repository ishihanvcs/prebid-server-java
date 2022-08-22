package com.improvedigital.prebid.server.customvast.model;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Value(staticConstructor = "of")
public class FloorBucket {

    // Following buckets have been defined using the rules found in
    // https://admanager.google.com/1015413#inventory/pricing/list
    private static final List<FloorBucket> BUCKETS = List.of(
            FloorBucket.of(0.0, 0.1, 0.01),
            FloorBucket.of(0.1, 3.0, 0.05),
            FloorBucket.of(3.0, 10.0, 0.1)
    );

    private static final FloorBucket DEFAULT_BUCKET = FloorBucket.of(
            0.0, 0.0, 1.0
    );

    double lowerBoundary;
    double upperBoundary;
    double valueInterval;

    public String resolveGamFloor(double bidFloor) {
        if (valueFitsWithinBucket(bidFloor)) {
            double currentValue = lowerBoundary;
            while (currentValue < upperBoundary) {
                double nextValue = currentValue + valueInterval;
                if (valueFitsWithin(bidFloor, currentValue, nextValue)) {
                    bidFloor = (bidFloor - currentValue) < (nextValue - bidFloor) ? currentValue : nextValue;
                    break;
                }
                currentValue = nextValue;
            }
        }
        return doubleToString(bidFloor);
    }

    private boolean valueFitsWithinBucket(double bidFloor) {
        return valueFitsWithin(bidFloor, lowerBoundary, upperBoundary);
    }

    public static FloorBucket get(int index) {
        return BUCKETS.get(index);
    }

    private static FloorBucket findMatchingBucket(double bidFloor) {
        return BUCKETS.parallelStream()
                .filter(bucket -> bucket.valueFitsWithinBucket(bidFloor))
                .findFirst()
                .orElse(DEFAULT_BUCKET);
    }

    public static String resolveGamFloorPrice(double bidFloor) {
        return FloorBucket
                .findMatchingBucket(bidFloor)
                .resolveGamFloor(bidFloor);
    }

    private static String doubleToString(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_EVEN)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static boolean valueFitsWithin(double value, double num1, double num2) {
        double[] range = num1 < num2 ? new double[] {num1, num2} : new double[] {num2, num1};
        return value >= range[0] && value < range[1];
    }
}
