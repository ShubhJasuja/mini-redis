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
 * MiniRedisClient - Step 7: the interactive terminal client (requirement 7).
 *
 * This replaces the dev-only Probe. It connects to a running MiniRedis server,
 * gives you a prompt, sends each line you type, and prints the server's reply.
 *
 * The key design choice: this client is a strict, SYNCHRONOUS request/reply
 * loop - send one line, read exactly one line back, print it, repeat. That is
 * possible only because the MiniRedis protocol is itself lockstep: every command
 * produces exactly one reply line, and the server never speaks unprompted (apart
 * from the one greeting line at connect, which we read once up front).
 *
 * The dev Probe needed a separate reader thread; a real, general client (or a
 * protocol with server-push messages) would too. We don't, and saying why is the
 * lesson: match the client's complexity to the protocol's, not to habit.
 *
 * Run with:  java MiniRedisClient                 (localhost:6379)
 *            java MiniRedisClient 6380             (localhost, custom port)
 *            java MiniRedisClient 192.168.0.5 6379 (host + port)
 */
public class MiniRedisClient {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        // Flexible, forgiving argument parsing:
        //   (none)        -> localhost:6379
        //   <port>        -> localhost:<port>     (one numeric arg is a port)
        //   <host> <port> -> both
        try {
            if (args.length == 1) {
                port = Integer.parseInt(args[0]);
            } else if (args.length == 2) {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } else if (args.length > 2) {
                System.err.println("Usage: java MiniRedisClient [host] [port]");
                System.exit(2);
            }
        } catch (NumberFormatException e) {
            System.err.println("Port must be a number. Usage: java MiniRedisClient [host] [port]");
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

            // The server sends one greeting line the instant we connect. Read it
            // before entering the loop so our prompt lines up with real replies.
            String greeting = in.readLine();
            if (greeting == null) {
                System.out.println("(server closed the connection immediately)");
                return;
            }
            System.out.println(greeting);

            converse(in, out, keyboard);

        } catch (ConnectException e) {
            // The most common first-run mistake: no server listening yet.
            System.err.println("Could not connect to " + host + ":" + port
                    + " - is the MiniRedis server running?");
            System.exit(1);
        } catch (SocketException e) {
            // The connection existed and then broke: the server process was
            // killed, or the network dropped. A hard kill sends TCP RST, which
            // surfaces here as "Connection reset" rather than as a clean
            // end-of-stream. Note the contrast with the null-return case in
            // converse(): a server that closes politely sends FIN and readLine()
            // returns null. Same meaning to a user, two different code paths.
            System.out.println("(connection lost - the server went away)");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * The request/reply loop. Each turn: prompt, read a typed line, send it, read
     * one reply, print it. Ends when the user leaves (QUIT/EXIT or Ctrl-D) or the
     * server hangs up.
     */
    private static void converse(BufferedReader in, PrintWriter out, BufferedReader keyboard)
            throws IOException {

        while (true) {
            System.out.print("MiniRedis> ");
            System.out.flush();

            // Ctrl-D (Unix) or Ctrl-Z then Enter (Windows) closes stdin -> null.
            String typed = keyboard.readLine();
            if (typed == null) {
                System.out.println();      // move off the prompt line cleanly
                sendQuit(in, out);         // tell the server so it logs a clean exit
                return;
            }

            // A blank line isn't a command. Don't bother the server with it; just
            // re-prompt. (The server would ignore it anyway, but then we'd be
            // waiting on a reply that never comes.)
            if (typed.trim().isEmpty()) {
                continue;
            }

            // QUIT and EXIT both mean "leave". The server only knows QUIT, so EXIT
            // is a client-side convenience that we translate onto the wire.
            String upper = typed.trim().toUpperCase();
            if (upper.equals("QUIT") || upper.equals("EXIT")) {
                sendQuit(in, out);
                return;
            }

            out.println(typed);

            // Exactly one reply line per command. null means the server closed the
            // socket while we were waiting - report it and stop.
            String reply = in.readLine();
            if (reply == null) {
                System.out.println("(server closed the connection)");
                return;
            }
            System.out.println(reply);
        }
    }

    /** Send QUIT, print the server's BYE (if it arrives) so the exit feels clean. */
    private static void sendQuit(BufferedReader in, PrintWriter out) throws IOException {
        out.println("QUIT");
        String bye = in.readLine();
        if (bye != null) {
            System.out.println(bye);
        }
    }
}
