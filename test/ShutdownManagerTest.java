import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ShutdownManager} - the step 9 logic.
 *
 * Harder to test than Database, and the reason is worth naming: shutdown IS
 * sockets and threads. There is no pure core to isolate the way CommandParser
 * has one. So rather than fake anything, each test builds the real thing in
 * miniature - a real ServerSocket, a real pool, a real reaper - and then
 * asserts on observable state: is the socket closed, is the thread dead, did
 * the pool terminate, is the port free again.
 *
 * ServerSocket(0) means "any free port the OS picks", so these tests never
 * collide with a running MiniRedis or with each other.
 *
 * requestShutdown() is deliberately untested: its whole body is System.exit,
 * which would kill the test JVM. What it triggers is shutdown(), and that is
 * what every test below exercises.
 *
 * Run with:  java ShutdownManagerTest
 */
public class ShutdownManagerTest {

    public static void main(String[] args) throws Exception {
        System.exit(run().exitCode());
    }

    public static TestKit run() throws Exception {
        TestKit t = new TestKit("ShutdownManagerTest");

        idleShutdownIsFastAndComplete(t);
        registryUnregisterIsHonoured(t);
        blockedHandlerSocketIsForceClosed(t);

        t.done();
        return t;
    }

    /** The common case - nobody connected. Must be quick, complete, repeatable. */
    private static void idleShutdownIsFastAndComplete(TestKit t) throws Exception {
        Fixture f = new Fixture();
        int port = f.server.getLocalPort();

        t.isTrue("isShuttingDown is false before shutdown", !f.manager.isShuttingDown());

        long start = System.nanoTime();
        f.manager.shutdown();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        t.isTrue("isShuttingDown is true after shutdown", f.manager.isShuttingDown());
        t.isTrue("idle shutdown is fast (no grace period needed)", elapsedMs < 1000);
        t.isTrue("server socket is closed", f.server.isClosed());
        t.isTrue("reaper thread has stopped", !f.reaperThread.isAlive());
        t.isTrue("client pool is shut down", f.pool.isShutdown());
        t.isTrue("client pool reached termination", f.pool.isTerminated());

        // The port is genuinely released, not merely marked closed: the proof
        // is that it can be bound again. This is what lets you restart the
        // server immediately instead of waiting for the OS to clean up.
        boolean rebound = false;
        try (ServerSocket again = new ServerSocket(port)) {
            rebound = again.isBound();
        } catch (IOException e) {
            rebound = false;
        }
        t.isTrue("port is free again after shutdown", rebound);

        // A shutdown hook can race a SHUTDOWN command, so this must be safe.
        boolean threw = false;
        try {
            f.manager.shutdown();
        } catch (RuntimeException e) {
            threw = true;
        }
        t.isTrue("shutdown is safe to call twice", !threw);
    }

    /**
     * unregister() must really remove. If it did not, shutdown would close
     * sockets belonging to clients that had already left on their own - which
     * would be harmless here but wrong, and would hide a leak in the set.
     */
    private static void registryUnregisterIsHonoured(TestKit t) throws Exception {
        Fixture f = new Fixture();

        Socket clientEnd = new Socket("127.0.0.1", f.server.getLocalPort());
        Socket serverEnd = f.server.accept();

        f.manager.register(serverEnd);
        f.manager.unregister(serverEnd);

        f.manager.shutdown();
        t.isTrue("unregistered socket is left alone by shutdown", !serverEnd.isClosed());

        serverEnd.close();
        clientEnd.close();
    }

    /**
     * The headline case, and the entire reason the socket registry exists.
     *
     * A handler parked in a blocking read never returns on its own: shutdown()
     * cannot interrupt it, and shutdownNow()'s interrupt does not break blocking
     * socket I/O either. So the graceful wait MUST expire, and the socket MUST
     * be closed for the thread to unwind. Without the registry, this thread
     * would keep the JVM alive forever.
     */
    private static void blockedHandlerSocketIsForceClosed(TestKit t) throws Exception {
        Fixture f = new Fixture();

        Socket clientEnd = new Socket("127.0.0.1", f.server.getLocalPort());
        Socket serverEnd = f.server.accept();
        f.manager.register(serverEnd);

        // Occupy a pool thread exactly the way an idle ClientHandler does:
        // blocked in a read nobody will ever satisfy. The client end stays open
        // and silent, so this cannot return until the socket is closed.
        f.pool.submit(() -> {
            try {
                serverEnd.getInputStream().read();
            } catch (IOException expected) {
                // Thrown when shutdown closes the socket. That is the point.
            }
        });
        Thread.sleep(150);   // let the task actually reach the blocking read

        long start = System.nanoTime();
        f.manager.shutdown();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        t.isTrue("blocked handler's socket was closed by shutdown", serverEnd.isClosed());
        t.isTrue("pool terminated once the socket was closed",
                f.pool.awaitTermination(2, TimeUnit.SECONDS));
        // It had to wait out the full grace period and then force. This asserts
        // both halves of "graceful": it really waits, and it really is bounded.
        t.isTrue("waited the grace period (>=1s) but stayed bounded (<5s)",
                elapsedMs >= 1000 && elapsedMs < 5000);

        clientEnd.close();
    }

    /** A complete, independent server-shaped setup on an OS-chosen port. */
    private static final class Fixture {
        final ServerSocket server;
        final ExecutorService pool;
        final ExpiryReaper reaper;
        final Thread reaperThread;
        final ShutdownManager manager;

        Fixture() throws IOException {
            server = new ServerSocket(0);
            pool = Executors.newCachedThreadPool();
            reaper = new ExpiryReaper(new Database(), 50);
            reaperThread = new Thread(reaper, "test-reaper");
            reaperThread.setDaemon(true);
            reaperThread.start();
            manager = new ShutdownManager(server, pool, reaper, reaperThread);
        }
    }
}
