import java.util.Objects;

/**
 * A tiny zero-dependency test harness.
 *
 * The project must stay "Java only, no libraries", so we cannot use JUnit. This
 * gives us just enough: named assertions, a pass/fail tally, and an exit code.
 * A test class is an ordinary program with a main() that creates a TestKit,
 * makes assertions, and ends with `System.exit(kit.done())`. done() returns 0
 * when everything passed and 1 otherwise, so a script (or CI) can tell success
 * from failure by the process exit code alone.
 */
public final class TestKit {

    private final String suite;
    private int passed = 0;
    private int failed = 0;

    public TestKit(String suite) {
        this.suite = suite;
        System.out.println("== " + suite + " ==");
    }

    /** Assert that two values are equal (null-safe, uses .equals). */
    public void eq(String label, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            pass(label);
        } else {
            fail(label, "expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Assert that a condition is true. */
    public void isTrue(String label, boolean condition) {
        if (condition) {
            pass(label);
        } else {
            fail(label, "expected true");
        }
    }

    /** Assert that a reference is null. */
    public void isNull(String label, Object value) {
        if (value == null) {
            pass(label);
        } else {
            fail(label, "expected null but was <" + value + ">");
        }
    }

    /** Assert that a reference is not null. */
    public void notNull(String label, Object value) {
        if (value != null) {
            pass(label);
        } else {
            fail(label, "expected non-null");
        }
    }

    private void pass(String label) {
        passed++;
        System.out.println("  [PASS] " + label);
    }

    private void fail(String label, String why) {
        failed++;
        System.out.println("  [FAIL] " + label + "  --  " + why);
    }

    /** Print the summary and return a process exit code (0 = all passed). */
    public int done() {
        System.out.println("-- " + suite + ": " + passed + " passed, " + failed + " failed --");
        System.out.println();
        return exitCode();
    }

    // --- accessors, so RunAllTests can total up several suites in one JVM ---

    /** 0 if every assertion passed, 1 otherwise. Does not print. */
    public int exitCode() {
        return failed == 0 ? 0 : 1;
    }

    public String suite() {
        return suite;
    }

    public int passedCount() {
        return passed;
    }

    public int failedCount() {
        return failed;
    }
}
