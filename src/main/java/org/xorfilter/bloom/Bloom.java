package org.xorfilter.bloom;

import org.xorfilter.Filter;
import org.xorfilter.utils.Hash;

/**
 * A standard bloom filter.
 *
 */
public class Bloom implements Filter {

    public static Bloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        long m = (long) (n * bitsPerKey);
        int k = getBestK(m, n);
        Bloom f = new Bloom((int) n, bitsPerKey, k);
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
    private final long[] data;

    public long getBitCount() {
        return data.length * 64L;
    }

    Bloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        this.bits = (long) (entryCount * bitsPerKey);
        data = new long[(int) ((bits + 63) / 64)];
    }

    void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        final int arraysize = data.length;
        for (int i = 0; i < k; i++) {
            // reworked to avoid overflows
            // use the fact that reduce is not very sensitive to lower bits of a
            data[Hash.reduce(a, arraysize)] |= getBit(a);
            a += b;
        }
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        final int arraysize = data.length;
        for (int i = 0; i < k; i++) {
            // reworked to avoid overflows
            if ((data[Hash.reduce(a, arraysize)] & getBit(a)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

    private static long getBit(int index) {
        return 1L << index;
    }

}
