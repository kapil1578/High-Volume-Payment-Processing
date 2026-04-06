import http from "k6/http";
import { check } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8081";

export const options = {
  scenarios: {
    payments_1000_tps: {
      executor: "constant-arrival-rate",
      rate: 1000,
      timeUnit: "1s",
      duration: "2m",
      preAllocatedVUs: 400,
      maxVUs: 2000,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<250"],
  },
};

export default function () {
  const requestId = `req-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    requestId,
    accountId: `acct-${__VU}`,
    amount: 125.5,
    currency: "USD",
    merchantId: "mrc-load",
  });

  const response = http.post(`${baseUrl}/api/payments`, payload, {
    headers: { "Content-Type": "application/json" },
    timeout: "5s",
  });

  check(response, {
    "status is 202": (r) => r.status === 202,
  });
}
