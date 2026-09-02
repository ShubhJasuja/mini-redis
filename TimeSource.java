/**
 * A source of "now", in milliseconds. Injected into Database so tests can
 * control time instead of calling Thread.sleep(10_000) to watch a key expire.
 *
 * The production clock is MONOTONIC (based on System.nanoTime), not wall-clock
 * (System.currentTimeMillis). Wall-clock time can jump backwards when the
 * machine syncs with an NTP server, which would make an already-expired key look
 * alive again, or kill a key early. nanoTime only ever moves forward, so a
 * deadline computed from it is stable. Only DIFFERENCES between nowMillis()
 * readings are meaningful - the absolute value is arbitrary (it can even be
 * negative), which is fine because we only ever compare now against a deadline
 * computed from the same clock.
 */
public interface TimeSource {

    long nowMillis();

    /** The real, monotonic system clock used when the server runs for real. */
    TimeSource SYSTEM = () -> System.nanoTime() / 1_000_000L;
}
