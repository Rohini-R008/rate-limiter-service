import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

// ---- config (overridable via -e VAR=value) ----
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RUN = __ENV.RUN_ID || `${Date.now()}`;   // unique suffix -> keys start clean each run
const CAPACITY = parseInt(__ENV.CAPACITY || '100', 10);  // must match ratelimiter.capacity
const SMOKE = __ENV.SMOKE === '1';              // quick dry-run mode

// ---- custom metrics ----
const filteredLatency = new Trend('filtered_latency', true);     // requests THROUGH the rate-limit filter
const controlLatency  = new Trend('control_latency', true);      // dashboard endpoint, BYPASSES the filter
const hammerAllowed   = new Counter('hammer_allowed');           // 200s on the single hammered key
const hammerThrottled = new Counter('hammer_throttled');         // 429s on the hammered key

const BASELINE_KEY_POOL = 1000; // enough keys that baseline never trips the limit itself

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  // Note: no threshold on hammer_allowed. Under concurrent mixed load k6's
  // aggregate count is not a clean per-window oracle; rate-limit correctness is
  // proven separately by an isolated single-key burst and the Phase 2
  // DistributedRateLimitIT. This scenario measures behavior under load, not the
  // exact per-window guarantee.
  scenarios: SMOKE ? {
    baseline:  { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '5s',
                 preAllocatedVUs: 10, maxVUs: 20, exec: 'baseline' },
    hammer:    { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '5s',
                 preAllocatedVUs: 10, maxVUs: 20, exec: 'hammer' },
    enum:      { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '5s',
                 preAllocatedVUs: 10, maxVUs: 20, exec: 'enumerate' },
    cred:      { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '5s',
                 preAllocatedVUs: 10, maxVUs: 20, exec: 'credStuff' },
  } : {
    // Baseline: ramps throughput to find where latency starts to climb.
    baseline: {
      executor: 'ramping-arrival-rate',
      startRate: 50, timeUnit: '1s',
      preAllocatedVUs: 100, maxVUs: 400,
      stages: [
        { target: 100, duration: '15s' },
        { target: 300, duration: '15s' },
        { target: 500, duration: '15s' },
        { target: 500, duration: '15s' },
      ],
      exec: 'baseline',
    },
    // Attacker 1: hammer ONE key past the limit at a high but PACED rate, so all
    // requests land in one 60s window without k6 itself choking on an instantaneous
    // connection burst. 200 req/s for 2s = 400 requests in one window; with the
    // limit at 100, exactly 100 are allowed and ~300 are cleanly 429'd.
    attacker_hammer: {
      executor: 'constant-arrival-rate',
      rate: 200, timeUnit: '1s', duration: '2s',
      preAllocatedVUs: 50, maxVUs: 100,
      exec: 'hammer', startTime: '0s',
    },
    // Attacker 2: enumerate sequential resource IDs on ONE key.
    attacker_enumeration: {
      executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s', duration: '40s',
      preAllocatedVUs: 20, maxVUs: 50, exec: 'enumerate', startTime: '0s',
    },
    // Attacker 3: high ratio of failed logins on ONE key.
    attacker_credstuffing: {
      executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s', duration: '40s',
      preAllocatedVUs: 20, maxVUs: 50, exec: 'credStuff', startTime: '0s',
    },
  },
};

export function baseline() {
  const key = `bench-${RUN}-${exec.scenario.iterationInTest % BASELINE_KEY_POOL}`;
  const headers = { 'X-API-Key': key };
  const r = Math.random();
  let res;
  if (r < 0.5) {
    res = http.get(`${BASE}/api/products`, { headers });
  } else if (r < 0.8) {
    res = http.get(`${BASE}/api/products/${1 + Math.floor(Math.random() * 5)}`, { headers });
  } else {
    res = http.post(`${BASE}/api/orders`, JSON.stringify({ item: 'widget' }),
      { headers: { ...headers, 'Content-Type': 'application/json' } });
  }
  filteredLatency.add(res.timings.duration);

  // control: same framework, but this path bypasses the rate-limit filter
  const ctrl = http.get(`${BASE}/api/dashboard/volume?buckets=1`);
  controlLatency.add(ctrl.timings.duration);
}

export function hammer() {
  const res = http.get(`${BASE}/api/products`, { headers: { 'X-API-Key': `attacker-hammer-${RUN}` } });
  if (res.status === 200) hammerAllowed.add(1);
  else hammerThrottled.add(1);  // 429s AND any connection hiccup count as "not allowed"
}

export function enumerate() {
  const id = exec.scenario.iterationInTest + 1; // globally sequential -> real enumeration
  http.get(`${BASE}/api/products/${id}`, { headers: { 'X-API-Key': `attacker-enum-${RUN}` } });
}

export function credStuff() {
  http.post(`${BASE}/api/login`,
    JSON.stringify({ username: 'demo', password: `wrong-${exec.scenario.iterationInTest}` }),
    { headers: { 'X-API-Key': `attacker-cred-${RUN}`, 'Content-Type': 'application/json' } });
}

// ---- self-contained summary (no external jslib dependency) ----
function line(label, t) {
  if (!t) return `${label}: (no data)`;
  const v = t.values;
  return `${label}: p50=${v['p(50)'].toFixed(1)}ms  p95=${v['p(95)'].toFixed(1)}ms  p99=${v['p(99)'].toFixed(1)}ms  max=${v.max.toFixed(1)}ms`;
}

export function handleSummary(data) {
  const m = data.metrics;
  const allowed = m.hammer_allowed ? m.hammer_allowed.values.count : 0;
  const throttled = m.hammer_throttled ? m.hammer_throttled.values.count : 0;
  const reqs = m.http_reqs ? m.http_reqs.values.count : 0;
  const rps = m.http_reqs ? m.http_reqs.values.rate.toFixed(0) : '0';

  const report = [
    '================ LOAD TEST SUMMARY ================',
    `configured limit (CAPACITY): ${CAPACITY}`,
    `total requests: ${reqs}   overall throughput: ~${rps} req/s`,
    '',
    '--- latency added by the rate-limit + detection path ---',
    line('through filter  (filtered_latency)', m.filtered_latency),
    line('control/bypass  (control_latency) ', m.control_latency),
    '',
    '--- rate-limit behavior under load (single hammered key) ---',
    `allowed (200): ${allowed}   throttled (429): ${throttled}`,
    `(exact per-window enforcement is proven by the isolated burst test and the`,
    ` Phase 2 DistributedRateLimitIT; this line reports behavior under mixed load)`,
    '',
    '--- attacker flagging ---',
    'Verify in Postgres / the dashboard that these keys were flagged:',
    `  attacker-hammer-${RUN}   -> velocity / rate limiting`,
    `  attacker-enum-${RUN}     -> endpoint_enumeration`,
    `  attacker-cred-${RUN}     -> credential_stuffing`,
    '==================================================',
    '',
  ].join('\n');

  const out = {};
  out['stdout'] = report;
  if (!SMOKE) {
    out['load-test/summary.txt'] = report;
    out['load-test/summary.json'] = JSON.stringify(data, null, 2);
  }
  return out;
}