/**
 * Dev-only teaching demo (NOT part of the deliverable): shows the INCR race.
 *
 * Both counters below run on the SAME thread-safe Database (ConcurrentHashMap).
 * The difference is only HOW the increment is done:
 *
 *   NAIVE  - get() then set(): two separate atomic operations. Between the read
 *            and the write, another thread can read the same old value, so
 *            increments overwrite each other and some are lost.
 *
 *   ATOMIC - incrBy() uses compute(): the read-modify-write is one atomic step
 *            per key, so nothing is lost.
 *
 * The lesson: a thread-safe map does NOT make a get-then-set pair thread-safe.
 *
 * Run with:  java -cp . RaceDemo     (after compiling the project)
 */
public class RaceDemo {

    public static void main(String[] args) throws InterruptedException {
        int threads = 8;
        int perThread = 20_000;
        long expected = (long) threads * perThread;

        System.out.println("threads=" + threads + ", increments each=" + perThread);
        System.out.println("expected final value = " + expected);
        System.out.println();

        // --- NAIVE: get() then set(), on a thread-safe map ---
        Database naive = new Database();
        naive.set("c", "0");
        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                long v = Long.parseLong(naive.get("c"));   // read
                naive.set("c", Long.toString(v + 1));      //   ...modify...write
            }
        });
        long naiveResult = Long.parseLong(naive.get("c"));
        System.out.printf("NAIVE  get-then-set : %,d   -> LOST %,d updates%n",
                naiveResult, expected - naiveResult);

        // --- ATOMIC: incrBy() using compute() ---
        Database atomic = new Database();
        atomic.set("c", "0");
        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                atomic.incrBy("c", 1);
            }
        });
        long atomicResult = Long.parseLong(atomic.get("c"));
        System.out.printf("ATOMIC compute()    : %,d   -> LOST %,d updates%n",
                atomicResult, expected - atomicResult);
    }

    private static void runConcurrently(int n, Runnable task) throws InterruptedException {
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) {
            ts[i] = new Thread(task);
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }
    }
}
