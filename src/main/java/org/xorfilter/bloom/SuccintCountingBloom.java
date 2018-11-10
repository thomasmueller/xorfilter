package org.xorfilter.bloom;

import org.xorfilter.Filter;
import org.xorfilter.utils.Hash;

/**
 * A succinct counting Bloom filter.
 * It uses almost half the space of a regular counting Bloom filter,
 * and lookup is faster (exactly as fast as with a regular Bloom filter).
 *
 * (WORK IN PROGRESS)
 * Disadvantage: currently, construction is very slow.
 * Once optimized, it will likely be a few times slower
 * than a regular counting Bloom filter.
 */
public class SuccintCountingBloom implements Filter {

    public static SuccintCountingBloom construct(long[] keys, double bitsPerKey) {
        long n = keys.length;
        long m = (long) (n * bitsPerKey);
        int k = getBestK(m, n);
        SuccintCountingBloom f = new SuccintCountingBloom((int) n, bitsPerKey, k);
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

    // the "data bits" exactly as in a regular Bloom filter
    private final long[] data;

    // the counter bits
    // the same size as the "data bits"
    private final long[] counts;

    // a bit set that contains one bit for each 16 "data bits"
    // whether there are any overflow from previous entries
    // (it turns out, this is about 50% of the cases)
    // once optimized, probably size can be reduced
    private final long[] levels;

    // each byte contains the real count for a "data bit"
    // (this is just used for verification)
    private final byte[] realCounts;

    public long getBitCount() {
        return data.length * 64L + counts.length * 64L + levels.length * 64L;
    }

    SuccintCountingBloom(int entryCount, double bitsPerKey, int k) {
        entryCount = Math.max(1, entryCount);
        this.k = k;
        this.seed = Hash.randomSeed();
        this.bits = (long) (entryCount * bitsPerKey);
        arraySize = (int) ((bits + 63) / 64);
        data = new long[arraySize + 1];
        counts = new long[arraySize + 1];
        levels = new long[(arraySize + 63) / 16];
        realCounts = new byte[arraySize * 64];
    }

    private void remove(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            decrement(a);
            a += b;
        }
    }

    private void add(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            increment(a);
            a += b;
        }
    }

