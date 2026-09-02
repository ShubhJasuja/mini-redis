import java.util.List;
import java.util.Locale;

/**
 * Turns a parsed {@link Command} into a reply string, running it against the
 * shared {@link Database}. This is where per-command knowledge lives: how many
 * arguments each command needs, and what it does.
 *
 * Note the split from CommandParser: the parser answered "what tokens did the
 * client type?"; the executor answers "is that a valid GET, and what is the
 * result?". Arity validation belongs here, with the command's meaning - not in
 * the parser, which is meant to stay ignorant of individual commands.
 *
 * Stateless apart from the Database reference, so one instance is safely shared
 * by every client thread; all shared mutable state lives in the Database.
 *
 * Commands implemented: PING, SET, GET, DEL, EXISTS (step 4), INCR/DECR
 * (step 5), SET ... EX/PX and TTL (step 6). SHUTDOWN is deliberately absent:
 * it is server-level, not data-level, and lives in ClientHandler.
 */
public class CommandExecutor {

    private final Database db;

    public CommandExecutor(Database db) {
        this.db = db;
    }

    public String execute(Command cmd) {
        switch (cmd.name()) {
            case "PING":   return ping(cmd);
            case "SET":    return set(cmd);
            case "GET":    return get(cmd);
            case "DEL":    return del(cmd);
            case "EXISTS": return exists(cmd);
            case "INCR":   return incrBy(cmd, +1);
            case "DECR":   return incrBy(cmd, -1);
            case "TTL":    return ttl(cmd);
            case "KEYS":   return keys(cmd);
            default:       return Reply.error("unknown command '" + cmd.name() + "'");
        }
    }

    private String ping(Command cmd) {
        if (cmd.argCount() == 0) {
            return Reply.PONG;
        }
        if (cmd.argCount() == 1) {
            return cmd.arg(0);   // PING <msg> echoes the message, like real Redis
        }
        return wrongArgs("ping");
    }

    private String set(Command cmd) {
        int n = cmd.argCount();
        // Either "SET key value" (2) or "SET key value EX|PX <n>" (4).
        if (n != 2 && n != 4) {
            return wrongArgs("set");
        }
        String key = cmd.arg(0);
        String value = cmd.arg(1);

        if (n == 2) {
            db.set(key, value);
            return Reply.OK;
        }

        // n == 4: an expiry option follows. EX is seconds, PX is milliseconds.
        String option = cmd.arg(2).toUpperCase(Locale.ROOT);
        long amount;
        try {
            amount = Long.parseLong(cmd.arg(3));
        } catch (NumberFormatException e) {
            return Reply.error("value is not an integer or out of range");
        }
        if (amount <= 0) {
            return Reply.error("invalid expire time in 'set'");
        }

        long ttlMillis;
        try {
            switch (option) {
                case "EX": ttlMillis = Math.multiplyExact(amount, 1000L); break;
                case "PX": ttlMillis = amount; break;
                default:   return Reply.error("syntax error");
            }
        } catch (ArithmeticException overflow) {
            return Reply.error("invalid expire time in 'set'");
        }

        db.set(key, value, ttlMillis);
        return Reply.OK;
    }

    private String get(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("get");
        }
        return Reply.value(db.get(cmd.arg(0)));   // value, or (nil) if absent
    }

    private String del(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("del");
        }
        return Reply.integer(db.delete(cmd.arg(0)) ? 1 : 0);
    }

    private String exists(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("exists");
        }
        return Reply.integer(db.exists(cmd.arg(0)) ? 1 : 0);
    }

    /** Shared by INCR (delta +1) and DECR (delta -1). */
    private String incrBy(Command cmd, long delta) {
        if (cmd.argCount() != 1) {
            return wrongArgs(delta > 0 ? "incr" : "decr");
        }
        try {
            return Reply.integer(db.incrBy(cmd.arg(0), delta));
        } catch (NumberFormatException e) {
            // The existing value is not a valid integer (e.g. INCR on "Shubh").
            return Reply.error("value is not an integer or out of range");
        } catch (ArithmeticException e) {
            // Adding delta would overflow a 64-bit long.
            return Reply.error("increment or decrement would overflow");
        }
    }

    private String keys(Command cmd) {
        if (cmd.argCount() != 0) {
            return wrongArgs("keys");
        }
        List<String> ks = db.keys();
        if (ks.isEmpty()) {
            return Reply.EMPTY;
        }
        // One line, space-separated. Safe because our parser forbids spaces in a
        // key (SET splits on whitespace), so keys never contain a space.
        return String.join(" ", ks);
    }

    /** TTL key -> remaining seconds, or -1 (no expiry) / -2 (missing). */
    private String ttl(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("ttl");
        }
        long ms = db.ttlMillis(cmd.arg(0));
        if (ms < 0) {
            return Reply.integer(ms);   // -1 persistent, -2 missing/expired
        }
        // Round remaining millis up to whole seconds so a 9,500ms TTL reads as 10.
        return Reply.integer((ms + 999) / 1000);
    }

    private static String wrongArgs(String commandName) {
        return Reply.error("wrong number of arguments for '"
                + commandName.toLowerCase(Locale.ROOT) + "'");
    }
}
