# MiniRedis

[![Build & Test](https://github.com/ShubhJasuja/mini-redis/actions/workflows/ci.yml/badge.svg)](https://github.com/ShubhJasuja/mini-redis/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)
![Dependencies](https://img.shields.io/badge/Dependencies-Zero-green.svg)
![Tests](https://img.shields.io/badge/Tests-125%20Passing-brightgreen.svg)

A tiny, high-performance in-memory key–value database served over TCP with **Append-Only File (AOF) durability**, written from scratch in pure Java. 

No Spring, no Netty, no third-party libraries — built strictly with `ServerSocket`, raw `Socket` I/O, threads, and the Java 17 Standard Library.

```
MiniRedis> SET user Alice
OK
MiniRedis> INCR visits
1
MiniRedis> SET session token_xyz EX 10
OK
MiniRedis> GET session           (after 10 seconds)
(nil)
```

---

## Key Features

* **TCP Wire Protocol**: Request/reply line-based text protocol served over custom port (default `6379`).
* **High Concurrency**: Thread-per-connection architecture backed by an unbounded cached thread pool (`Executors.newCachedThreadPool`).
* **Atomic Arithmetic**: Atomic `INCR` / `DECR` operations powered by `ConcurrentHashMap.compute()`, verified under stress tests of 200,000 concurrent writes across 8 worker threads.
* **Dual Expiration Strategy (TTL)**: 
  * **Lazy (Passive)**: Expired keys are detected and pruned on-demand during read operations.
  * **Active (Background)**: A dedicated daemon thread (`ExpiryReaper`) periodically sweeps memory to prevent leaks from dead, unread keys.
* **Monotonic Time**: Key expiration deadlines are computed using `System.nanoTime()` rather than wall-clock time, protecting against NTP adjustments and leap seconds.
* **AOF Durability**: Mutating commands are continuously written to an Append-Only File (`appendonly.aof`). On reboot, the log is sequentially replayed to reconstruct the in-memory state.
* **Graceful Socket Termination**: Active client sockets are tracked in a thread-safe registry. Shutdown gives connections a 1-second grace period before force-closing sockets to unblock threads parked in socket reads.
* **Zero External Dependencies**: Production server has zero third-party JARs.

---

## Architecture

```
MiniRedis ─── Accept loop (ServerSocket:6379)
   │
   ├── Cached Thread Pool ─── Assigns one thread per client
   │     │
   │     └── ClientHandler ─── Reads TCP socket stream (client-N)
   │           │
   │           ├── CommandParser ──→ Command (immutable record)
   │           │
   │           └── CommandExecutor ──→ Reply
   │                 ├── Database (ConcurrentHashMap<String, Entry>)
   │                 └── PersistenceManager (Append-Only File: appendonly.aof)
   │
   ├── ExpiryReaper ─── Daemon thread sweeping expired keys every 100ms
   │
   └── ShutdownManager ─── Coordinates graceful stop, socket registry, & pool unwinding
```

---

## Quick Start

### 1. Build & Run with Batch Scripts (Windows)

Start the server:
```cmd
server.bat
```

In a second terminal, start the interactive CLI:
```cmd
client.bat
```

Run the full test suite (125 assertions across 5 suites):
```cmd
test.bat
```

### 2. Build & Run with Maven

Compile and test:
```bash
mvn clean test
```

Package executable JAR:
```bash
mvn package
```

Run server:
```bash
java -jar target/mini-redis-1.0.0.jar
```

---

## Supported Commands

| Command | Wire Reply | Description |
|---|---|---|
| `PING` / `PING <msg>` | `PONG` / `<msg>` | Liveness check / echo message |
| `SET <key> <val>` | `OK` | Store key-value pair (persistent) |
| `SET <key> <val> EX <sec>` | `OK` | Set with expiration in seconds |
| `SET <key> <val> PX <ms>` | `OK` | Set with expiration in milliseconds |
| `GET <key>` | `<val>` or `(nil)` | Retrieve value (`(nil)` if absent or expired) |
| `DEL <key>` | `1` / `0` | Delete key (`1` if existed, `0` if absent) |
| `EXISTS <key>` | `1` / `0` | Check key presence |
| `INCR <key>` / `DECR <key>` | integer | Atomic increment/decrement (starts at 0 if missing) |
| `KEYS` | space-separated keys | List all live keys |
| `TTL <key>` | seconds, `-1`, `-2` | Remaining TTL (`-1` = persistent, `-2` = missing/expired) |
| `QUIT` | `BYE` | Close client connection |
| `SHUTDOWN` | `OK` | Gracefully terminate entire server |

---

## Project Structure

```
.
├── pom.xml                               # Maven project configuration
├── .github/workflows/ci.yml             # GitHub Actions CI workflow (Ubuntu & Windows)
├── server.bat / client.bat / test.bat   # Windows one-click scripts
└── src/
    ├── main/java/com/miniredis/
    │   ├── MiniRedis.java               # Main entry point & accept loop
    │   ├── MiniRedisClient.java         # Interactive REPL client
    │   ├── core/
    │   │   ├── Database.java            # Concurrent in-memory store
    │   │   ├── Entry.java               # Immutable value & absolute deadline
    │   │   ├── TimeSource.java          # Monotonic time abstraction
    │   │   ├── ExpiryReaper.java        # Active background sweeper
    │   │   └── PersistenceManager.java  # Append-Only File durability engine
    │   ├── net/
    │   │   ├── ClientHandler.java       # Per-connection socket session
    │   │   └── ShutdownManager.java     # Graceful teardown & socket registry
    │   ├── protocol/
    │   │   ├── Command.java             # Parsed command record
    │   │   ├── CommandParser.java       # Pure whitespace tokenizer
    │   │   ├── CommandExecutor.java     # Command dispatcher & arity validator
    │   │   └── Reply.java               # Standard wire reply constants
    │   └── util/
    │       └── Log.java                 # Structured timestamped logger
    └── test/java/com/miniredis/
        ├── TestKit.java                 # Zero-dependency assertion harness
        ├── ManualClock.java             # Deterministic clock for TTL testing
        ├── CommandParserTest.java       # Tokenizer test suite
        ├── DatabaseTest.java            # Concurrency (200k incrs) & store tests
        ├── CommandExecutorTest.java     # Protocol & error validation tests
        ├── ShutdownManagerTest.java     # Graceful shutdown & socket force-close tests
        ├── PersistenceTest.java         # AOF recovery & replay tests
        ├── RunAllTests.java             # Standalone test runner
        └── MiniRedisJupiterTest.java    # JUnit 5 adapter for Maven & CI
```

---

## Engineering Deep-Dive (Interview Notes)

1. **Why `ConcurrentHashMap.compute()` for `INCR`?**
   A standard `map.get()` followed by `map.put()` is not atomic across multiple threads (classic check-then-act race condition). Under concurrent traffic, updates would be overwritten and lost. `compute()` evaluates the update atomically within the map's bin lock.

2. **Why `System.nanoTime()` instead of `System.currentTimeMillis()`?**
   Wall-clock time can skew or jump backwards during NTP time synchronizations. A monotonic clock only progresses forward, guaranteeing reliable deadline calculations.

3. **Why two expiration mechanisms?**
   * *Lazy expiration* ensures clients never observe a stale key, but dead keys that are never queried would sit in RAM forever.
   * *Active reaping* cleans out dead, unqueried keys in the background.

4. **Solving the Blocked Socket Read Problem on Shutdown:**
   Java cannot interrupt a thread blocked on traditional blocking socket I/O via `Thread.interrupt()`. `ShutdownManager` tracks open sockets in a thread-safe registry (`liveClients`) and explicitly closes them after a 1-second grace period, forcing blocked `readLine()` calls to throw and unwind the worker threads.
