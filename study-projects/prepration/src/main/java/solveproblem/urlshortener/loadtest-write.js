import http from 'k6/http';
import { check, sleep } from 'k6';

// ============================================================
// SINGLE SOURCE OF TRUTH — change ONLY this value to adjust load.
// Both the test behavior AND the summary verdict read from here.
// ============================================================
const TARGET_RATE = 3000;

// Simulates sustained write traffic against POST /api/urls
export const options = {
  scenarios: {
    write_load: {
      executor: 'constant-arrival-rate',
      rate: TARGET_RATE,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: Math.min(TARGET_RATE, 500),
      maxVUs: TARGET_RATE * 2, // generous headroom, scales automatically with TARGET_RATE
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    longUrl: `https://example.com/page/${Math.random().toString(36).substring(7)}`
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post('http://localhost:8080/api/urls', payload, params);

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has shortCode': (r) => JSON.parse(r.body).shortCode !== undefined,
  });

  // Readable per-request log line: which VU (user) sent it, iteration number,
  // response time, status, and the shortCode returned.
  let shortCode = 'N/A';
  try {
    shortCode = JSON.parse(res.body).shortCode;
  } catch (e) {
    shortCode = 'PARSE_ERROR';
  }

  //console.log(
   // `VU=${__VU} | iter=${__ITER} | status=${res.status} | time=${res.timings.duration.toFixed(2)}ms | shortCode=${shortCode}`
  //);
}

// Custom, plain-English summary printed at the very end of the test.
export function handleSummary(data) {
  const targetRate = TARGET_RATE; // now always in sync with the actual test config above
  const totalRequests = data.metrics.http_reqs.values.count;
  const requestsPerSec = data.metrics.http_reqs.values.rate;
  const failedRate = data.metrics.http_req_failed.values.rate * 100;
  const avgTime = data.metrics.http_req_duration.values.avg;
  const p95Time = data.metrics.http_req_duration.values['p(95)'];
  const maxTime = data.metrics.http_req_duration.values.max;
  const maxVUs = data.metrics.vus_max.values.max;
  const testDurationSec = (data.state.testRunDurationMs / 1000).toFixed(1);

  // ---- Verdict logic, based on industry rule-of-thumb thresholds ----

  // 1. Did we achieve close to the target rate? (within 10% = pass)
  const rateAchievedPct = (requestsPerSec / targetRate) * 100;
  const rateOk = rateAchievedPct >= 90;

  // 2. Latency classification (based on p95, since avg can hide problems)
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

  // 3. Failure rate (anything above 0% is a real problem, not just slowness)
  const failuresOk = failedRate === 0;

  // ---- Overall verdict ----
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
==================== LOAD TEST SUMMARY ====================
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
=============================================================
`;

  return {
    stdout: readableReport, // prints to terminal instead of default k6 summary
  };
}