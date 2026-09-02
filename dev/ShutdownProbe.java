import java.io.IOException;

/**
 * Dev-only scaffold (NOT part of the MiniRedis deliverable).
 *
 * Tests the SIGNAL path of graceful shutdown - what happens on Ctrl-C - without
 * needing a console to press keys in.
 *
 * Why this is a fair test. Ctrl-C does not call any MiniRedis code. It makes
 * the JVM start its shutdown sequence, and the JVM's only contract with our
 * program is: "I will run every thread registered with addShutdownHook".
 * System.exit(0) begins that exact same sequence. So both paths converge on
 * one line of ours - the hook registered in MiniRedis.main - and testing one
 * tests the mechanism the other relies on. What Ctrl-C adds is only the OS
 * getting the JVM's attention.
 *
 * (On Windows, CTRL_BREAK_EVENT is NOT a substitute: the JVM treats break as
 * "print a thread dump and keep running", the equivalent of kill -QUIT, which
 * is why this scaffold exists instead of a signal-sending one.)
 *
 * Starts the real server in a thread, waits, and exits from a DIFFERENT thread
 * than the accept loop - like a signal, which never arrives on the main thread.
 *
 * Run:  java -cp .:dev/out ShutdownProbe 6393 3000
 */
public class ShutdownProbe {

    public static void main(String[] args) throws Exception {
        String port = args.length > 0 ? args[0] : "6393";
        long waitMillis = args.length > 1 ? Long.parseLong(args[1]) : 3000;

        // Run the real server's main() on its own thread: it blocks in accept()
        // forever, exactly as it does in production.
        Thread server = new Thread(() -> {
            try {
                MiniRedis.main(new String[]{port});
            } catch (IOException e) {
                System.out.println("server failed: " + e.getMessage());
            }
        }, "server-main");
        server.start();

        Thread.sleep(waitMillis);

        // The moment a Ctrl-C would land. Note this is NOT the accept-loop
        // thread - shutdown has to work from outside, which is the whole point.
        System.out.println(">>> triggering shutdown (the Ctrl-C moment) <<<");
        System.exit(0);
    }
}
