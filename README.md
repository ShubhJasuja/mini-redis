# MiniRedis

A tiny in-memory key–value database served over TCP, written from scratch in
plain Java. No Redis, no Spring, no Netty, no database, no third-party
libraries — just `ServerSocket`, `Socket`, threads, and the JDK.

It speaks a simple line-based text protocol, holds data in memory, expires keys
on a TTL, serves many clients at once, and shuts down cleanly.

```
MiniRedis> SET name Shubh
OK
MiniRedis> GET name
Shubh
MiniRedis> SET name Shubh EX 10
OK
MiniRedis> GET name          (after 10 seconds)
(nil)
```

## Running it

Two terminals. Start the server in the first:

```bash
javac -d . *.java
java MiniRedis
```

Then a client in the second:

```bash
java MiniRedisClient
```

Both take optional arguments: `java MiniRedis 6380` to use another port, and
`java MiniRedisClient 6380` (or `java MiniRedisClient <host> <port>`) to reach
it. On Windows, `server.bat` and `client.bat` do the same thing including the
JDK setup.

Default port is **6379**. Stop the server with **Ctrl-C**, or send `SHUTDOWN`
from any client.

## Commands

| Command | Reply | Notes |
|---|---|---|
| `PING` / `PING <msg>` | `PONG` / `<msg>` | liveness check |
| `SET <key> <value>` | `OK` | overwrites, no expiry |
| `SET <key> <value> EX <seconds>` | `OK` | expires after N seconds |
| `SET <key> <value> PX <millis>` | `OK` | same, in milliseconds |
| `GET <key>` | value or `(nil)` | `(nil)` if missing or expired |
| `DEL <key>` | `1` / `0` | 1 if it existed |
| `EXISTS <key>` | `1` / `0` | |
| `KEYS` | space-separated, or `(empty)` | live keys only |
| `INCR <key>` / `DECR <key>` | the new number | starts at 0; atomic |
| `TTL <key>` | seconds, `-1`, or `-2` | -1 = no expiry, -2 = missing |
| `QUIT` | `BYE` | closes this connection |
| `SHUTDOWN` | `OK` | stops the whole server gracefully |

Errors come back as `(error) <reason>`, e.g.
`(error) value is not an integer or out of range`.

Values cannot contain spaces — the protocol splits on whitespace, and quoting
was deliberately left out to keep the parser honest and small.

## Tests

```bash
javac -d . *.java test/*.java
java RunAllTests
```

**117 assertions across 4 suites**, exit code 0 when everything passes
(`test.bat` on Windows). No JUnit — `TestKit` is a ~70-line harness, because
"Java only" should mean it.

| Suite | Covers |
|---|---|
| `CommandParserTest` | tokenizing, case folding, whitespace, blank/null lines |
| `DatabaseTest` | CRUD, INCR/DECR, overflow, expiry, **200 000 concurrent increments** |
| `CommandExecutorTest` | every command's exact wire reply, arity and validation errors |
| `ShutdownManagerTest` | socket registry, grace period, forced close of blocked handlers |

## How it fits together

```
MiniRedis ──── accept loop; one thread per client (cached pool)
   │
   ├── ClientHandler ──── one connection: read line → reply line
   │        │
   │        └── CommandParser ──→ Command ──→ CommandExecutor ──→ Reply
   │                                                  │
   ├── Database ─────────────────────────────────────┘   ConcurrentHashMap<String, Entry>
   │        └── Entry (value + absolute deadline), TimeSource (injectable clock)
   │
   ├── ExpiryReaper ──── daemon thread; sweeps expired keys every 100 ms
   │
   └── ShutdownManager ── Ctrl-C and SHUTDOWN both funnel here
```

**Class by class:**

- `MiniRedis` — binds the port, accepts connections, hands each to the pool.
- `ClientHandler` — owns one conversation. Renames its thread to `client-N`
  while it runs, because a pooled thread serves many clients over its life.
- `CommandParser` — pure function, `String` → `Command`. No sockets, no state.
- `CommandExecutor` — per-command knowledge: arity, validation, what it does.
- `Database` — the store. Thread-safe via `ConcurrentHashMap`; `INCR` uses
  `compute()` so read-modify-write is atomic.
- `Entry` / `TimeSource` — a value plus an absolute deadline, read off an
  injectable monotonic clock (which is what makes expiry testable in
  microseconds rather than by sleeping).
- `ExpiryReaper` — active expiration, so keys nobody reads can't leak.
- `ShutdownManager` — the ordered stop sequence.
- `Log` — `HH:mm:ss.SSS [TAG] [thread] message`.

## Design notes

**Thread per connection.** Each client gets a pool thread that blocks on
`readLine()`. Simple and easy to reason about; it trades memory per connection
for clarity. This is precisely the model Netty exists to replace, and seeing
where it strains is part of the point.

**Two expiration mechanisms, because each covers the other's blind spot.**
Lazy expiry (checked on every read) guarantees you can never *observe* an
expired key, but never cleans a key nobody reads. The reaper thread guarantees
memory doesn't leak, but a key can briefly linger between sweeps. Together
they're airtight — the same design real Redis uses.

**Absolute deadlines on a monotonic clock.** An entry stores *when* it dies, not
"seconds remaining", so nothing has to tick. `System.nanoTime()` rather than
wall-clock time, so an NTP jump or DST change can't resurrect a dead key.

**Atomicity is not thread-safety.** A `ConcurrentHashMap` makes each *operation*
safe, but `get` then `put` is still two operations — under load, increments
vanish. `compute()` makes the whole read-modify-write atomic. The test asserts
exactly 200 000 across 8 threads; a naive version loses ~75 % of them.

**Graceful shutdown is bounded, not infinite.** Stop accepting → stop the reaper
→ let in-flight commands finish → wait 1 second → then force. The forcing step
matters: a handler blocked in `readLine()` cannot be interrupted, so every live
socket is tracked and closed, which is what makes those reads return.

## Layout

```
MiniRedis.java  ClientHandler.java  MiniRedisClient.java
CommandParser.java  Command.java  CommandExecutor.java  Reply.java
Database.java  Entry.java  TimeSource.java  ExpiryReaper.java
ShutdownManager.java  Log.java
test/    the four suites, TestKit, RunAllTests, ManualClock
dev/     scaffolding used to demonstrate things during development, not part
         of the deliverable (Probe, RaceDemo, RudeServer, ShutdownProbe)
```

## Limitations

Deliberate, to keep the project small and readable:

- values can't contain spaces (no quoting in the protocol)
- data is memory-only; nothing survives a restart
- no auth, no key patterns for `KEYS`, no data types beyond strings
- one thread per connection, so many thousands of idle clients would be costly
