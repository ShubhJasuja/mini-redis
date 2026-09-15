package com.miniredis;

import com.miniredis.core.Database;
import com.miniredis.core.ExpiryReaper;
import com.miniredis.net.ShutdownManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ShutdownManager}.
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

        boolean rebound = false;
        try (ServerSocket again = new ServerSocket(port)) {
            rebound = again.isBound();
        } catch (IOException e) {
            rebound = false;
        }
        t.isTrue("port is free again after shutdown", rebound);

        boolean threw = false;
        try {
            f.manager.shutdown();
        } catch (RuntimeException e) {
            threw = true;
        }
        t.isTrue("shutdown is safe to call twice", !threw);
    }

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

    private static void blockedHandlerSocketIsForceClosed(TestKit t) throws Exception {
        Fixture f = new Fixture();

        Socket clientEnd = new Socket("127.0.0.1", f.server.getLocalPort());
        Socket serverEnd = f.server.accept();
        f.manager.register(serverEnd);

        f.pool.submit(() -> {
            try {
                serverEnd.getInputStream().read();
            } catch (IOException expected) {
            }
        });
        Thread.sleep(150);

        long start = System.nanoTime();
        f.manager.shutdown();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        t.isTrue("blocked handler's socket was closed by shutdown", serverEnd.isClosed());
        t.isTrue("pool terminated once the socket was closed",
                f.pool.awaitTermination(2, TimeUnit.SECONDS));
        t.isTrue("waited the grace period (>=1s) but stayed bounded (<5s)",
                elapsedMs >= 1000 && elapsedMs < 5000);

        clientEnd.close();
    }

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
