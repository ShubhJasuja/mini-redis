import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory key-value store, now with key expiration (step 6).
 *
 * Values are wrapped in an {@link Entry} carrying an absolute expiry deadline.
 * Two mechanisms keep expiry correct AND tidy, exactly as real Redis does:
 *
 *   LAZY (passive)  - every read (get/exists/keys/ttl) checks the deadline. An
 *                     expired key is treated as absent and removed on the spot.
 *                     This guarantees a client can NEVER observe an expired key,
 *                     regardless of what any background thread is doing.
 *
 *   ACTIVE          - purgeExpired() sweeps out dead keys. Called periodically by
 *                     ExpiryReaper so that a key which is set-and-never-read-again
 *                     does not leak memory forever (lazy expiry alone never runs
 *                     for it, because nobody ever reads it).
 *
 * Time comes from an injected TimeSource so tests can fast-forward instead of
 * sleeping. Thread-safety is unchanged: ConcurrentHashMap, with compute() for
 * the read-modify-write in incrBy, and conditional remove(key, entry) for lazy
 * deletion so a concurrent SET is never clobbered.
 */
public class Database {

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final TimeSource clock;

    /** Production constructor: real monotonic clock. */
    public Database() {
        this(TimeSource.SYSTEM);
    }

    /** Test constructor: inject a controllable clock. */
    public Database(TimeSource clock) {
        this.clock = clock;
    }

    /** Set a value with no expiry. */
    public void set(String key, String value) {
        store.put(key, new Entry(value, Entry.NEVER));
    }

    /** Set a value that expires {@code ttlMillis} from now. */
    public void set(String key, String value, long ttlMillis) {
        store.put(key, new Entry(value, clock.nowMillis() + ttlMillis));
    }

    /** @return the value, or {@code null} if absent OR expired (lazy expiry). */
    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (e.isExpired(clock.nowMillis())) {
            store.remove(key, e);   // conditional: only this exact expired entry
            return null;
        }
        return e.value;
    }

    /** @return true if present and not expired. */
    public boolean exists(String key) {
        return get(key) != null;   // reuse the lazy-expiry path
    }

    /** Remove a key. @return true only if a LIVE (non-expired) key was removed. */
    public boolean delete(String key) {
        Entry e = store.remove(key);
        return e != null && !e.isExpired(clock.nowMillis());
    }

    /**
     * Atomically add {@code delta} to the integer at {@code key}, returning the
     * new value. A missing OR expired key is treated as 0, so INCR on an expired
     * key restarts at 1 and drops the old TTL. A live key keeps its existing TTL.
     *
     * @throws NumberFormatException if the current value is not an integer
     * @throws ArithmeticException   if the result would overflow a long
     */
    public long incrBy(String key, long delta) {
        long now = clock.nowMillis();
        Entry updated = store.compute(key, (k, current) -> {
            long base;
            long expireAt;
            if (current == null || current.isExpired(now)) {
                base = 0L;
                expireAt = Entry.NEVER;          // expired/missing -> fresh, no TTL
            } else {
                base = Long.parseLong(current.value);
                expireAt = current.expireAt;     // live -> keep its deadline
            }
            return new Entry(Long.toString(Math.addExact(base, delta)), expireAt);
        });
        return Long.parseLong(updated.value);
    }

    /** A snapshot of all LIVE keys (expired ones are filtered out). */
    public List<String> keys() {
        long now = clock.nowMillis();
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Entry> me : store.entrySet()) {
            if (!me.getValue().isExpired(now)) {
                result.add(me.getKey());
            }
        }
        return result;
    }

    /**
     * Remaining time to live, in milliseconds.
     * @return {@code >=0} millis left; {@code -1} if the key never expires;
     *         {@code -2} if the key is missing or already expired.
     */
    public long ttlMillis(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return -2;
        }
        long now = clock.nowMillis();
        if (e.isExpired(now)) {
            return -2;
        }
        if (e.expireAt == Entry.NEVER) {
            return -1;
        }
        return e.expireAt - now;
    }

    /**
     * Active expiration: remove every entry whose deadline has passed.
     * @return how many keys were purged (for logging).
     */
    public int purgeExpired() {
        long now = clock.nowMillis();
        int purged = 0;
        for (Map.Entry<String, Entry> me : store.entrySet()) {
            // remove(key, entry) is conditional, so a key re-SET after we read it
            // (thus no longer expired) is left untouched.
            if (me.getValue().isExpired(now) && store.remove(me.getKey(), me.getValue())) {
                purged++;
            }
        }
        return purged;
    }

    /** Number of LIVE keys. */
    public int size() {
        return keys().size();
    }
}
