package com.miniredis.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns one raw line of text into a {@link Command}. Pure function: same input,
 * same output, no sockets, no shared state, no side effects.
 *
 * Rules:
 *   - One command per line.
 *   - Tokens are separated by any run of whitespace.
 *   - The first token is the command name, matched case-insensitively.
 *   - A blank or whitespace-only line returns null.
 */
public final class CommandParser {

    private CommandParser() {
    }

    /**
     * @param line one line from the client, without its trailing newline
     * @return the parsed command, or {@code null} if the line held no command
     */
    public static Command parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] tokens = trimmed.split("\\s+");
        String name = tokens[0].toUpperCase(Locale.ROOT);

        List<String> args = Arrays.asList(tokens).subList(1, tokens.length);
        return new Command(name, args);
    }
}
