import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Dev-only test probe (NOT part of the MiniRedis deliverable - the real client
 * is MiniRedisClient, built in step 7).
 *
 * Two modes:
 *
 *   Scripted - each argument is sent as one line, replies are printed, then it
 *   hangs up. Used for automated testing:
 *       java -cp dev/out Probe "SET name Shubh" "GET name"
 *   An argument of the form sleep:2500 pauses instead of sending.
 *
 *   Interactive - with no arguments, whatever you type is sent to the server
 *   and whatever the server says is printed:
 *       java -cp dev/out Probe
 */
public class Probe {

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("port", "6379"));

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            if (args.length == 0) {
                interactive(in, out);
            } else {
                scripted(args, in, out);
            }
        }
    }

    private static void scripted(String[] args, BufferedReader in, PrintWriter out) throws Exception {
        System.out.println("<< " + in.readLine());   // drain the greeting
        for (String arg : args) {
            if (arg.startsWith("sleep:")) {
                long ms = Long.parseLong(arg.substring("sleep:".length()));
                System.out.println(".. sleeping " + ms + "ms");
                Thread.sleep(ms);
                continue;
            }
            System.out.println(">> " + arg);
            out.println(arg);
            System.out.println("<< " + in.readLine());
        }
    }

    private static void interactive(BufferedReader in, PrintWriter out) throws Exception {
        // A dedicated reader thread means anything the server sends is printed
        // the moment it arrives, even if we happen to be waiting on the keyboard.
        // Daemon, so it cannot keep the JVM alive after we are done typing.
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    System.out.print("> ");
                    System.out.flush();
                }
                System.out.println("\n(server closed the connection)");
            } catch (Exception e) {
                System.out.println("\n(disconnected: " + e.getMessage() + ")");
            }
            System.exit(0);
        }, "probe-reader");
        reader.setDaemon(true);
        reader.start();

        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        String typed;
        // Ctrl-D (or Ctrl-Z then Enter on Windows) ends stdin and returns null.
        while ((typed = keyboard.readLine()) != null) {
            out.println(typed);
            if (typed.equalsIgnoreCase("QUIT")) {
                Thread.sleep(150);   // let the BYE arrive before we tear down
                break;
            }
        }
    }
}
