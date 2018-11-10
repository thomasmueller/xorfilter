package org.xorfilter.bloom;

import org.xorfilter.Filter;
import org.xorfilter.utils.Hash;

/**
 * A standard counting Bloom filter, with 4 bits per "data bit" (entry).
 */
public class CountingBloom implements Filter {

    public static CountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        long m = (long) (n * bitsPerKey);
        int k = getBestK(m, n);
        CountingBloom f = new CountingBloom((int) n, bitsPerKey, k);
        for(long x : keys) {
            f.add(x);
        }
        return f;
    }

    private static int getBestK(long m, long n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private final int k;
    private final long bits;
    private final long seed;
    private final int arraySize;
    private final long[] counts;

    public long getBitCount() {
        return counts.length * 64L;
    }

    CountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        this.bits = (long) (4 * entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        counts = new long[arraySize];
    }

    private void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize * 16);
            counts[index / 16] += getBit(index);
            a += b;
        }
    }

    private static long getBit(int index) {
        return 1L << (index * 4);
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            int index = Hash.reduce(a, arraySize * 16);
            if ((counts[index / 16] & (0xf * getBit(index))) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

}
