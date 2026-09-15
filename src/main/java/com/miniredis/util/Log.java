package com.miniredis.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * All server output goes through here.
 *
 * Provides thread-safe, structured timestamped logs:
 * HH:mm:ss.SSS [TAG] [thread] message
 */
public final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    /** Server lifecycle: startup, connects, disconnects, shutdown. */
    public static void server(String message) {
        line("SERVER", message);
    }

    /** One line exactly as a client sent it. */
    public static void command(String message) {
        line("COMMAND", message);
    }

    private static void line(String tag, String message) {
        System.out.println(LocalTime.now().format(TIME)
                + " [" + tag + "] [" + Thread.currentThread().getName() + "] " + message);
    }
}
