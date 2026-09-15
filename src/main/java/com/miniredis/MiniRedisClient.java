package com.miniredis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * MiniRedis Interactive Terminal Client.
 *
 * Connects to a running MiniRedis server, provides an interactive REPL prompt,
 * and exchanges line-based commands and replies.
 */
public class MiniRedisClient {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        try {
            if (args.length == 1) {
                port = Integer.parseInt(args[0]);
            } else if (args.length == 2) {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } else if (args.length > 2) {
                System.err.println("Usage: java com.miniredis.MiniRedisClient [host] [port]");
                System.exit(2);
            }
        } catch (NumberFormatException e) {
            System.err.println("Port must be a number. Usage: java com.miniredis.MiniRedisClient [host] [port]");
            System.exit(2);
        }

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader keyboard = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Connected to MiniRedis at " + host + ":" + port);

            String greeting = in.readLine();
            if (greeting == null) {
                System.out.println("(server closed the connection immediately)");
                return;
            }
            System.out.println(greeting);

            converse(in, out, keyboard);

        } catch (ConnectException e) {
            System.err.println("Could not connect to " + host + ":" + port
                    + " - is the MiniRedis server running?");
            System.exit(1);
        } catch (SocketException e) {
            System.out.println("(connection lost - the server went away)");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void converse(BufferedReader in, PrintWriter out, BufferedReader keyboard)
            throws IOException {

        while (true) {
            System.out.print("MiniRedis> ");
            System.out.flush();

            String typed = keyboard.readLine();
            if (typed == null) {
                System.out.println();
                sendQuit(in, out);
                return;
            }

            if (typed.trim().isEmpty()) {
                continue;
            }

            String upper = typed.trim().toUpperCase();
            if (upper.equals("QUIT") || upper.equals("EXIT")) {
                sendQuit(in, out);
                return;
            }

            out.println(typed);

            String reply = in.readLine();
            if (reply == null) {
                System.out.println("(server closed the connection)");
                return;
            }
            System.out.println(reply);
        }
    }

    private static void sendQuit(BufferedReader in, PrintWriter out) throws IOException {
        out.println("QUIT");
        String bye = in.readLine();
        if (bye != null) {
            System.out.println(bye);
        }
    }
}
