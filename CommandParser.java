import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns one raw line of text into a {@link Command}. Pure function: same input,
 * same output, no sockets, no shared state, no side effects. That purity is the
 * whole reason it lives in its own class - it can be unit-tested directly (see
 * CommandParserTest), with no server running.
 *
 * The rules of our simple text protocol:
 *   - One command per line (the newline framing lives in ClientHandler).
 *   - Tokens are separated by any run of whitespace (spaces and/or tabs).
 *   - The first token is the command name, matched case-insensitively.
 *   - A blank or whitespace-only line is not a command -> parse returns null.
 *
 * Known limitation (matches the spec's examples, all single-word values):
 * a value cannot contain spaces, because we split on whitespace. "SET k a b"
 * parses as key=k with TWO value tokens, not key=k value="a b". Real Redis
 * avoids this with a length-prefixed protocol; quoting could be added later.
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

        // Split on one-or-more whitespace chars. Because we trimmed first, there
        // is no empty leading token, and runs of spaces/tabs collapse cleanly.
        String[] tokens = trimmed.split("\\s+");

        // Locale.ROOT, not the default locale: in a Turkish locale "PING"
        // upper/lower-cases differently ("i" <-> dotless/dotted I), which would
        // make command matching depend on the server's regional settings.
        String name = tokens[0].toUpperCase(Locale.ROOT);

        List<String> args = Arrays.asList(tokens).subList(1, tokens.length);
        return new Command(name, args);   // record copies args into an immutable list
    }
}
