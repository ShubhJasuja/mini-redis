package com.miniredis.net;

import com.miniredis.core.ExpiryReaper;
import com.miniredis.core.PersistenceManager;
import com.miniredis.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates graceful server termination.
 *
 * Runs on Ctrl-C (JVM shutdown hook) and on the SHUTDOWN client command.
 */
public class ShutdownManager {

    private final ServerSocket serverSocket;
    private final ExecutorService clientPool;
    private final ExpiryReaper reaper;
    private final Thread reaperThread;
    private final PersistenceManager persistence;

    /** Every socket with an active ClientHandler. */
    private final Set<Socket> liveClients = ConcurrentHashMap.newKeySet();

    private volatile boolean shuttingDown = false;

    public ShutdownManager(ServerSocket serverSocket, ExecutorService clientPool,
                           ExpiryReaper reaper, Thread reaperThread) {
        this(serverSocket, clientPool, reaper, reaperThread, null);
    }

    public ShutdownManager(ServerSocket serverSocket, ExecutorService clientPool,
                           ExpiryReaper reaper, Thread reaperThread,
                           PersistenceManager persistence) {
        this.serverSocket = serverSocket;
        this.clientPool = clientPool;
        this.reaper = reaper;
        this.reaperThread = reaperThread;
        this.persistence = persistence;
    }

    public void register(Socket socket) { liveClients.add(socket); }
    public void unregister(Socket socket) { liveClients.remove(socket); }

    public boolean isShuttingDown() { return shuttingDown; }

    public void requestShutdown() {
        System.exit(0);
    }

    public void shutdown() {
        shuttingDown = true;
        Log.server("Shutting down - no new connections will be accepted");

        // 1. Stop accepting new connections
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.server("While closing server socket: " + e.getMessage());
        }

        // 2. Stop the expiry reaper
        if (reaper != null) {
            reaper.stop();
        }
        if (reaperThread != null) {
            try {
                reaperThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 3. Close AOF file cleanly
        if (persistence != null) {
            persistence.close();
        }

        // 4. Refuse new tasks and allow in-flight commands to finish
        clientPool.shutdown();

        // 5. One second of grace, then force-close lingering sockets
        boolean finished;
        try {
            finished = clientPool.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            Log.server("Grace period over - closing " + liveClients.size()
                    + " client connection(s)");
            closeAllClients();
            clientPool.shutdownNow();
            try {
                clientPool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Log.server("Shutdown complete");
    }

    private void closeAllClients() {
        for (Socket socket : liveClients) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        liveClients.clear();
    }
}
