// load-test.js — Verify your API meets the 50ms target
// Install k6: https://k6.io/docs/getting-started/installation/
// Run: k6 run load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// ─── Custom Metrics ────────────────────────────────────────────────────────
const responseTime = new Trend('response_time', true);
const successRate  = new Rate('success_rate');

// ─── Test Config ──────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 50  },  // Ramp up to 50 users
    { duration: '60s', target: 200 },  // Ramp up to 200 users (stress)
    { duration: '30s', target: 0   },  // Ramp down
  ],
  thresholds: {
    // ✅ These must pass — your 50ms contract
    'http_req_duration': [
      'p(50) < 50',    // 50% of requests under 50ms
      'p(95) < 100',   // 95% of requests under 100ms
      'p(99) < 200',   // 99% of requests under 200ms
    ],
    'success_rate': ['rate > 0.99'],  // 99%+ success rate
  },
};

const BASE_URL = 'http://localhost:8080/api/v1';

export default function () {
  // ── HOT PATH TEST — GET by ID ────────────────────────────────────────────
  const id = Math.floor(Math.random() * 100) + 1;
  const res = http.get(`${BASE_URL}/products/${id}`, {
    headers: { 'Accept-Encoding': 'gzip' },
    tags: { endpoint: 'getById' },
  });

  responseTime.add(res.timings.duration);
  successRate.add(res.status === 200 || res.status === 404);

  check(res, {
    'status is 200': r => r.status === 200,
    'response under 50ms': r => r.timings.duration < 50,
    'has body': r => r.body && r.body.length > 0,
  });

  sleep(0.1); // 100ms think time between requests
}

export function handleSummary(data) {
  const p50 = data.metrics.http_req_duration.values['p(50)'];
  const p95 = data.metrics.http_req_duration.values['p(95)'];
  const p99 = data.metrics.http_req_duration.values['p(99)'];

  console.log('\n══════════════════════════════════════');
  console.log('        LATENCY REPORT');
  console.log('══════════════════════════════════════');
  console.log(`  p50: ${p50?.toFixed(2)}ms  ${p50 < 50  ? '✅' : '❌'} (target: <50ms)`);
  console.log(`  p95: ${p95?.toFixed(2)}ms  ${p95 < 100 ? '✅' : '❌'} (target: <100ms)`);
  console.log(`  p99: ${p99?.toFixed(2)}ms  ${p99 < 200 ? '✅' : '❌'} (target: <200ms)`);
  console.log('══════════════════════════════════════\n');

  return {};
}
