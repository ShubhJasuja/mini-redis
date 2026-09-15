package com.miniredis.protocol;

import java.util.List;

/**
 * One parsed command: an uppercase name plus its argument tokens.
 *
 * A record because a command is pure immutable data - no behaviour, no identity,
 * just "what did the client ask for". The compact constructor copies the args
 * into an unmodifiable list so a Command can be passed to any thread and can
 * never be mutated after parsing.
 */
public record Command(String name, List<String> args) {

    public Command(String name, List<String> args) {
        this.name = name;
        this.args = List.copyOf(args);   // defensive, immutable copy
    }

    /** Number of arguments after the command word. */
    public int argCount() {
        return args.size();
    }

    /** The i-th argument (0-based). Caller is responsible for the bounds. */
    public String arg(int i) {
        return args.get(i);
    }

    /** True if this command's name equals the given (already-uppercase) name. */
    public boolean is(String commandName) {
        return name.equals(commandName);
    }
}
