import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the "server is going away" sequence (step 9).
 *
 * Two things can ask the server to stop, and both funnel into the SAME method:
 *   - a signal from outside (Ctrl-C): the JVM runs every registered shutdown
 *     hook, and MiniRedis.main registered shutdown() as one;
 *   - a SHUTDOWN command from a client: the handler replies OK and calls
 *     requestShutdown(), which is System.exit(0) - and System.exit runs the
 *     same hooks.
 * One sequence, two triggers. Real Redis's SHUTDOWN command is the analogy.
 *
 * The order matters. Stopping a server is like closing a restaurant:
 *   1. STOP TAKING ORDERS   - close the ServerSocket. The blocked accept()
 *      throws, the accept loop sees isShuttingDown() and leaves, and the OS
 *      refuses new connections from now on. Nobody can join mid-cleanup.
 *   2. STOP THE KITCHEN     - reaper.stop(), then join the reaper thread: it
 *      wakes within one interval (100ms), sees running == false, exits.
 *   3. LET PLATES FINISH    - clientPool.shutdown() refuses NEW tasks but lets
 *      every handler that is mid-conversation finish its current command.
 *   4. BOUNDED GRACE       - awaitTermination(1s). Handlers left running after
 *      that are almost certainly parked in readLine() on an idle client; they
 *      would never return on their own. So we stop being polite: close every
 *      live socket (that is what the registry is for), which makes those
 *      readLine() calls throw, and the threads unwind.
 *
 * liveClients is the load-bearing detail. Each ClientHandler registers its
 * socket on entry and unregisters on exit, so shutdown can close ALL of them at
 * once. Without that, one idle client would keep the JVM alive forever after
 * "Shutdown complete", because its handler thread never returns.
 */
public class ShutdownManager {

    private final ServerSocket serverSocket;
    private final ExecutorService clientPool;
    private final ExpiryReaper reaper;
    private final Thread reaperThread;

    /** Every socket with a live ClientHandler, so shutdown can close them all. */
    private final Set<Socket> liveClients = ConcurrentHashMap.newKeySet();

    /**
     * Volatile because handlers read it from their own threads: it lets them
     * distinguish "my socket was closed by the server shutting down" (normal)
     * from a genuine connection error.
     */
    private volatile boolean shuttingDown = false;

    public ShutdownManager(ServerSocket serverSocket, ExecutorService clientPool,
                           ExpiryReaper reaper, Thread reaperThread) {
        this.serverSocket = serverSocket;
        this.clientPool = clientPool;
        this.reaper = reaper;
        this.reaperThread = reaperThread;
    }

    /** Called by a ClientHandler on entry / exit. */
    public void register(Socket socket) { liveClients.add(socket); }
    public void unregister(Socket socket) { liveClients.remove(socket); }

    public boolean isShuttingDown() { return shuttingDown; }

    /**
     * The client-triggered path (the SHUTDOWN command). System.exit(0) runs the
     * JVM's shutdown hooks, so this goes through exactly the same sequence as
     * Ctrl-C. The caller's thread stays inside this call until the process is
     * gone, which is fine - there is nothing left for it to do.
     */
    public void requestShutdown() {
        System.exit(0);
    }

    /**
     * The sequence itself. Runs on the JVM's "shutdown-hook" thread.
     */
    public void shutdown() {
        shuttingDown = true;
        Log.server("Shutting down - no new connections will be accepted");

        // 1. Stop taking orders. close() unblocks accept() with SocketException;
        //    the loop in main sees isShuttingDown() and exits. From this moment
        //    on, connection attempts are refused at the OS level.
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.server("While closing server socket: " + e.getMessage());
        }

        // 2. Stop the kitchen and wait for it, so the reaper's exit line lands
        //    before ours (join = "wait until this thread is done").
        reaper.stop();
        try {
            reaperThread.join(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Refuse new client tasks; let handlers mid-command finish.
        clientPool.shutdown();

        // 4. One second of grace, then force. In practice the leftovers are
        //    idle clients parked in readLine() - their threads never return on
        //    their own, so the sockets have to be closed for us.
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
                // Let the woken handler threads unwind and log their
                // disconnects before we declare the shutdown complete.
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
                socket.close();   // idempotent; a clean close = FIN to the client
            } catch (IOException ignored) {
                // That client already hung up on its own; nothing to do.
            }
        }
        liveClients.clear();
    }
}
