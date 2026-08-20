import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.K6_BASE_URL || 'http://app:8080';
const runId = __ENV.RUN_ID;
const rate = Number(__ENV.RATE || 1);
const duration = __ENV.DURATION || '1s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 1);
const maxVUs = Number(__ENV.MAX_VUS || 1);
const resultsDir = __ENV.RESULT_DIR || '/results';

export const options = {
  systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'scenario'],
  scenarios: {
    point_charge: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    'checks{target:point_charge}': ['rate==1'],
    'http_req_failed{target:point_charge}': ['rate<0.01'],
    'dropped_iterations{scenario:point_charge}': ['count==0'],
  },
};

function controlParams() {
  return {
    headers: { 'Content-Type': 'application/json' },
    tags: { target: 'control', name: 'load-test-control' },
  };
}

export function setup() {
  if (!runId) {
    throw new Error('RUN_ID is required');
  }
  const response = http.post(
    `${baseUrl}/api/load-test/runs/point/tokens`,
    JSON.stringify({ runId }),
    controlParams(),
  );
  if (response.status !== 200) {
    throw new Error(`point token preparation failed: ${response.status}`);
  }
  const tokens = response.json('accounts');
  if (!Array.isArray(tokens) || tokens.length === 0) {
    throw new Error('point token preparation returned no accounts');
  }
  return tokens;
}

export default function (accounts) {
  const account = accounts[(__VU - 1) % accounts.length];
  const suffix = `${runId}-${__VU}-${__ITER}`;
  const response = http.post(
    `${baseUrl}/api/points/charges`,
    JSON.stringify({
      payment_id: `load-test-payment-${suffix}`,
      order_id: `lt-${suffix}`,
      amount: 10000,
    }),
    {
      headers: {
        Authorization: `Bearer ${account.accessToken}`,
        'Content-Type': 'application/json',
      },
      tags: { target: 'point_charge', name: 'point-charge' },
    },
  );
  check(response, { 'point charge created': (result) => result.status === 201 }, { target: 'point_charge' });
}

export function handleSummary(data) {
  const output = {};
  output[`${resultsDir}/summary.json`] = JSON.stringify(data);
  return output;
}
