package com.miniredis.core;

/**
 * A source of "now", in milliseconds. Injected into Database so tests can
 * control time deterministically without sleeping.
 *
 * The production clock is MONOTONIC (based on System.nanoTime), not wall-clock
 * (System.currentTimeMillis), preventing clock skew/NTP issues from affecting TTLs.
 */
public interface TimeSource {

    long nowMillis();

    /** The real, monotonic system clock used in production. */
    TimeSource SYSTEM = () -> System.nanoTime() / 1_000_000L;
}
