package org.xorfilter;

import org.junit.Test;
import org.xorfilter.Filter;
import org.xorfilter.FilterType;
import org.xorfilter.utils.RandomGenerator;

public class TestAllFilters {

    public static void main(String... args) {
        testAll(1000000, true);
    }

    @Test
    public void test() {
        testAll(1000000, false);
    }

    private static void testAll(int len, boolean log) {
        for (FilterType type : FilterType.values()) {
            test(type, len, log);
        }
    }

    private static void test(FilterType type, int len, boolean log) {
        long[] list = new long[len * 2];
        RandomGenerator.createRandomUniqueListFast(list, len);
        long[] keys = new long[len];
        long[] nonKeys = new long[len];
        // first half is keys, second half is non-keys
        for (int i = 0; i < len; i++) {
            keys[i] = list[i];
            nonKeys[i] = list[i + len];
        }
        Filter f = type.construct(keys, 8);
        // each key in the set needs to be found
        for (int i = 0; i < len; i++) {
            if (!f.mayContain(keys[i])) {
                f.mayContain(keys[i]);
                throw new AssertionError();
            }
        }
        // non keys _may_ be found - this is used to calculate false
        // positives
        int falsePositives = 0;
        for (int i = 0; i < len; i++) {
            if (f.mayContain(nonKeys[i])) {
                falsePositives++;
            }
        }
        double fpp = (double) falsePositives / len;
        long bitCount = f.getBitCount();
        double bitsPerKey = (double) bitCount / len;
        if (log) {
            System.out.println(type + " fpp " + fpp + " " + bitsPerKey);
        }
    }

}
