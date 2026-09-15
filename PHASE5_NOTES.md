# Phase 5 Notes — Load Testing and Proof

## What the test does

A single k6 script (`load-test/loadtest.js`) runs four scenarios concurrently
against the full stack (2 app instances behind nginx, shared Redis, Postgres):

- **Baseline** — ramps 50 -> 500 req/s across the three dummy endpoints, using a
  pool of ~1000 keys so baseline traffic never trips the limit itself.
- **Attacker 1 (hammer)** — one key sending 400 requests past the limit.
- **Attacker 2 (enumeration)** — one key walking sequential resource IDs.
- **Attacker 3 (credential stuffing)** — one key with an all-failing login stream.

The run uses `ratelimiter.capacity=100` so the abuse rules are the binding
constraint (at the default of 10 the limiter throttles the enumeration attacker
before it reaches the 20-distinct-ID threshold). The committed default remains 10.

## Results

Latency, at ~634 req/s sustained throughput (measured under full mixed load):

```
through filter  (filtered_latency): p50=1.7ms  p95=3.6ms  p99=7.5ms
control/bypass  (control_latency) : p50=1.4ms  p95=2.4ms  p99=4.0ms
```

The rate-limit + detection path adds roughly **1–3.5ms** over an equivalent
request that bypasses the filter. The control is `/api/dashboard/volume?buckets=1`,
which uses the same framework but skips the rate-limit filter.

**All three attacker profiles were flagged and escalated** (from `abuse_events`):

```
      rule_name       | action_taken | count
----------------------+--------------+-------
 credential_stuffing  | LOG_ONLY     |    49
 credential_stuffing  | TEMP_BLOCK   |    48
 endpoint_enumeration | LOG_ONLY     |    28
 endpoint_enumeration | TEMP_BLOCK   |    27
```

Both detection rules fired and climbed LOG_ONLY -> TEMP_BLOCK under sustained
load. The hammer key is caught by rate limiting (and velocity).

## Rate-limit correctness — and a debugging note worth keeping

The exact per-window guarantee is proven by an **isolated single-key burst**:
400 sequential requests to one key at capacity 100 gave exactly

```
200: 100    429: 300
```

with the Redis counter reading exactly `100`. Zero requests exceeded the limit.
This matches the rigorous multi-instance proof in Phase 2's
`DistributedRateLimitIT`.

**Why correctness is proven in isolation rather than from the concurrent k6 run:**
the first version of this load test asserted `hammer_allowed <= capacity` as a k6
threshold, and it "failed" — the concurrent run reported far more than `capacity`
allowed on the hammer key. Before trusting that, I verified the limiter three
ways:
- the Lua script under 30 concurrent Redis clients caps at exactly 100;
- a sequential 250-request curl burst gives a clean 100/150 split, counter = 100;
- an isolated 400-request burst gives 100/300, counter = 100.

All three show the limiter is correct. The discrepancy was in the **measurement**:
under concurrent mixed load, k6's `constant-arrival-rate` executor competing with
the baseline ramp does not produce a clean per-window count, so its aggregate
`allowed` figure is not a valid correctness oracle. The fix was to remove the
misleading threshold and prove correctness with a controlled isolated burst
instead. The k6 run's hammer line is retained as *behavior under load*, not as a
correctness assertion.

Lesson: a load test measures behavior under contention; it is not automatically a
correctness proof. Correctness needs a controlled test where the thing being
measured isn't confounded by the load itself.

## Running it

```
# with ratelimiter.capacity temporarily set to 100:
docker compose up --build -d
k6 run -e CAPACITY=100 load-test/loadtest.js          # latency + attacker flagging
# correctness proof (isolated):
1..400 | %{ curl.exe -s -o NUL -w "%{http_code}`n" -H "X-API-Key: proof" http://localhost:8080/api/products } | Group-Object
```
See `load-test/summary.txt` for the captured k6 run.