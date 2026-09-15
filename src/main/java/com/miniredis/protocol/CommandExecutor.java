package com.miniredis.protocol;

import com.miniredis.core.Database;
import com.miniredis.core.PersistenceManager;

import java.util.List;
import java.util.Locale;

/**
 * Turns a parsed {@link Command} into a reply string, running it against the
 * shared {@link Database}.
 *
 * If a {@link PersistenceManager} is attached, successful mutating operations
 * (SET, DEL, INCR, DECR) are written to the Append-Only File (AOF).
 */
public class CommandExecutor {

    private final Database db;
    private final PersistenceManager persistence;

    public CommandExecutor(Database db) {
        this(db, null);
    }

    public CommandExecutor(Database db, PersistenceManager persistence) {
        this.db = db;
        this.persistence = persistence;
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
            return cmd.arg(0);
        }
        return wrongArgs("ping");
    }

    private String set(Command cmd) {
        int n = cmd.argCount();
        if (n != 2 && n != 4) {
            return wrongArgs("set");
        }
        String key = cmd.arg(0);
        String value = cmd.arg(1);

        if (n == 2) {
            db.set(key, value);
            logAof("SET " + key + " " + value);
            return Reply.OK;
        }

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
        logAof("SET " + key + " " + value + " " + option + " " + amount);
        return Reply.OK;
    }

    private String get(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("get");
        }
        return Reply.value(db.get(cmd.arg(0)));
    }

    private String del(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("del");
        }
        String key = cmd.arg(0);
        boolean deleted = db.delete(key);
        if (deleted) {
            logAof("DEL " + key);
        }
        return Reply.integer(deleted ? 1 : 0);
    }

    private String exists(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("exists");
        }
        return Reply.integer(db.exists(cmd.arg(0)) ? 1 : 0);
    }

    private String incrBy(Command cmd, long delta) {
        if (cmd.argCount() != 1) {
            return wrongArgs(delta > 0 ? "incr" : "decr");
        }
        String key = cmd.arg(0);
        try {
            long result = db.incrBy(key, delta);
            logAof((delta > 0 ? "INCR " : "DECR ") + key);
            return Reply.integer(result);
        } catch (NumberFormatException e) {
            return Reply.error("value is not an integer or out of range");
        } catch (ArithmeticException e) {
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
        return String.join(" ", ks);
    }

    private String ttl(Command cmd) {
        if (cmd.argCount() != 1) {
            return wrongArgs("ttl");
        }
        long ms = db.ttlMillis(cmd.arg(0));
        if (ms < 0) {
            return Reply.integer(ms);
        }
        return Reply.integer((ms + 999) / 1000);
    }

    private void logAof(String commandLine) {
        if (persistence != null) {
            persistence.append(commandLine);
        }
    }

    private static String wrongArgs(String commandName) {
        return Reply.error("wrong number of arguments for '"
                + commandName.toLowerCase(Locale.ROOT) + "'");
    }
}
