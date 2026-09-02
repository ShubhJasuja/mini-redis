import java.util.ArrayList;
import java.util.List;

/**
 * Runs every MiniRedis test suite in one JVM and prints one verdict.
 *
 * Why this exists: `java CommandParserTest && java DatabaseTest && ...` works,
 * but it is four processes, four JVM startups, and a verdict you have to
 * assemble by eye. One command, one exit code, one total.
 *
 * This is why each suite exposes run() separately from main(): main() ends with
 * System.exit, which would kill this JVM after the first suite. run() returns
 * its TestKit instead, so the totals can be added up here.
 *
 * Run:  java RunAllTests        (exit code 0 = everything passed)
 */
public class RunAllTests {

    public static void main(String[] args) throws Exception {
        System.out.println("=========================================");
        System.out.println("  MiniRedis - full test suite");
        System.out.println("=========================================");
        System.out.println();

        List<TestKit> results = new ArrayList<>();
        results.add(CommandParserTest.run());
        results.add(DatabaseTest.run());
        results.add(CommandExecutorTest.run());
        results.add(ShutdownManagerTest.run());

        int passed = 0;
        int failed = 0;
        List<String> failedSuites = new ArrayList<>();
        for (TestKit kit : results) {
            passed += kit.passedCount();
            failed += kit.failedCount();
            if (kit.failedCount() > 0) {
                failedSuites.add(kit.suite());
            }
        }

        System.out.println("=========================================");
        if (failed == 0) {
            System.out.println("  ALL " + results.size() + " SUITES PASSED"
                    + "  (" + passed + " assertions)");
            System.out.println("=========================================");
            System.exit(0);
        } else {
            System.out.println("  FAILED: " + failed + " of " + (passed + failed)
                    + " assertions");
            System.out.println("  in: " + String.join(", ", failedSuites));
            System.out.println("=========================================");
            System.exit(1);
        }
    }
}
