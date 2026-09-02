/**
 * Test-only clock (NOT part of the deliverable). Lets a test move time forward
 * on demand so key-expiry can be verified in microseconds instead of by really
 * sleeping for ten seconds. Starts at an arbitrary non-zero value.
 */
public class ManualClock implements TimeSource {

    private long millis = 1_000_000L;

    @Override
    public long nowMillis() {
        return millis;
    }

    /** Jump the clock forward. */
    public void advance(long ms) {
        millis += ms;
    }
}
