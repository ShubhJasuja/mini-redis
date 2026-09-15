package com.miniredis;

import com.miniredis.core.TimeSource;

/**
 * A controllable clock for unit tests.
 */
public class ManualClock implements TimeSource {

    private long now;

    public ManualClock(long startMillis) {
        this.now = startMillis;
    }

    public ManualClock() {
        this(1_000_000L);
    }

    @Override
    public long nowMillis() {
        return now;
    }

    public void advance(long deltaMillis) {
        this.now += deltaMillis;
    }

    public void set(long millis) {
        this.now = millis;
    }
}
