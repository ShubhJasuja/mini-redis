import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * All server output goes through here.
 *
 * Why a class instead of scattered System.out.println calls: once many threads
 * log at once we want one place to control both the format and, later, the
 * destination. A log line is only useful if it answers when, what kind, and who.
 *
 * One line looks like:
 *
 *     14:23:02.250 [COMMAND] [client-2] SET name Shubh
 *     14:23:05.700 [SERVER] [expiry-reaper] Reaper purged 2 expired key(s)
 *
 *  - WHEN: HH:mm:ss with milliseconds. The reaper wakes every 100ms and TTLs
 *    run on second boundaries, so sub-second timing is exactly what we may
 *    need to correlate.
 *  - WHAT KIND: SERVER = lifecycle event, COMMAND = one line from one client.
 *  - WHO: the thread's CURRENT name. In this server that is main (startup),
 *    expiry-reaper, or client-N - the client the thread serves right now.
 *    ClientHandler renames its thread at serve time because the pool reuses
 *    threads; the name here is what the thread is doing, not what it was
 *    born as. See ClientHandler.run().
 *
 * Thread safety: System.out is a PrintStream, whose println is internally
 * synchronized, so two threads printing at the same time cannot interleave
 * halfway through a line. What is NOT guaranteed is ordering - with several
 * clients active, the order of lines between threads is whatever the OS
 * scheduler decides. That is expected, and it is why the fields exist.
 */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    /** Server lifecycle: startup, connects, disconnects, shutdown. */
    public static void server(String message) {
        line("SERVER", message);
    }

    /** One line exactly as a client sent it. */
    public static void command(String message) {
        line("COMMAND", message);
    }

    private static void line(String tag, String message) {
        System.out.println(LocalTime.now().format(TIME)
                + " [" + tag + "] [" + Thread.currentThread().getName() + "] " + message);
    }
}
