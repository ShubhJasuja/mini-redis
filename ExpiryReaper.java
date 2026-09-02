/**
 * Active expiration: a single background thread that periodically asks the
 * Database to purge keys whose deadline has passed.
 *
 * Why this exists even though every read already expires keys lazily: a key that
 * is SET with a TTL and then never read again would never hit the lazy path, so
 * its memory would leak forever. One sweeper thread total (not one timer per
 * key) keeps memory bounded. Real Redis does the same - lazy check on access
 * plus incremental background sampling.
 *
 * ONE thread for the whole server, run as a daemon so it can never keep the JVM
 * alive on its own. stop() exists for the graceful shutdown of step 9:
 * ShutdownManager calls it and then joins this thread, so the reaper's exit
 * line is the last thing to appear before the shutdown sequence finishes.
 */
public class ExpiryReaper implements Runnable {

    private final Database db;
    private final long intervalMillis;
    private volatile boolean running = true;   // volatile: written by stop(), read by run()

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
                // Interrupt is the signal to stop. Restore the flag and leave.
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

    /** Ask the reaper to finish its current nap and exit (used in step 9). */
    public void stop() {
        running = false;
    }
}
