# Phase 2 Notes — Distributed Correctness (Redis)

## Client choice: Lettuce (not Redisson)

I used Lettuce, the client that ships by default with `spring-boot-starter-data-redis`:
- This phase is about writing and reviewing the atomic Lua script directly. Lettuce, via
  Spring Data Redis's `RedisTemplate.execute(RedisScript, keys, args)`, gives a thin path to
  EVAL without hiding the logic. Redisson would tempt me toward its high-level `RRateLimiter`
  and I'd never see the atomic step.
- It's the Spring Boot default, so there's no extra client to justify.

Redisson's `RRateLimiter` is the off-the-shelf production option. I hand-rolled the Lua
around it deliberately, so I understand exactly what the atomic step does rather than
trusting a library method.

## The race in the naive version

The naive `RedisTokenBucketRateLimiter` used two round trips:
1. GET the counter
2. check the limit in Java
3. INCRBY to consume

Redis executes each command atomically on its own, but nothing ties the GET and the INCRBY
together. Two app instances sharing one Redis both GET the same stale count, both pass the
check in their own JVM, and both INCRBY. The global count sails past the limit. Because the
gap between the two round trips now spans a network hop, the window is far wider than the
single-node case — so the overshoot is much larger.

## Why the Lua script closes it

Redis runs a Lua script to completion atomically: no other command, from any instance,
interleaves with it. Moving GET + check + INCRBY into one EVAL means the check and the
increment can't be split by a concurrent caller. The decision and the mutation are one
indivisible step on the single-threaded Redis server, so every instance sees a consistent
count.

## Sliding window via sorted set

`RedisSlidingWindowRateLimiter` stores each admitted token as a ZSET member scored by its
timestamp. Each call, in one Lua EVAL:
- `ZREMRANGEBYSCORE` drops members older than the window,
- `ZCARD` counts what remains,
- if there's room, `ZADD` adds the new member(s) and `PEXPIRE` refreshes the key's TTL.

This is an exact rolling window shared across all instances, with no fixed-window boundary
to burst across.

## Test output (two app instances behind nginx, one shared Redis, limit 10)

The `DistributedRateLimitIT` fires 60 concurrent requests at the nginx load balancer, which
round-robins them across app1 and app2.

**Naive (GET + INCR, two round trips) — the race lets everything through:**
```
Allowed: 60 (limit=10)
Served by instances: {app2=30, app1=30}
=> FAILURE: Global limit exceeded: 60 > 10
```
All 60 requests were admitted, evenly split across both instances — every request read the
stale count before any INCRBY landed.

**Fixed (single Lua EVAL) — the limit holds globally:**
```
Allowed: 10 (limit=10)
Served by instances: {app2=30, app1=30}
=> BUILD SUCCESS
```
Both instances still handled 30 requests each (traffic is genuinely distributed), but the
shared count held at exactly 10 — the atomic decision lives in Redis, not in either JVM.

The sliding-window variant produced the same result (`Allowed: 10`) when both instances were
switched to `redis-sliding-window`.

## Running it

One command brings up two app instances + Redis + nginx:
```
docker compose up --build -d
mvn test -Dtest=DistributedRateLimitIT
```
Switch both apps' `RATELIMITER_IMPLEMENTATION` env var between `redis-token-bucket` and
`redis-sliding-window` in `docker-compose.yml` to exercise either limiter.