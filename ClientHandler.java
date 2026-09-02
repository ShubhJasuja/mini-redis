import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles the whole conversation with ONE client, on its own thread.
 *
 * Step 4 change: the echo is gone. Each line is now parsed into a Command and
 * run through the shared CommandExecutor, and the executor's reply is sent back.
 * This is the moment SET/GET/DEL/EXISTS/KEYS/PING actually work over TCP.
 *
 * The executor and its Database are shared by every ClientHandler, so what one
 * client SETs, another can GET. That shared state is the whole point of the
 * server, and it is safe because Database is built on a ConcurrentHashMap.
 *
 * Step 9 adds the SHUTDOWN command, which is deliberately handled HERE rather
 * than in the executor: it is not a data command, it ends the whole server -
 * the handler knows about the server lifecycle (via ShutdownManager), the
 * executor knows about keys and values.
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
        // This thread came from a shared CACHED pool, so it may have served
        // other clients before us and will serve others after. Rename it to
        // the current client: the thread name that Log prints is the truth
        // about what the thread is doing right now, not what it was born as.
        // (Without this, client-2 could be serving client #11 and the log
        // would lie about it. The factory name is only the birth name.)
        Thread.currentThread().setName("client-" + id);

        // Register BEFORE the greeting: if shutdown begins while this handler is
        // still setting up, the socket is reachable and will be closed.
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

                // A blank / whitespace-only line is not a command. Send nothing
                // and wait for the next line (a well-behaved client won't send
                // blank lines; the step-7 client skips them before sending).
                if (cmd == null) {
                    continue;
                }

                // QUIT is a connection-control command, not a data command, so it
                // is handled here rather than in the executor: it must close the
                // socket, which the executor knows nothing about.
                if (cmd.is("QUIT")) {
                    out.println("BYE");
                    break;
                }

                // SHUTDOWN is server-level, not socket-level. Say OK so the
                // client is not left waiting for a reply while the world ends,
                // then exit: System.exit runs the JVM's shutdown hooks, which
                // is the same sequence the server runs on Ctrl-C.
                if (cmd.is("SHUTDOWN")) {
                    out.println("OK");
                    manager.requestShutdown();
                    return;
                }

                out.println(executor.execute(cmd));
            }
        } catch (IOException e) {
            if (manager.isShuttingDown()) {
                // Our socket was closed by the server's own shutdown - that is
                // the planned path, not a connection error. Report it quietly.
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
