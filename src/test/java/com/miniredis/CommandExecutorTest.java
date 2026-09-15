package com.miniredis;

import com.miniredis.core.Database;
import com.miniredis.protocol.CommandExecutor;
import com.miniredis.protocol.CommandParser;

/**
 * Unit tests for {@link CommandExecutor}.
 */
public class CommandExecutorTest {

    public static void main(String[] args) {
        System.exit(run().exitCode());
    }

    public static TestKit run() {
        TestKit t = new TestKit("CommandExecutorTest");

        CommandExecutor ex = new CommandExecutor(new Database());

        // --- the exact sequence from the spec ---
        t.eq("SET name Shubh -> OK", "OK", run(ex, "SET name Shubh"));
        t.eq("GET name -> Shubh", "Shubh", run(ex, "GET name"));
        t.eq("EXISTS name -> 1", "1", run(ex, "EXISTS name"));
        t.eq("DEL name -> 1", "1", run(ex, "DEL name"));
        t.eq("GET name (after del) -> (nil)", "(nil)", run(ex, "GET name"));
        t.eq("EXISTS name (after del) -> 0", "0", run(ex, "EXISTS name"));
        t.eq("DEL name (absent) -> 0", "0", run(ex, "DEL name"));

        // --- PING ---
        t.eq("PING -> PONG", "PONG", run(ex, "PING"));
        t.eq("PING hello -> hello (echo)", "hello", run(ex, "PING hello"));

        // --- KEYS ---
        t.eq("KEYS on empty -> (empty)", "(empty)", run(ex, "KEYS"));
        run(ex, "SET a 1");
        run(ex, "SET b 2");
        String keys = run(ex, "KEYS");
        t.isTrue("KEYS lists a", keys.contains("a"));
        t.isTrue("KEYS lists b", keys.contains("b"));

        // --- overwrite ---
        run(ex, "SET a 1");
        t.eq("SET overwrites -> OK", "OK", run(ex, "SET a 99"));
        t.eq("GET sees overwritten value", "99", run(ex, "GET a"));

        // --- arity errors ---
        t.eq("GET with no key errors", "(error) wrong number of arguments for 'get'",
                run(ex, "GET"));
        t.eq("SET with only a key errors", "(error) wrong number of arguments for 'set'",
                run(ex, "SET lonely"));

        // --- unknown command ---
        t.eq("unknown command reported", "(error) unknown command 'FLUSHALL'",
                run(ex, "FLUSHALL"));

        // --- INCR / DECR ---
        CommandExecutor c = new CommandExecutor(new Database());
        t.eq("INCR on fresh key -> 1", "1", run(c, "INCR counter"));
        t.eq("INCR again -> 2", "2", run(c, "INCR counter"));
        t.eq("DECR -> 1", "1", run(c, "DECR counter"));
        t.eq("DECR -> 0", "0", run(c, "DECR counter"));
        t.eq("DECR below zero -> -1", "-1", run(c, "DECR counter"));
        run(c, "SET word Shubh");
        t.eq("INCR on non-integer -> error",
                "(error) value is not an integer or out of range", run(c, "INCR word"));
        t.eq("INCR with no key -> arity error",
                "(error) wrong number of arguments for 'incr'", run(c, "INCR"));
        t.eq("DECR with no key -> arity error",
                "(error) wrong number of arguments for 'decr'", run(c, "DECR"));

        // --- SET with expiry + TTL, on a manual clock ---
        ManualClock clock = new ManualClock();
        CommandExecutor xc = new CommandExecutor(new Database(clock));
        t.eq("SET k v EX 10 -> OK (now supported)", "OK", run(xc, "SET k v EX 10"));
        t.eq("GET before expiry -> value", "v", run(xc, "GET k"));
        t.eq("TTL k -> 10 seconds", "10", run(xc, "TTL k"));
        clock.advance(10_000);
        t.eq("GET after 10s -> (nil)", "(nil)", run(xc, "GET k"));
        t.eq("TTL of expired key -> -2", "-2", run(xc, "TTL k"));

        CommandExecutor px = new CommandExecutor(new Database(new ManualClock()));
        t.eq("SET with PX milliseconds -> OK", "OK", run(px, "SET k v PX 500"));
        run(px, "SET persistent v");
        t.eq("TTL of persistent key -> -1", "-1", run(px, "TTL persistent"));
        t.eq("TTL of missing key -> -2", "-2", run(px, "TTL ghost"));

        // --- expiry option validation ---
        t.eq("EX 0 -> invalid expire time",
                "(error) invalid expire time in 'set'", run(xc, "SET k v EX 0"));
        t.eq("EX -5 -> invalid expire time",
                "(error) invalid expire time in 'set'", run(xc, "SET k v EX -5"));
        t.eq("unknown option -> syntax error",
                "(error) syntax error", run(xc, "SET k v ZZ 10"));
        t.eq("EX with non-number -> error",
                "(error) value is not an integer or out of range", run(xc, "SET k v EX abc"));

        t.done();
        return t;
    }

    private static String run(CommandExecutor ex, String line) {
        return ex.execute(CommandParser.parse(line));
    }
}
