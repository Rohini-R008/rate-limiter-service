# Phase 4 Notes — Minimal Dashboard

## What it shows

A single read-only page that polls the service every 3 seconds:
- **Request volume over time** — a bar chart of total requests per time bucket.
- **Recent abuse events** — the audit table from Postgres: time, key, which rule
  fired, and the escalation action taken.
- **Current rate per key** and **active bans** — live from Redis.

## Endpoints

All under `/api/dashboard`, read-only, no auth:
- `GET /rates` — current used/limit per active key (Redis SCAN of `rl:tb:*`).
- `GET /abuse-events?limit=N` — recent rows from `abuse_events` (Postgres).
- `GET /bans` — active bans with TTL (Redis SCAN of `abuse:ban:*`).
- `GET /volume?buckets=N` — requests per time bucket for the chart.

## Design decisions

**Four endpoints, not the three named.** The chart needs time-series data that
the three named endpoints (rates, events, bans) don't provide, so `/volume` is
its data source. Volume is tracked by INCRing a per-time-bucket Redis counter on
each request; reading needs no key scan because bucket names are deterministic
(compute the last N, MGET them).

**Hand-rolled SVG chart, no charting library.** AGENTS.md asks for plain React
with no heavy component library, so the volume chart is a small SVG bar chart
component rather than a recharts/chart.js dependency.

**Dashboard bypasses the rate-limit filter.** `shouldNotFilter` skips
`/api/dashboard/**` so the dashboard's own polling doesn't consume rate budget
or need an API key.

**Redis reads use SCAN, not KEYS.** KEYS blocks Redis while it walks the whole
keyspace; SCAN is cursor-based. At larger scale you'd keep an index set of
active keys instead of scanning.

**React runs separately for now.** CORS is enabled only on `/api/dashboard/**`
for the Vite dev server (localhost:5173). Wiring the frontend into
docker-compose is Phase 6's job, so it isn't pulled forward here.

## Running it

With the stack up (`docker compose up --build -d`):
```
cd dashboard
npm install
npm run dev
```
Open http://localhost:5173. Generate traffic against localhost:8080 and the
chart, tables, and bans update live.

## Verification

After generating traffic (12 requests to `/api/products` and 12 failed logins),
the four endpoints returned live data:

`GET /rates` — the key that hit its limit, with a live TTL:
```json
[{"apiKey":"dash-stuffer","used":10,"capacity":10,"ttlSeconds":40}]
```

`GET /abuse-events?limit=5` — audit rows newest-first (ordered by event_time then
id, so same-millisecond escalations stay in sequence):
```json
[{"id":4,"apiKey":"dash-stuffer","ruleName":"credential_stuffing","actionTaken":"LOG_ONLY"},
 {"id":3,"apiKey":"stuffer-3","ruleName":"credential_stuffing","actionTaken":"TEMP_BLOCK"},
 {"id":2,"apiKey":"stuffer-3","ruleName":"credential_stuffing","actionTaken":"LOG_ONLY"},
 {"id":1,"apiKey":"stuffer-2","ruleName":"credential_stuffing","actionTaken":"LOG_ONLY"}]
```

`GET /bans` — empty, correctly: `dash-stuffer` only triggered once (LOG_ONLY)
before the rate limiter capped it, so it never escalated to a ban:
```json
[]
```

`GET /volume?buckets=10` — one populated bucket holding the burst, the rest zero
(null buckets correctly mapped to 0):
```json
[..., {"timestampMs":1789456780000,"requests":12}, ...]
```

The React dashboard renders these as a live bar chart plus the abuse-events
table, refreshing every 3 seconds.

The running dashboard is captured in `dashboard/screenshot-volume.png` (the
request-volume chart) and `dashboard/screenshot-events.png` (the abuse-events
table with all three attacker profiles flagged, escalating LOG_ONLY ->
TEMP_BLOCK, plus active bans counting down). These were generated with the rate
limit temporarily raised so the enumeration rule could reach its distinct-ID
threshold; the committed default remains 10.