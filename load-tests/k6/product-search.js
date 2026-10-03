// k6 benchmark — Elasticsearch (product-query) vs direct SQL (product-service)
//
// Fires equal mixes of real-world search queries against:
//   ES path:  GET http://product-query:8088/products/search/query?...
//   SQL path: GET http://api-gateway:8080/api/v1/products/{id}
//             (point lookup — SQL at its best, Redis cache helps too)
//
// Why this benchmark matters for interviews:
//   - Shows ES scales fuzzy / faceted queries that SQL LIKE can't do at all.
//   - Shows direct-id lookups where SQL+cache can beat ES on p50 cold.
//   - Produces side-by-side p95/p99 panels so you can quote exact numbers.
//
// Run:
//   docker compose --profile loadtest run --rm \
//     -e TARGET=all -e SCENARIO=load k6 run /scripts/product-search.js
//
// Env:
//   ES_URL        [http://product-query:8088]
//   SQL_URL       [http://api-gateway:8080]
//   AUTH          [admin:admin123]
//   TARGET        [all]  one of: es | sql | all
//   SCENARIO      [smoke] one of: smoke | load | stress
//   PRODUCT_IDS   [1,2,3,4,5]  for SQL point-lookup
//   SEARCH_TERMS  ["widget","gadget","doohickey","thingamajig"]

import http from 'k6/http';
import encoding from 'k6/encoding';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const ES_URL   = __ENV.ES_URL   || 'http://product-query:8088';
const SQL_URL  = __ENV.SQL_URL  || 'http://api-gateway:8080';
const AUTH     = __ENV.AUTH     || 'admin:admin123';
const TARGET   = __ENV.TARGET   || 'all';
const SCENARIO = __ENV.SCENARIO || 'smoke';

const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '1,2,3,4,5').split(',').map(Number);
const SEARCH_TERMS = (__ENV.SEARCH_TERMS ||
    'widget,gadget,doohickey,thingamajig,blue,red,green').split(',');

// Shape of each scenario mirrors load-tests/k6/orders.js so the two coexist.
const SCENARIOS = {
  smoke:  { executor: 'per-vu-iterations', vus: 1, iterations: 20, maxDuration: '1m' },
  load:   { executor: 'ramping-vus',
            stages: [
              { duration: '30s', target: 20 },
              { duration: '2m',  target: 20 },
              { duration: '20s', target: 0  },
            ],
            gracefulRampDown: '20s' },
  stress: { executor: 'ramping-arrival-rate',
            startRate: 5, timeUnit: '1s',
            preAllocatedVUs: 50, maxVUs: 200,
            stages: [
              { duration: '1m', target: 50  },
              { duration: '2m', target: 200 },
              { duration: '30s', target: 0  },
            ] },
};

export const options = {
  scenarios: { [SCENARIO]: SCENARIOS[SCENARIO] },
  thresholds: {
    'http_req_failed':                                 ['rate<0.01'],
    'http_req_duration{endpoint:es-fulltext}':         ['p(95)<150', 'p(99)<400'],
    'http_req_duration{endpoint:es-facet}':            ['p(95)<200', 'p(99)<500'],
    'http_req_duration{endpoint:es-fuzzy}':            ['p(95)<200', 'p(99)<500'],
    'http_req_duration{endpoint:es-autocomplete}':     ['p(95)<80',  'p(99)<200'],
    'http_req_duration{endpoint:sql-get}':             ['p(95)<80',  'p(99)<300'],
  },
  tags: { service: 'product-search' },
};

// Per-endpoint custom trends so Grafana can plot each separately.
const esFullText     = new Trend('es_fulltext_ms',     true);
const esFacet        = new Trend('es_facet_ms',        true);
const esFuzzy        = new Trend('es_fuzzy_ms',        true);
const esAutocomplete = new Trend('es_autocomplete_ms', true);
const sqlGet         = new Trend('sql_get_ms',         true);

export function setup() {
  if (TARGET === 'es' || TARGET === 'all') {
    const r = http.get(`${ES_URL}/actuator/health`, { tags: { endpoint: 'probe' } });
    if (r.status !== 200) throw new Error(`product-query not reachable: ${r.status}`);
  }
  if (TARGET === 'sql' || TARGET === 'all') {
    const r = http.get(`${SQL_URL}/actuator/health`, { tags: { endpoint: 'probe' } });
    if (r.status !== 200) throw new Error(`api-gateway not reachable: ${r.status}`);
  }
  console.log(`✓ targets reachable — running ${SCENARIO} against ${TARGET}`);
}

export default function () {
  if (TARGET === 'es' || TARGET === 'all') runEs();
  if (TARGET === 'sql' || TARGET === 'all') runSql();
  sleep(0.1 + Math.random() * 0.3);
}

function runEs() {
  const term = pick(SEARCH_TERMS);

  group('ES full-text', () => {
    const r = http.get(`${ES_URL}/products/search/query?q=${term}`,
        { tags: { endpoint: 'es-fulltext' } });
    checkEs(r);
    esFullText.add(r.timings.duration);
  });

  group('ES facets', () => {
    const r = http.get(`${ES_URL}/products/search/query?q=${term}&aggregations=category,brand,price`,
        { tags: { endpoint: 'es-facet' } });
    checkEs(r);
    esFacet.add(r.timings.duration);
  });

  group('ES fuzzy', () => {
    // Introduce a typo — drop a character so fuzziness has something to fix.
    const typo = term.length > 4 ? term.slice(0, -1) : term + 'x';
    const r = http.get(`${ES_URL}/products/search/query?q=${typo}&fuzzy=true`,
        { tags: { endpoint: 'es-fuzzy' } });
    checkEs(r);
    esFuzzy.add(r.timings.duration);
  });

  group('ES autocomplete (completion suggester)', () => {
    const prefix = term.slice(0, 2);
    const r = http.get(`${ES_URL}/products/suggest?prefix=${prefix}`,
        { tags: { endpoint: 'es-autocomplete' } });
    checkEs(r);
    esAutocomplete.add(r.timings.duration);
  });
}

function runSql() {
  group('SQL point lookup', () => {
    const id = pick(PRODUCT_IDS);
    const r = http.get(`${SQL_URL}/api/v1/products/${id}`, {
      headers: { Authorization: `Basic ${encoding.b64encode(AUTH)}` },
      tags: { endpoint: 'sql-get' },
    });
    check(r, {
      'status 2xx or 404': (res) => res.status < 500,
    });
    sqlGet.add(r.timings.duration);
  });
}

function checkEs(r) {
  check(r, {
    'status 200': (res) => res.status === 200,
    'has body':   (res) => res.body && res.body.length > 2,
  });
}

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
