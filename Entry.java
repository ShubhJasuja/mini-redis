/**
 * One stored value plus the instant it expires.
 *
 * Immutable: every SET or INCR creates a brand-new Entry rather than mutating an
 * existing one. That immutability is what makes lazy expiry safe under
 * concurrency - see the note on equals() below.
 *
 * The deadline is ABSOLUTE (a point in time from a TimeSource), not a remaining
 * duration. A duration would have to be counted down by someone; an absolute
 * deadline just sits there and every reader compares it against "now". A key
 * with no expiry stores NEVER, a deadline so far in the future that now can
 * never reach it - so no special-casing is needed in isExpired().
 */
public final class Entry {

    /** Sentinel deadline meaning "this key never expires". */
    public static final long NEVER = Long.MAX_VALUE;

    public final String value;
    public final long expireAt;   // millis, on some TimeSource's timeline

    public Entry(String value, long expireAt) {
        this.value = value;
        this.expireAt = expireAt;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expireAt;   // for NEVER (=MAX_VALUE) this is never true
    }

    // Deliberately NO equals()/hashCode() override -> identity semantics.
    //
    // Database uses store.remove(key, entry), which removes only if the current
    // mapping .equals() the entry we pass. With identity semantics that means
    // "remove this key only if it still holds THIS exact Entry object". If a
    // concurrent SET has replaced it with a new Entry, the removal harmlessly
    // fails, so we never delete a value someone just wrote.
}
