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
 * MiniRedis - Step 2: one thread per client.
 *
 * Step 1 served a single client at a time: the accept loop called handle()
 * directly, so it could not accept anyone else until that client hung up.
 *
 * Now the accept loop does one thing only - accept and hand off. All the talking
 * happens on a pool thread inside ClientHandler.
 *
 * Still echo-only on purpose: nothing about the protocol changed in this step,
 * so the same two-client test that failed in step 1 should now pass. One
 * variable at a time.
 */
public class MiniRedis {

    private static final int DEFAULT_PORT = 6379;

    public static void main(String[] args) throws IOException {
        // Defaults to 6379. An optional argument lets a second instance run on
        // another port, which is handy when 6379 is already taken and later when
        // tests need a throwaway port: java MiniRedis 6380
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: java MiniRedis [port]");
                System.exit(2);
            }
        }

        // Numbers clients so their log lines can be told apart once several are
        // active. The id becomes the serving thread's name (client-N) when the
        // handler runs, which is what Log prints. Atomic because the accept loop
        // is not the only reader in later steps, and because it costs nothing.
        AtomicInteger nextClientId = new AtomicInteger(1);

        // Cached pool: creates a thread when one is needed, reuses threads left
        // idle by departed clients (for 60s), and is unbounded.
        //
        // Why not a fixed pool? A fixed pool of N would silently reintroduce the
        // step-1 bug for client N+1: accepted by the kernel, then stuck in the
        // task queue with nobody to serve it.
        //
        // Why not `new Thread(...).start()` per client? It works, but a pool
        // gives us thread reuse, and gives step 9 a single object to shut down.
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                // Naming threads is free and makes a stack dump readable. This
                // is the BIRTH name only - ClientHandler renames the thread to
                // client-<id> for the duration of each connection, because this
                // pool hands the same thread to many clients over its lifetime.
                return new Thread(r, "client-" + threadNumber.getAndIncrement());
            }
        };
        ExecutorService clientPool = Executors.newCachedThreadPool(factory);

        // One Database, one executor, shared by every client thread. This shared
        // state is what makes it a server: a value SET by one client is visible
        // to GET from another. Database is thread-safe (ConcurrentHashMap), and
        // the executor is stateless apart from its Database reference.
        Database database = new Database();
        CommandExecutor executor = new CommandExecutor(database);

        // Active expiration: one daemon thread sweeps expired keys every 100ms so
        // keys that are set-with-TTL and never read again cannot leak memory.
        // Daemon => it never keeps the JVM alive by itself. Step 9 is where
        // stop() gets used: shutdown stops and joins this thread.
        ExpiryReaper reaper = new ExpiryReaper(database, 100);
        Thread reaperThread = new Thread(reaper, "expiry-reaper");
        reaperThread.setDaemon(true);
        reaperThread.start();
        Log.server("Expiry reaper started (sweeps every 100ms)");

        // The socket must exist before the manager, because the manager owns
        // closing it. Note: if bind fails here, no manager and no hook were
        // created yet - nothing to clean up, main just throws and exits.
        ServerSocket serverSocket = new ServerSocket(port);
        ShutdownManager manager =
                new ShutdownManager(serverSocket, clientPool, reaper, reaperThread);

        // The single shutdown sequence for the whole server. Runs on Ctrl-C
        // (the JVM runs registered shutdown hooks when it sees the signal) and
        // on the SHUTDOWN command (System.exit runs the same hooks).
        Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown, "shutdown-hook"));

        try {
            Log.server("MiniRedis listening on port " + port);

            while (true) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (SocketException e) {
                    // accept() only throws when the socket is closed - by the
                    // shutdown sequence. That close is the signal to stop, not
                    // an error. Anything else would be a real failure.
                    if (manager.isShuttingDown()) {
                        break;
                    }
                    throw e;
                }

                try {
                    // Returns immediately. This is the whole difference from step 1.
                    clientPool.submit(new ClientHandler(client, nextClientId.getAndIncrement(),
                            executor, manager));
                } catch (RejectedExecutionException e) {
                    // Shutdown started between accept() and submit(). The pool
                    // refuses new work, so we must refuse this client ourselves.
                    client.close();
                    break;
                }
            }
        } finally {
            // When the loop ends - shutdown, or more rarely a bind or accept
            // failure - make sure the pool is shutting down too. Idempotent:
            // the crash-after-grace path in ShutdownManager may have done it.
            clientPool.shutdown();
        }
    }
}
