package com.miniredis;

import com.miniredis.core.Database;
import java.util.List;

/**
 * Unit tests for {@link Database}.
 */
public class DatabaseTest {

    public static void main(String[] args) {
        System.exit(run().exitCode());
    }

    public static TestKit run() {
        TestKit t = new TestKit("DatabaseTest");

        Database db = new Database();

        // --- a fresh database is empty ---
        t.eq("new db has size 0", 0, db.size());
        t.isNull("get on absent key is null", db.get("nope"));
        t.isTrue("exists on absent key is false", !db.exists("nope"));
        t.isTrue("keys() on empty db is empty", db.keys().isEmpty());

        // --- set then get ---
        db.set("name", "Shubh");
        t.eq("get returns the stored value", "Shubh", db.get("name"));
        t.isTrue("exists is true after set", db.exists("name"));
        t.eq("size is 1 after one set", 1, db.size());

        // --- set overwrites, does not duplicate ---
        db.set("name", "Kumar");
        t.eq("set overwrites existing value", "Kumar", db.get("name"));
        t.eq("overwrite keeps size at 1", 1, db.size());

        // --- delete ---
        t.isTrue("delete existing key returns true", db.delete("name"));
        t.isNull("get after delete is null", db.get("name"));
        t.isTrue("exists is false after delete", !db.exists("name"));
        t.isTrue("delete absent key returns false", !db.delete("name"));

        // --- keys() reflects contents and is a snapshot ---
        db.set("a", "1");
        db.set("b", "2");
        db.set("c", "3");
        List<String> keys = db.keys();
        t.eq("keys() has all three", 3, keys.size());
        t.isTrue("keys() contains a", keys.contains("a"));
        t.isTrue("keys() contains b", keys.contains("b"));
        t.isTrue("keys() contains c", keys.contains("c"));

        // mutating the returned list must not affect the store (it is a copy)
        keys.clear();
        t.eq("clearing the snapshot leaves store intact", 3, db.size());

        // --- values may contain arbitrary characters ---
        db.set("k", "a value with, punctuation!");
        t.eq("value stored verbatim", "a value with, punctuation!", db.get("k"));

        // --- INCR / DECR arithmetic (incrBy) ---
        Database n = new Database();
        t.eq("incr on missing key starts at 1", 1L, n.incrBy("hits", 1));
        t.eq("incr again -> 2", 2L, n.incrBy("hits", 1));
        t.eq("decr -> 1", 1L, n.incrBy("hits", -1));
        t.eq("decr -> 0", 0L, n.incrBy("hits", -1));
        t.eq("decr below zero -> -1", -1L, n.incrBy("hits", -1));
        t.eq("stored value is the number as text", "-1", n.get("hits"));

        // incr on a non-integer value throws
        n.set("word", "Shubh");
        boolean threwNfe;
        try {
            n.incrBy("word", 1);
            threwNfe = false;
        } catch (NumberFormatException e) {
            threwNfe = true;
        }
        t.isTrue("incr on non-integer throws NumberFormatException", threwNfe);
        t.eq("failed incr leaves value unchanged", "Shubh", n.get("word"));

        // overflow throws (Math.addExact)
        n.set("big", Long.toString(Long.MAX_VALUE));
        boolean threwOverflow;
        try {
            n.incrBy("big", 1);
            threwOverflow = false;
        } catch (ArithmeticException e) {
            threwOverflow = true;
        }
        t.isTrue("incr past Long.MAX_VALUE throws ArithmeticException", threwOverflow);

        // --- concurrent incrBy loses nothing ---
        Database shared = new Database();
        int threads = 8;
        int perThread = 25_000;
        long expected = (long) threads * perThread;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    shared.incrBy("counter", 1);
                }
            });
        }
        for (Thread w : workers) {
            w.start();
        }
        try {
            for (Thread w : workers) {
                w.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        t.eq(threads + " threads x " + perThread + " incrs = exactly " + expected,
                expected, Long.parseLong(shared.get("counter")));

        // ================= expiration, via a manual clock =================
        ManualClock clock = new ManualClock();
        Database e = new Database(clock);

        e.set("temp", "here", 10_000);                 // 10s TTL
        t.eq("value present before expiry", "here", e.get("temp"));
        t.isTrue("exists before expiry", e.exists("temp"));
        t.eq("ttl is 10000ms right after set", 10_000L, e.ttlMillis("temp"));

        clock.advance(9_000);
        t.eq("still present after 9s", "here", e.get("temp"));
        t.eq("ttl is now 1000ms", 1_000L, e.ttlMillis("temp"));

        clock.advance(1_000);                          // now exactly at the deadline
        t.isNull("lazy expiry: get returns null at deadline", e.get("temp"));
        t.isTrue("exists is false after expiry", !e.exists("temp"));
        t.eq("ttl is -2 after expiry", -2L, e.ttlMillis("temp"));

        // persistent vs missing TTL
        e.set("perm", "forever");
        t.eq("persistent key ttl is -1", -1L, e.ttlMillis("perm"));
        t.eq("missing key ttl is -2", -2L, e.ttlMillis("ghost"));

        // keys() hides expired keys
        ManualClock kClock = new ManualClock();
        Database kdb = new Database(kClock);
        kdb.set("live", "1");
        kdb.set("dying", "2", 5_000);
        kClock.advance(10_000);
        List<String> liveKeys = kdb.keys();
        t.isTrue("keys() includes the live key", liveKeys.contains("live"));
        t.isTrue("keys() excludes the expired key", !liveKeys.contains("dying"));
        t.eq("size() counts only live keys", 1, kdb.size());

        // active expiration: purgeExpired removes dead keys, spares persistent ones
        ManualClock pClock = new ManualClock();
        Database pdb = new Database(pClock);
        pdb.set("a", "1", 1_000);
        pdb.set("b", "2", 1_000);
        pdb.set("c", "3");
        pClock.advance(2_000);
        t.eq("purgeExpired removes the 2 dead keys", 2, pdb.purgeExpired());
        t.eq("persistent key survives the purge", "3", pdb.get("c"));

        // INCR on an expired key restarts from 0 and drops the old TTL
        ManualClock iClock = new ManualClock();
        Database idb = new Database(iClock);
        idb.set("n", "41", 1_000);
        iClock.advance(2_000);
        t.eq("incr on expired key restarts at 1", 1L, idb.incrBy("n", 1));
        t.eq("incr cleared the old TTL (now persistent)", -1L, idb.ttlMillis("n"));

        t.done();
        return t;
    }
}
