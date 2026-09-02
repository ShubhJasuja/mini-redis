import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Dev-only scaffold (NOT part of the MiniRedis deliverable).
 *
 * Accepts one connection, sends the greeting, then closes the socket POLITELY -
 * a normal close(), which puts a TCP FIN on the wire.
 *
 * Why this exists: it isolates the one client branch the real server cannot
 * reach yet. Two very different things both mean "the server is gone":
 *
 *   FIN (polite close)  -> readLine() returns null      -> the null branch
 *   RST (process killed) -> readLine() throws SocketException -> the catch
 *
 * MiniRedis only closes politely on QUIT (where the client is already leaving)
 * and, from step 9 on, during graceful shutdown. So without this scaffold the
 * null branch would be untested code until step 9.
 *
 * Run:  java -cp dev/out RudeServer 6391
 */
public class RudeServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6391;

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("RudeServer listening on " + port + " (will greet, then hang up)");

            try (Socket client = server.accept()) {
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println("MiniRedis. Commands: SET GET DEL EXISTS KEYS INCR DECR TTL PING. QUIT to leave.");
                System.out.println("greeted; closing politely now");
            }   // close() here -> FIN
        }
    }
}
