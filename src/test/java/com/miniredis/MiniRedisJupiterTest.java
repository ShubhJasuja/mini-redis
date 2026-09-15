package com.miniredis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit 5 test adapter.
 *
 * Enables standard Maven builds ('mvn test') and GitHub Actions CI/CD to
 * discover and execute all 5 test suites (125 assertions).
 */
public class MiniRedisJupiterTest {

    @Test
    public void testCommandParserSuite() {
        TestKit t = CommandParserTest.run();
        assertEquals(0, t.failedCount(), "CommandParserTest assertions should pass");
    }

    @Test
    public void testDatabaseSuite() {
        TestKit t = DatabaseTest.run();
        assertEquals(0, t.failedCount(), "DatabaseTest assertions should pass");
    }

    @Test
    public void testCommandExecutorSuite() {
        TestKit t = CommandExecutorTest.run();
        assertEquals(0, t.failedCount(), "CommandExecutorTest assertions should pass");
    }

    @Test
    public void testShutdownManagerSuite() throws Exception {
        TestKit t = ShutdownManagerTest.run();
        assertEquals(0, t.failedCount(), "ShutdownManagerTest assertions should pass");
    }

    @Test
    public void testPersistenceSuite() throws Exception {
        TestKit t = PersistenceTest.run();
        assertEquals(0, t.failedCount(), "PersistenceTest assertions should pass");
    }
}
