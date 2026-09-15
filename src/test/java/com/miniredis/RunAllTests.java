package com.miniredis;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every MiniRedis test suite in one JVM and prints the overall verdict.
 */
public class RunAllTests {

    public static void main(String[] args) throws Exception {
        System.out.println("=========================================");
        System.out.println("  MiniRedis - Full Test Suite");
        System.out.println("=========================================");
        System.out.println();

        List<TestKit> results = new ArrayList<>();
        results.add(CommandParserTest.run());
        results.add(DatabaseTest.run());
        results.add(CommandExecutorTest.run());
        results.add(ShutdownManagerTest.run());
        results.add(PersistenceTest.run());

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
