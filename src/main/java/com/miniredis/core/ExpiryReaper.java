package com.miniredis.core;

import com.miniredis.util.Log;

/**
 * Active expiration: a single background daemon thread that periodically sweeps
 * keys whose deadline has passed.
 */
public class ExpiryReaper implements Runnable {

    private final Database db;
    private final long intervalMillis;
    private volatile boolean running = true;

    public ExpiryReaper(Database db, long intervalMillis) {
        this.db = db;
        this.intervalMillis = intervalMillis;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            int purged = db.purgeExpired();
            if (purged > 0) {
                Log.server("Reaper purged " + purged + " expired key(s)");
            }
        }
        Log.server("Expiry reaper stopped");
    }

    public void stop() {
        running = false;
    }
}
