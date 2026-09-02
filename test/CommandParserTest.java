import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link CommandParser} - requirement 10, the parser half.
 *
 * No server, no socket, no Database: parsing is a pure function, so these tests
 * just call CommandParser.parse(...) and check what comes back. Run with:
 *     java CommandParserTest
 * Exit code is 0 if everything passed, 1 otherwise. run() is separate from
 * main() so RunAllTests can execute this suite without System.exit ending the
 * JVM before the other suites get a turn.
 */
public class CommandParserTest {

    public static void main(String[] args) {
        System.exit(run().exitCode());
    }

    public static TestKit run() {
        TestKit t = new TestKit("CommandParserTest");

        // --- the happy path from the spec ---
        Command set = CommandParser.parse("SET name Shubh");
        t.eq("SET: name is uppercase command", "SET", set.name());
        t.eq("SET: two args captured", List.of("name", "Shubh"), set.args());
        t.eq("SET: argCount is 2", 2, set.argCount());
        t.eq("SET: arg(0) is the key", "name", set.arg(0));
        t.eq("SET: arg(1) is the value", "Shubh", set.arg(1));

        // --- command names are case-insensitive, normalized to uppercase ---
        t.eq("lowercase 'get' -> GET", "GET", CommandParser.parse("get name").name());
        t.eq("mixed 'gEt' -> GET", "GET", CommandParser.parse("gEt name").name());
        t.eq("PING has no args", 0, CommandParser.parse("PING").argCount());

        // --- arguments keep their original case (only the command is folded) ---
        t.eq("value case preserved", "Shubh", CommandParser.parse("SET k Shubh").arg(1));

        // --- whitespace handling: leading/trailing trimmed, inner runs collapsed ---
        Command padded = CommandParser.parse("   PING   ");
        t.eq("surrounding spaces trimmed", "PING", padded.name());
        t.eq("surrounding spaces -> no args", 0, padded.argCount());

        Command spaced = CommandParser.parse("SET   name     Shubh");
        t.eq("multiple spaces collapse", List.of("name", "Shubh"), spaced.args());

        Command tabbed = CommandParser.parse("SET\tname\tShubh");
        t.eq("tabs split like spaces", List.of("name", "Shubh"), tabbed.args());

        // --- the EX modifier is just more tokens to the parser (executor cares) ---
        Command withEx = CommandParser.parse("SET name Shubh EX 10");
        t.eq("EX line -> 4 args", 4, withEx.argCount());
        t.eq("EX line preserved verbatim", List.of("name", "Shubh", "EX", "10"), withEx.args());

        // --- non-commands: blank, whitespace-only, and null all yield null ---
        t.isNull("empty string is not a command", CommandParser.parse(""));
        t.isNull("whitespace-only is not a command", CommandParser.parse("   \t  "));
        t.isNull("null line is not a command", CommandParser.parse(null));

        // --- args list is immutable (safe to share across threads) ---
        boolean immutable;
        try {
            CommandParser.parse("KEYS extra").args().add("boom");
            immutable = false;
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        t.isTrue("args list is unmodifiable", immutable);

        // --- a single unknown word still parses (validation is the executor's job) ---
        Command unknown = CommandParser.parse("FLUSHALL");
        t.eq("unknown command still parses to a name", "FLUSHALL", unknown.name());
        t.eq("unknown command has no args", Collections.emptyList(), unknown.args());

        t.done();
        return t;
    }
}
