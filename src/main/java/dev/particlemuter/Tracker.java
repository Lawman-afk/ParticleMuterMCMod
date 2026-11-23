package dev.particlemuter;

import java.util.Arrays;

/**
 * How many of each particle are being made: a ring of one-second counts per catalog entry, the newest second filling
 * as particles are made, moved on once a second by the client tick. All on the client's thread; plain ints, nothing
 * allocated as particles are counted.
 */
public final class Tracker {
    /** Seconds the rate is averaged over. */
    public static final int SECONDS = 5;
    /** Ticks in a second. */
    private static final int TICKS = 20;

    private static int[][] buckets = new int[SECONDS + 1][0];
    /** When each entry was last made, in seconds since the game started (0: never). */
    private static int[] lastSeen = new int[0];
    private static int head, ticks, clock = 1;

    private Tracker() {}

    static void resize(int size) {
        buckets = new int[SECONDS + 1][size];
        lastSeen = new int[size];
        head = 0;
    }

    /** One more of that entry (an index from the catalog; -1, before the catalog, is ignored). */
    public static void count(int index) {
        int[] now = buckets[head];
        if (index >= 0 && index < now.length) {
            now[index]++;
            lastSeen[index] = clock;
        }
    }

    /** Called every client tick: once a second, the oldest second is emptied to become the newest. */
    public static void tick() {
        if (++ticks < TICKS) {
            return;
        }
        ticks = 0;
        clock++;
        head = (head + 1) % buckets.length;
        Arrays.fill(buckets[head], 0);
    }

    /** That entry's particles a second, over the last {@link #SECONDS} whole seconds. */
    public static float rate(int index) {
        if (index < 0 || index >= lastSeen.length) {
            return 0;
        }
        int sum = 0;
        for (int b = 0; b < buckets.length; b++) {
            if (b != head) {
                sum += buckets[b][index];
            }
        }
        return sum / (float) SECONDS;
    }

    /** That entry's count so far in the second now filling. */
    public static int current(int index) {
        return index >= 0 && index < buckets[head].length ? buckets[head][index] : 0;
    }

    /** Whether that entry was made since the game started. */
    public static boolean seen(int index) {
        return index >= 0 && index < lastSeen.length && lastSeen[index] != 0;
    }

    /** How many seconds ago that entry was last made (a large number if never). */
    public static int secondsSince(int index) {
        return seen(index) ? clock - lastSeen[index] : Integer.MAX_VALUE;
    }
}
