package com.miniredis.core;

import com.miniredis.protocol.Command;
import com.miniredis.protocol.CommandExecutor;
import com.miniredis.protocol.CommandParser;
import com.miniredis.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Append-Only File (AOF) Persistence Manager.
 *
 * Provides durability across restarts:
 * 1. Appends every successful state-mutating command (SET, DEL, INCR, DECR)
 *    to disk in real time.
 * 2. On server startup, replays the AOF log sequentially to reconstruct
 *    the exact in-memory state before accepting incoming client connections.
 */
public class PersistenceManager {

    private final File aofFile;
    private final boolean enabled;
    private BufferedWriter writer;

    public PersistenceManager(File aofFile, boolean enabled) {
        this.aofFile = aofFile;
        this.enabled = enabled;
        if (enabled) {
            initWriter();
        }
    }

    public PersistenceManager(String filename, boolean enabled) {
        this(new File(filename), enabled);
    }

    public PersistenceManager(String filename) {
        this(new File(filename), true);
    }

    private synchronized void initWriter() {
        try {
            File parent = aofFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(aofFile, true), StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.server("Failed to initialize AOF writer: " + e.getMessage());
        }
    }

    /**
     * Appends a mutating command to the AOF log and flushes to disk.
     */
    public synchronized void append(String commandLine) {
        if (!enabled || writer == null || commandLine == null) {
            return;
        }
        try {
            writer.write(commandLine);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            Log.server("AOF write failed: " + e.getMessage());
        }
    }

    /**
     * Replays past commands from the AOF file to rebuild the Database.
     * @return the number of commands successfully replayed.
     */
    public int replay(Database db) {
        if (!aofFile.exists() || aofFile.length() == 0) {
            return 0;
        }

        // Executor with persistence disabled during replay to prevent re-logging
        CommandExecutor replayExecutor = new CommandExecutor(db, null);
        int replayedCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(aofFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Command cmd = CommandParser.parse(line);
                if (cmd != null) {
                    replayExecutor.execute(cmd);
                    replayedCount++;
                }
            }
            Log.server("AOF replay complete: restored " + replayedCount + " command(s) from " + aofFile.getName());
        } catch (IOException e) {
            Log.server("Error during AOF replay: " + e.getMessage());
        }

        return replayedCount;
    }

    /** Flushes and closes the AOF log file on server shutdown. */
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            } finally {
                writer = null;
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public File getAofFile() {
        return aofFile;
    }
}
