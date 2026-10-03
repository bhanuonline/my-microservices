// k6 load test for POST /api/v1/orders
//
// Scenarios:
//   smoke  — 1 VU, 10 iterations. Confirms the service is responsive.
//   load   — ramp 0 → 50 VUs over 1m, hold 2m, ramp down. Soaks the happy path.
//
// Thresholds act as pass/fail gates — exit code 99 if any breach, so CI can
// use k6's exit code directly as a build signal.
//
// Run:
//   k6 run load-tests/k6/orders.js                                    # default: smoke
//   k6 run -e SCENARIO=load load-tests/k6/orders.js                   # full ramp
//   k6 run --out experimental-prometheus-rw -e SCENARIO=load orders.js
//
// Required env (defaults in brackets):
//   BASE_URL     [http://localhost:8080]        gateway base
//   AUTH         [admin:admin123]               basic-auth user:pass OR a bearer token
//   PRODUCT_IDS  [1,2,3]                        comma-separated — rotated per iter

import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, group, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const AUTH       = __ENV.AUTH       || 'admin:admin123';
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '1,2,3').split(',').map(Number);
const SCENARIO   = __ENV.SCENARIO   || 'smoke';

// Scenario catalogue — pick one via SCENARIO env.
const SCENARIOS = {
  smoke: {
    executor: 'per-vu-iterations',
    vus: 1,
    iterations: 10,
    maxDuration: '30s',
    tags: { scenario: 'smoke' },
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m',  target: 50 },  // ramp up
      { duration: '2m',  target: 50 },  // soak
      { duration: '30s', target: 0  },  // ramp down
    ],
    gracefulRampDown: '30s',
    tags: { scenario: 'load' },
  },
  stress: {
    executor: 'ramping-arrival-rate',
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 200,
    stages: [
      { duration: '1m', target: 50  },   // 50 rps
      { duration: '2m', target: 200 },   // 200 rps — expected to find a breaking point
      { duration: '1m', target: 0   },
    ],
    tags: { scenario: 'stress' },
  },
};

export const options = {
  scenarios: { [SCENARIO]: SCENARIOS[SCENARIO] },
  thresholds: {
    // SLOs: fail the run if we miss them. Tune to your actual service.
    http_req_failed:   ['rate<0.01'],                   // <1% errors
    http_req_duration: ['p(95)<800', 'p(99)<1500'],     // p95 < 800ms, p99 < 1.5s
    checks:            ['rate>0.99'],                   // 99%+ of assertions pass
    orders_created:    ['count>0'],                     // sanity: SOME orders landed
  },
  // Attach service tag so Grafana dashboards can filter.
  tags: { service: 'order-service', endpoint: 'POST /api/v1/orders' },
};

// Custom metrics — surfaced on the Grafana dashboard.
const ordersCreated      = new Counter('orders_created');
const idempotencyReplays = new Counter('idempotency_replays');
const orderCreateTime    = new Trend('order_create_duration_ms', true);

export function setup() {
  // Sanity ping — avoid firing load against a cold / absent stack.
  const probe = http.get(`${BASE_URL}/actuator/health`, {
    headers: authHeader(),
    tags: { name: 'health-check' },
  });
  if (probe.status !== 200) {
    throw new Error(`gateway health check failed: ${probe.status} ${probe.body}`);
  }
  console.log(`✓ gateway healthy at ${BASE_URL} — starting ${SCENARIO} scenario`);
  return { startedAt: new Date().toISOString() };
}

export default function () {
  group('POST /api/v1/orders', () => {
    const idemKey = uuidv4();
    const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
    const correlationId = `k6-${__VU}-${__ITER}-${idemKey.slice(0, 8)}`;

    const payload = JSON.stringify({ productId, quantity: 1 + (__ITER % 5) });
    // Avoid object-spread — k6's Babel compiler doesn't accept it.
    const headers = Object.assign({}, authHeader(), {
      'Content-Type': 'application/json',
      'Idempotency-Key': idemKey,
      'X-Correlation-Id': correlationId,
    });
    const params = { headers: headers, tags: { name: 'create-order' } };

    const res = http.post(`${BASE_URL}/api/v1/orders`, payload, params);

    const ok = check(res, {
      'status is 2xx': (r) => r.status >= 200 && r.status < 300,
      'response has id': (r) => {
        try { return !!JSON.parse(r.body).id; } catch (e) { return false; }
      },
    });

    if (ok) {
      ordersCreated.add(1);
      orderCreateTime.add(res.timings.duration);
      if (res.headers['Idempotency-Replay'] === 'true') {
        idempotencyReplays.add(1);
      }
    }
  });

  // Pacing — tiny jitter so we don't synchronize every VU on wall-clock.
  sleep(0.2 + Math.random() * 0.3);
}

export function teardown(data) {
  console.log(`run finished (started ${data.startedAt})`);
}

function authHeader() {
  // Supports two auth modes:
  //   AUTH=user:pass          → HTTP basic
  //   AUTH=<bearer-token>     → Authorization: Bearer <token>
  if (AUTH.includes(':') && !AUTH.includes('.')) {
    return { Authorization: `Basic ${encodeB64(AUTH)}` };
  }
  return { Authorization: `Bearer ${AUTH}` };
}

function encodeB64(s) {
  // k6 has no btoa; use the built-in helper from k6/encoding.
  return encoding.b64encode(s);
}
