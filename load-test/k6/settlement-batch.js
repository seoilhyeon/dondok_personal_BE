import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = __ENV.K6_BASE_URL || 'http://app:8080';
const runId = __ENV.RUN_ID;
const settlements = Number(__ENV.SETTLEMENTS || 1);
const resultsDir = __ENV.RESULT_DIR || '/results';
const settlementWallSeconds = new Trend('settlement_wall_seconds');
const settlementsPerSecond = new Trend('settlements_per_second');
const itemsPerSecond = new Trend('settlement_items_per_second');

export const options = {
  systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'error_code', 'scenario'],
  scenarios: { settlement_batch: { executor: 'shared-iterations', vus: 1, iterations: 1 } },
  thresholds: { 'checks{target:settlement_batch}': ['rate==1'] },
};

export default function () {
  if (!runId) {
    throw new Error('RUN_ID is required');
  }
  const response = http.post(
    `${baseUrl}/api/load-test/runs/settlement/final/trigger`,
    JSON.stringify({ runId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { target: 'settlement_batch', name: 'settlement-final-batch' },
    },
  );
  const body = response.json();
  const expectedItems = settlements * 2;
  check(
    response,
    {
      'settlement batch accepted': (result) => result.status === 200,
      'settlement batch exact success': () =>
        body.requested === settlements &&
        body.claimed === settlements &&
        body.processed === settlements &&
        body.succeeded === settlements &&
        body.failed === 0 &&
        body.nonTerminal === 0 &&
        body.settlementItems === expectedItems &&
        body.refundedItems === expectedItems,
    },
    { target: 'settlement_batch' },
  );
  if (body.startedAt && body.finishedAt) {
    const seconds = (Date.parse(body.finishedAt) - Date.parse(body.startedAt)) / 1000;
    if (seconds > 0) {
      settlementWallSeconds.add(seconds);
      settlementsPerSecond.add(settlements / seconds);
      itemsPerSecond.add(expectedItems / seconds);
    }
  }
}

export function handleSummary(data) {
  const output = {};
  output[`${resultsDir}/summary.json`] = JSON.stringify(data);
  return output;
}
