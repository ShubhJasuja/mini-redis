package com.miniredis.protocol;

/**
 * The vocabulary of our wire protocol, in one place.
 *
 * Every reply the server sends is a single line of text. Centralizing the exact
 * strings here means the format is defined once, and the tests can assert
 * against these same constants instead of hard-coding "(nil)" in ten places.
 */
public final class Reply {

    private Reply() {
    }

    public static final String OK = "OK";
    public static final String PONG = "PONG";
    public static final String NIL = "(nil)";
    public static final String EMPTY = "(empty)";

    /** A bare integer reply, e.g. EXISTS -> "1". */
    public static String integer(long n) {
        return Long.toString(n);
    }

    /** A value reply, collapsing a missing value to (nil). */
    public static String value(String v) {
        return v == null ? NIL : v;
    }

    /** An error reply. Prefixed with (error) so a client can tell it apart. */
    public static String error(String message) {
        return "(error) " + message;
    }
}
