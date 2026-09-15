package com.miniredis.core;

/**
 * One stored value plus the instant it expires.
 *
 * Immutable: every SET or INCR creates a brand-new Entry rather than mutating an
 * existing one.
 *
 * The deadline is ABSOLUTE (a point in time from a TimeSource), not a remaining
 * duration. A key with no expiry stores NEVER.
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
        return nowMillis >= expireAt;
    }

    // Deliberately NO equals()/hashCode() override -> identity semantics.
    // Database uses store.remove(key, entry), which removes only if the current
    // mapping == the entry we pass. If a concurrent SET has replaced it, removal
    // harmlessly fails.
}
