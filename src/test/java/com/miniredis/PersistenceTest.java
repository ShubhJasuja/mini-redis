package com.miniredis;

import com.miniredis.core.Database;
import com.miniredis.core.PersistenceManager;
import com.miniredis.protocol.CommandExecutor;
import com.miniredis.protocol.CommandParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Unit tests for {@link PersistenceManager} (Append-Only File persistence).
 */
public class PersistenceTest {

    public static void main(String[] args) throws Exception {
        System.exit(run().exitCode());
    }

    public static TestKit run() throws Exception {
        TestKit t = new TestKit("PersistenceTest");

        File tempAof = File.createTempFile("test-miniredis-", ".aof");
        tempAof.deleteOnExit();

        try {
            PersistenceManager pm = new PersistenceManager(tempAof, true);
            Database db = new Database();
            CommandExecutor ex = new CommandExecutor(db, pm);

            // Execute a series of mutating commands
            ex.execute(CommandParser.parse("SET user Alice"));
            ex.execute(CommandParser.parse("SET temp 123"));
            ex.execute(CommandParser.parse("INCR counter"));
            ex.execute(CommandParser.parse("INCR counter"));
            ex.execute(CommandParser.parse("DECR counter"));
            ex.execute(CommandParser.parse("DEL temp"));
            // Read-only commands (should NOT appear in AOF)
            ex.execute(CommandParser.parse("GET user"));
            ex.execute(CommandParser.parse("EXISTS user"));
            ex.execute(CommandParser.parse("PING"));

            // Close the writer so everything is flushed
            pm.close();

            // Verify AOF contents on disk
            long lineCount = Files.lines(tempAof.toPath()).count();
            t.eq("mutations logged (SET, SET, INCR, INCR, DECR, DEL)", 6L, lineCount);

            // Simulate server crash & restart: brand new empty Database
            Database freshDb = new Database();
            t.isNull("fresh db is empty before replay", freshDb.get("user"));
            t.eq("fresh db size is 0", 0, freshDb.size());

            // Replay AOF
            PersistenceManager recoveryPm = new PersistenceManager(tempAof, false);
            int replayed = recoveryPm.replay(freshDb);
            t.eq("replayed 6 mutations", 6, replayed);

            // Verify state is restored
            t.eq("user restored to Alice", "Alice", freshDb.get("user"));
            t.eq("counter restored to 1", "1", freshDb.get("counter"));
            t.isNull("temp was deleted, remains null", freshDb.get("temp"));
            t.eq("restored db size is 2", 2, freshDb.size());

        } finally {
            try {
                Files.deleteIfExists(tempAof.toPath());
            } catch (IOException ignored) {}
        }

        t.done();
        return t;
    }
}
