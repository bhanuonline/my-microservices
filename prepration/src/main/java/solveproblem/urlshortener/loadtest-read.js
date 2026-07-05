import http from 'k6/http';
import { check } from 'k6';

// ============================================================
// SINGLE SOURCE OF TRUTH — change ONLY this value to adjust load.
// ============================================================
const TARGET_RATE = 15000;
const NUM_URLS_TO_SEED = 50; // how many real short codes to create before the read test

export const options = {
  scenarios: {
    read_load: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RATE,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: Math.min(TARGET_RATE, 500),
      maxVUs: TARGET_RATE * 2,
    },
  },
};

// setup() runs ONCE, before the load test starts, and is NOT counted
// in the performance metrics. We use it to create real short codes
// so the read test hits data that actually exists.
export function setup() {
  const shortCodes = [];

  console.log(`Seeding ${NUM_URLS_TO_SEED} short URLs before starting read test...`);

  for (let i = 0; i < NUM_URLS_TO_SEED; i++) {
    const payload = JSON.stringify({
      longUrl: `https://example.com/seed/${i}/${Math.random().toString(36).substring(7)}`
    });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const res = http.post('http://localhost:8080/api/urls', payload, params);

    if (res.status === 200) {
      const body = JSON.parse(res.body);
      shortCodes.push(body.shortCode);
    }
  }

  console.log(`Seeded ${shortCodes.length} short codes successfully.`);
  return { shortCodes };
}

// default function receives whatever setup() returned, as `data`
export default function (data) {
  const shortCodes = data.shortCodes;
  const code = shortCodes[Math.floor(Math.random() * shortCodes.length)];

  const res = http.get(`http://localhost:8080/api/urls/${code}`);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}

// Custom, plain-English summary printed at the very end of the test.
export function handleSummary(data) {
  const targetRate = TARGET_RATE;
  const totalRequests = data.metrics.http_reqs.values.count;
  const requestsPerSec = data.metrics.http_reqs.values.rate;
  const failedRate = data.metrics.http_req_failed.values.rate * 100;
  const avgTime = data.metrics.http_req_duration.values.avg;
  const p95Time = data.metrics.http_req_duration.values['p(95)'];
  const maxTime = data.metrics.http_req_duration.values.max;
  const maxVUs = data.metrics.vus_max.values.max;
  const testDurationSec = (data.state.testRunDurationMs / 1000).toFixed(1);

  const rateAchievedPct = (requestsPerSec / targetRate) * 100;
  const rateOk = rateAchievedPct >= 90;

  let latencyVerdict;
  if (p95Time < 100) {
    latencyVerdict = 'EXCELLENT (feels instant)';
  } else if (p95Time < 300) {
    latencyVerdict = 'GOOD (acceptable, slight delay)';
  } else if (p95Time < 1000) {
    latencyVerdict = 'BORDERLINE (noticeable delay)';
  } else {
    latencyVerdict = 'POOR (feels slow / broken)';
  }
  const latencyOk = p95Time < 300;
  const failuresOk = failedRate === 0;

  let overallVerdict;
  if (rateOk && latencyOk && failuresOk) {
    overallVerdict = '✅ PASS — system handled this load well.';
  } else if (failuresOk && p95Time < 1000) {
    overallVerdict = '⚠️  DEGRADED — no errors, but slowing down. Approaching capacity limit.';
  } else if (failuresOk && p95Time >= 1000) {
    overallVerdict = '🔴 SEVERE — no outright errors, but response times are unusable for real users. System is saturated.';
  } else {
    overallVerdict = '❌ FAIL — errors occurred and/or target rate was not achievable.';
  }

  const readableReport = `
==================== READ LOAD TEST SUMMARY ====================
Target rate              : ${targetRate} requests/sec
Total requests sent      : ${totalRequests}
Test duration            : ${testDurationSec} seconds
Achieved rate            : ${requestsPerSec.toFixed(2)} requests/sec (${rateAchievedPct.toFixed(1)}% of target)
Max concurrent users (VU): ${maxVUs}
Average response time    : ${avgTime.toFixed(2)} ms
95th percentile time     : ${p95Time.toFixed(2)} ms  -> ${latencyVerdict}
Slowest request          : ${maxTime.toFixed(2)} ms
Failed requests          : ${failedRate.toFixed(2)}%
-------------------------------------------------------------
VERDICT: ${overallVerdict}
=================================================================
`;

  return {
    stdout: readableReport,
  };
}