import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";
const runId = __ENV.RUN_ID;
if (!runId || runId.length > 60) throw new Error("Provide a unique RUN_ID of at most 60 characters for each run");
const accepted = new Counter("acknowledged_payments");
export const options = {
  scenarios: {
    ingestion: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 100), timeUnit: "1s",
      duration: __ENV.DURATION || "2m",
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 400),
    },
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
  thresholds: {
    checks: ["rate==1"], http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<250", "p(99)<500"],
    dropped_iterations: ["count==0"],
  },
};
export default function () {
  const requestId = `${runId}-${__VU}-${__ITER}`;
  const response = http.post(`${baseUrl}/api/payments`, JSON.stringify({
    requestId, accountId: `acct-${__VU}`, amount: 125.5, currency: "USD", merchantId: "mrc-load",
  }), { headers: { "Content-Type": "application/json" }, timeout: "15s" });
  let body = {};
  try { body = response.json(); } catch (_) { /* failed checks below preserve errors */ }
  const ok = check(response, {
    "Kafka acknowledged this request": r => r.status === 202 && body && body.status === "ACCEPTED" && body.requestId === requestId,
  });
  if (ok) accepted.add(1);
}
export function handleSummary(data) {
  return {
    [__ENV.SUMMARY_PATH || "summary.json"]: JSON.stringify({
      runId, scope: "HTTP ingestion to Kafka acknowledgement, not settlement",
      workload: options.scenarios.ingestion, results: data,
    }, null, 2),
    stdout: `Run ${runId} complete. Inspect the JSON summary and threshold exit status.\n`,
  };
}