/*

10000
00000 > 1

10000
10000 > 2

11000
10000 > 2, 1

11000
11000 > 2, 2

10000
11000 > 3

11000
10100 > 3, 1

11000
11010 > 3, 2

*/

    private void increment(int x) {
        int index = Hash.reduce(x, arraySize) * 64 + (x & 63);
        int from = index;
        realCounts[index]++;
        int level = getLevel(index);
        if ((data[index / 64] & getBit(index)) == 0) {
            data[index / 64] |= getBit(index);
            level++;
            while (level > 0) {
                index = insert(index, 0);
                index = getEnd(index);
                level--;
            }
        } else {
            index = insert(index, 1);
            while (level > 0) {
                index = getEnd(index);
                index = insert(index, 0);
                level--;
            }
        }
        updateLevels(from, index);
//        for(int i=0; i<realCounts.length; i++) {
//            if (realCounts[i] != readCount(i)) {
//                System.out.println("real: " + realCounts[i]);
//                System.out.println("read: " + readCount(i));
//                readCount(i);
//            }
//        }
    }

    private void decrement(int x) {
        int index = Hash.reduce(x, arraySize) * 64 + (x & 63);
        int from = index;
        realCounts[index]--;
        int level = getLevel(index);
        if ((data[index / 64] & getBit(index)) == 0) {
            throw new IllegalStateException();
        }
        if ((counts[index / 64] & getBit(index)) == 0) {
            data[index / 64] &= ~getBit(index);
        }
        while (level > 0) {
            index = remove(index);
            level--;
        }
        updateLevels(from, index);
        for(int i=0; i<realCounts.length; i++) {
            if (realCounts[i] != readCount(i)) {
                System.out.println("real: " + realCounts[i]);
                System.out.println("read: " + readCount(i));
                readCount(i);
            }
        }
    }

    private int remove(int index) {
        // TODO
        return index;
    }

    private int readCount(int index) {
        int result = 0;
        if ((data[index / 64] & getBit(index)) == 0) {
            return result;
        }
        result++;
        while (true) {
            if ((counts[index / 64] & getBit(index)) == 0) {
                return result;
            }
            result++;
            index = getEnd(index + 1);
        }
    }

    /**
     * Get the index of the next bit for this run,
     * skipping over interleaved runs.
     *
     * @param index
     * @return the end index
     */
    private int getEnd(int index) {
        int level = 1;
        while (true) {
            if ((data[index / 64] & getBit(index)) == 0) {
                level--;
                if (level == 0) {
                    return index;
                }
            }
            if ((counts[index / 64] & getBit(index)) != 0) {
                level++;
            }
            index++;
        }
    }

    public String toString() {
        getLevel(realCounts.length - 1);
//        if (this.realCounts.length > 200) {
//            return "";
//        }
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < realCounts.length; i++) {
            if ((data[i / 64] & getBit(i)) == 0) {
                buff.append('0');
            } else {
                buff.append('1');
            }
        }
        buff.append("  data\n");
        for (int i = 0; i < realCounts.length; i++) {
            if ((counts[i / 64] & getBit(i)) == 0) {
                buff.append('0');
            } else {
                buff.append('1');
            }
        }
        buff.append("  counts\n");
        for (int i = 0; i < realCounts.length; i++) {
            buff.append((char) ('0' + (realCounts[i] % 10)));
        }
        buff.append("  realCounts\n");
        for (int i = 0; i < realCounts.length; i++) {
            buff.append((char) ('0' + (i % 10)));
        }
        buff.append("  index\n");
        for (int i = 0; i < realCounts.length; i++) {
            if (i % 10 == 0 && i > 0) {
                buff.append(i / 10);
            } else {
                buff.append('0');
            }
        }
        buff.append("  index\n");
        for (int i = 0; i < realCounts.length; i++) {
            buff.append(getLevel(i));
        }
        buff.append("  level\n");
        return buff.toString();
    }

    private int insert(int index, int x) {
        if ((counts[index / 64] & getBit(index)) == 0) {
            // current count bit is 0
            if (x == 1) {
                // current count bit is 0, inserting 1
                counts[index / 64] |= getBit(index);
            } else {
                // insert 0
                return index + 1;
            }
            index = getEnd(index + 1);
            return insert(index, 0);
        }
        // current count bit is 1
        if (x == 0) {
            // inserting 0
            counts[index / 64] ^= getBit(index);
        }
        do {
            index = getEnd(index + 1);
            // if data bit is 1: skip over
        } while ((data[index / 64] & getBit(index)) != 0);
        return insert(index, 1);
    }

    void updateLevels(int from, int to) {
//        if(true)return;
        from = from / 16 * 16;
        to = to / 16 * 16;
        while ((levels[from / 16 / 64] & getBit(from / 16)) != 0) {
            from -= 16;
        }
        int level = 0;
        for (int i = from; i <= to;) {
            if ((data[i / 64] & getBit(i)) == 0) {
                if (level > 0) {
                    level--;
                }
            }
            if ((counts[i / 64] & getBit(i)) != 0) {
                level++;
            }
//            int exp = getLevel(i);
//            if (level != exp) {
//                throw new AssertionError();
//            }
            i++;
            if ((i & 15) == 0) {
                if (level == 0) {
                    levels[i / 16 / 64] &= ~getBit(i / 16);
                } else {
                    levels[i / 16 / 64] |= getBit(i / 16);
                }
            }
        }
    }

    int getLevel(int index) {
        int level = 0;
        boolean log = false;
        if (index > realCounts.length / 2) {
            long now = System.currentTimeMillis();
            if(now > lastLogLevel + 5000) {
                lastLogLevel = now;
                log = true;
            }
        }
        int i = index / 16 * 16;
        while (true) {
            boolean levelBit = (levels[i / 16 / 64] & getBit(i / 16)) != 0;
            if (levelBit) {
                i -= 16;
            } else {
                break;
            }
        }
//if (index - i > 100)
//System.out.println(index - i);
        for (; i <= index;) {
            if ((data[i / 64] & getBit(i)) == 0) {
                if (level > 0) {
                    level--;
                }
            }
            if ((counts[i / 64] & getBit(i)) != 0) {
                level++;
            }
            i++;
//            if ((i & 63) == 0) {
//                boolean levelBit = (level0[i / 64 / 64] & getBit(i / 64)) != 0;
//                if (levelBit != (level != 0)) {
//                    updateLevels(0, realCounts.length);
//                    System.out.println(" before " + levelBit + " now " +
//                            ((level0[i / 64 / 64] & getBit(i / 64)) != 0));
//                    levelBit ^= levelBit;
////                    throw new AssertionError();
//                }
//            }
//            if (log && (i & 63) == 0) {
//                System.out.print((char) ('0' + level));
//            }
//            if (log && level != 0) {
//                System.out.print((char) ('0' + level));
//            }
        }
//        if(log) {
//            System.out.println(" << level bits");
//        }
        return level;
    }

    private long lastLogLevel;

    private static long getBit(int index) {
        return 1L << index;
    }

    @Override
    public boolean mayContain(long key) {
        long hash = Hash.hash64(key, seed);
        int a = (int) (hash >>> 32);
        int b = (int) hash;
        for (int i = 0; i < k; i++) {
            if ((data[Hash.reduce(a, arraySize)] & getBit(a)) == 0) {
                return false;
            }
            a += b;
        }
        return true;
    }

}
