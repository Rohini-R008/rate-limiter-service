# Phase 1 Notes — Single-Node Rate Limiter

## The race in the naive version

The naive `TokenBucketRateLimiter` did this on every call, against a plain `HashMap`:

1. READ:  `remainingTokens.getOrDefault(key, capacity)`
2. CHECK: `current < tokens`
3. WRITE: `remainingTokens.put(key, current - tokens)`

These three steps aren't atomic as a group. Between the READ and the WRITE, other
threads run their own READ against the same key and see the same stale value. If several
threads all read `current = 1` before any of them writes, they all pass the CHECK and all
write back `0` — every decrement but one is silently lost, and the limiter admits more
requests than the limit. This is a time-of-check-to-time-of-use (TOCTOU) race, on top of
`HashMap` not being safe for concurrent access at all.

## Why the fix closes it

The fix changes the map's value type to `AtomicInteger` and replaces the read-check-write
with a compare-and-swap retry loop. `compareAndSet(expected, new)` only succeeds if the
value is still exactly what was just read; if another thread changed it in between, the CAS
fails, the loop re-reads the now-current value and retries. Two threads can still call
`tryAcquire` on the same key at the same instant, but only one can ever win a given
`compareAndSet`, so no decrement is lost.

## Test output (from my own machine, 50 threads, limit 10)

**Naive version — the race fires on essentially every run:**
```
Allowed: 11 (limit=10)   => FAIL
Allowed: 12 (limit=10)   => FAIL
Allowed: 13 (limit=10)   => FAIL
```

**Fixed version — same test, unmodified:**
```
Allowed: 10 (limit=10)
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Known, accepted limitation

`TokenBucketRateLimiter` uses one fixed window. A client can send `capacity` requests just
before a window boundary and another `capacity` just after, getting ~2x the limit in a
short span — the classic fixed-window boundary-burst problem. This is accepted, and is
exactly why `SlidingWindowRateLimiter` exists: it tracks a true rolling window per key.

## SlidingWindowRateLimiter: counter or log-based?

Log-based (a deque of timestamped entries per key), not counter-based. Log-based gives an
exact rolling window with no approximation to defend, at the cost of
O(requests-in-window) memory per key — acceptable since windows are small and per-key
volume is itself bounded by this limiter. Thread-safe from the start: each key's window is
only touched inside `synchronized(w)`, so eviction + admission is atomic per key.