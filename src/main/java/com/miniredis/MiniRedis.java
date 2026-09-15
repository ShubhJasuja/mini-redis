package com.miniredis;

import com.miniredis.core.Database;
import com.miniredis.core.ExpiryReaper;
import com.miniredis.core.PersistenceManager;
import com.miniredis.net.ClientHandler;
import com.miniredis.net.ShutdownManager;
import com.miniredis.protocol.CommandExecutor;
import com.miniredis.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MiniRedis Server Entry Point.
 *
 * Listens for client connections over TCP, manages concurrency via a cached
 * thread pool, drives active key expiration, and ensures durable persistence
 * with an Append-Only File (AOF).
 */
public class MiniRedis {

    private static final int DEFAULT_PORT = 6379;
    private static final String DEFAULT_AOF_PATH = "appendonly.aof";

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        boolean aofEnabled = true;

        for (String arg : args) {
            if ("--no-aof".equalsIgnoreCase(arg)) {
                aofEnabled = false;
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("Usage: java com.miniredis.MiniRedis [port] [--no-aof]");
                    System.exit(2);
                }
            }
        }

        AtomicInteger nextClientId = new AtomicInteger(1);

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "client-" + threadNumber.getAndIncrement());
            }
        };
        ExecutorService clientPool = Executors.newCachedThreadPool(factory);

        // Core store & persistence
        Database database = new Database();
        PersistenceManager persistence = new PersistenceManager(DEFAULT_AOF_PATH, aofEnabled);

        if (aofEnabled) {
            int replayed = persistence.replay(database);
            if (replayed > 0) {
                Log.server("Durable state restored (" + replayed + " mutations replayed)");
            }
        }

        CommandExecutor executor = new CommandExecutor(database, persistence);

        // Expiry reaper background thread
        ExpiryReaper reaper = new ExpiryReaper(database, 100);
        Thread reaperThread = new Thread(reaper, "expiry-reaper");
        reaperThread.setDaemon(true);
        reaperThread.start();
        Log.server("Expiry reaper started (sweeps every 100ms)");

        ServerSocket serverSocket = new ServerSocket(port);
        ShutdownManager manager =
                new ShutdownManager(serverSocket, clientPool, reaper, reaperThread, persistence);

        Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown, "shutdown-hook"));

        try {
            Log.server("MiniRedis listening on port " + port
                    + (aofEnabled ? " [AOF persistence: enabled]" : " [AOF persistence: disabled]"));

            while (true) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (SocketException e) {
                    if (manager.isShuttingDown()) {
                        break;
                    }
                    throw e;
                }

                try {
                    clientPool.submit(new ClientHandler(client, nextClientId.getAndIncrement(),
                            executor, manager));
                } catch (RejectedExecutionException e) {
                    client.close();
                    break;
                }
            }
        } finally {
            clientPool.shutdown();
        }
    }
}
