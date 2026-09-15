# Phase 3 Notes — Rule-Based Abuse Detection

## The four rules, each defensible in one sentence

1. **Velocity anomaly** — flag a key whose request count in the current time
   bucket exceeds its own recent average by more than N standard deviations, so
   a naturally busy key isn't punished but a key spiking against its own history
   is. (A small stddev floor stops a perfectly steady key from flapping.)
2. **Endpoint enumeration** — flag a key that touches more than T *distinct*
   resource IDs in a short window (catalog scraping), while hammering one ID
   many times does not trip it.
3. **Credential stuffing** — flag a key whose fraction of 401/403 responses
   exceeds R over at least M attempts, so credential guessing trips it but a
   couple of honest typos don't.
4. **Cost-weighted limiting** — charge expensive endpoints more tokens
   (GET=1, POST /orders=5) so a few costly calls drain the budget as fast as
   many cheap ones.

## Design decisions

**Rules 1–3 implement `AbuseRule`; rule 4 does not.** Cost-weighting doesn't
detect or flag — it prices endpoints and feeds the existing rate limiter via
`tryAcquire(key, cost)`. It's an `EndpointCostResolver`, not a detector, so it
never writes to `abuse_events`. Forcing it into the rule interface would
misrepresent what it does.

**No giant if/else.** Each rule is its own `@Component`. `AbuseDetectionService`
receives every `AbuseRule` bean Spring finds and iterates. Adding a rule is
adding a class — nothing else changes.

**Detection state is per-instance; bans are shared.** Rate-limit *enforcement*
must be globally exact, so it lives in Redis with atomic Lua (Phase 2). Abuse
*detection* is heuristic and advisory, so each instance keeps its own rolling
windows in memory — which keeps every rule trivial to read. But when any
instance detects abuse it increments a **shared** Redis violation counter and
sets a **shared** ban, so bans are honored across all instances. The clear
production next step is centralizing the detection windows in Redis too; the
codebase is already set up for it.

**Escalation.** A per-key Redis counter (rolling TTL window) drives the action:
1st trigger = LOG_ONLY, >= temp-block threshold = TEMP_BLOCK (short Redis ban),
>= long-ban threshold = LONG_BAN (long Redis ban). All thresholds and durations
are configurable in application.properties.

**Added endpoint.** `POST /api/login` was added so the credential-stuffing rule
has real 401s to observe (the three original dummy endpoints never return
401/403). It accepts one hardcoded credential; everything else is a 401.

## Audit log

Every triggered rule writes a row to the Postgres `abuse_events` table
(`event_time`, `api_key`, `rule_name`, `action_taken`) via a Flyway migration
(`V1__create_abuse_events.sql`). Spring Boot 4.x needs the Flyway *starter*
(not just `flyway-core`) plus `flyway-database-postgresql`. Verified on startup:

```
Successfully applied 1 migration to schema "public", now at version v1
```

## Verification

**Credential-stuffing rule + escalation.** 25 failed logins on a fresh key,
with the rate limit temporarily raised so the rule could trigger repeatedly:

```
401 x11   (attempts accumulating; rule fires once past the min-attempts floor)
403 x14   (ban active — requests rejected before reaching the endpoint)
```

Audit log for that key:
```
 api_key   |      rule_name      | action_taken
-----------+---------------------+--------------
 stuffer-3 | credential_stuffing | LOG_ONLY
 stuffer-3 | credential_stuffing | TEMP_BLOCK
```

The escalation ladder climbed LOG_ONLY -> TEMP_BLOCK as designed.

**Why it stopped at TEMP_BLOCK (not LONG_BAN):** the ban is self-limiting. Once
TEMP_BLOCK sets a ban, subsequent requests are rejected with 403 at the ban
check *before* reaching the endpoint, so they produce no response for the
credential-stuffing rule to observe. With no new triggers, the counter stops
climbing and doesn't reach the long-ban threshold until the temp block expires
and a fresh burst arrives. This is intended behavior: an active block stops
generating the very events that would escalate it further.

**Rate-limit vs. detection ordering (observed at the default limit of 10).**
With the production limit, a run of 15 failed logins produced ten 401s, then
five 429s, and a single `LOG_ONLY` abuse event. The rate-limit check runs
*before* the request is forwarded, and the credential-stuffing rule runs
*after* the response — so once a key exhausts its 10-request budget, further
requests are rate-limited (429) before the endpoint replies, and the detection
rule never sees them. The two defenses coexist: the rate limiter caps raw
volume, and abuse detection catches patterns within the allowed volume. To
demonstrate escalation in isolation, the limit was temporarily raised; the
committed default remains 10.

## Rule logic (unit-verified before integration)

- Velocity: steady traffic does **not** trip; a sudden burst **does**.
- Enumeration: many distinct IDs **does** trip; hammering one ID **does not**.
- Credential stuffing: a high failure ratio over enough attempts **does** trip;
  a few failures below the minimum **do not**.