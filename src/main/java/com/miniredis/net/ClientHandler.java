package com.miniredis.net;

import com.miniredis.protocol.Command;
import com.miniredis.protocol.CommandExecutor;
import com.miniredis.protocol.CommandParser;
import com.miniredis.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles the complete conversation with ONE client on its own pool thread.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final int id;
    private final CommandExecutor executor;
    private final ShutdownManager manager;

    public ClientHandler(Socket socket, int id, CommandExecutor executor, ShutdownManager manager) {
        this.socket = socket;
        this.id = id;
        this.executor = executor;
        this.manager = manager;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("client-" + id);
        manager.register(socket);

        String peer = String.valueOf(socket.getRemoteSocketAddress());
        Log.server("Client connected (" + peer + ")");

        try (Socket managed = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(managed.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(managed.getOutputStream(), StandardCharsets.UTF_8), true)) {

            out.println("MiniRedis. Commands: SET GET DEL EXISTS KEYS INCR DECR TTL PING. QUIT to leave.");

            String line;
            while ((line = in.readLine()) != null) {
                Log.command(line);

                Command cmd = CommandParser.parse(line);
                if (cmd == null) {
                    continue;
                }

                if (cmd.is("QUIT")) {
                    out.println("BYE");
                    break;
                }

                if (cmd.is("SHUTDOWN")) {
                    out.println("OK");
                    manager.requestShutdown();
                    return;
                }

                out.println(executor.execute(cmd));
            }
        } catch (IOException e) {
            if (manager.isShuttingDown()) {
                Log.server("Client connection closed by shutdown (" + peer + ")");
            } else {
                Log.server("Connection error: " + e.getMessage());
            }
        } finally {
            manager.unregister(socket);
            Log.server("Client disconnected (" + peer + ")");
        }
    }
}
