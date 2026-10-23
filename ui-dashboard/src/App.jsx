import { useEffect, useMemo, useState } from "react";

function fmtMoney(value) {
  return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function App() {
  const [overview, setOverview] = useState({ throughputPerSecond: 0, successRatePercent: 0, avgLatencyMs: 0, failedTransactions: 0 });
  const [transactions, setTransactions] = useState([]);
  const [catalogedTransactions, setCatalogedTransactions] = useState([]);
  const [partitions, setPartitions] = useState([]);
  const [failed, setFailed] = useState([]);
  const [live, setLive] = useState(null);
  const [activeSection, setActiveSection] = useState("live");
  const [activeTopTab, setActiveTopTab] = useState("dashboard");
  const [telemetryRange, setTelemetryRange] = useState("hour");
  const [telemetry, setTelemetry] = useState({ range: "hour", points: [] });
  const [theme, setTheme] = useState(() => localStorage.getItem("paydash-theme") || "light");
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [transactionDetail, setTransactionDetail] = useState(null);

  useEffect(() => {
    const load = async () => {
      const [o, t, p, f] = await Promise.all([
        fetch("/api/dashboard/overview").then((r) => r.json()),
        fetch("/api/dashboard/transactions").then((r) => r.json()),
        fetch("/api/dashboard/kafka/partitions").then((r) => r.json()),
        fetch("/api/dashboard/failed").then((r) => r.json())
      ]);
      setOverview(o);
      setTransactions(t);
      setPartitions(p);
      setFailed(f);
    };
    load();
  }, []);

  useEffect(() => {
    const loadTransactions = async () => {
      const [recentData, catalogedData] = await Promise.all([
        fetch("/api/dashboard/transactions").then((r) => r.json()),
        fetch("/api/dashboard/transactions/catalog").then((r) => r.json())
      ]);
      setTransactions(recentData);
      setCatalogedTransactions(catalogedData);
    };
    loadTransactions();
    const timer = setInterval(loadTransactions, 3000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const loadTelemetry = async () => {
      const data = await fetch(`/api/dashboard/telemetry?range=${telemetryRange}`).then((r) => r.json());
      setTelemetry(data);
    };
    loadTelemetry();
    const timer = setInterval(loadTelemetry, 5000);
    return () => clearInterval(timer);
  }, [telemetryRange]);

  useEffect(() => {
    if (!selectedTransaction) {
      setTransactionDetail(null);
      return;
    }
    fetch(`/api/dashboard/transactions/${selectedTransaction}`)
      .then((r) => r.json())
      .then((data) => setTransactionDetail(data))
      .catch(() => setTransactionDetail(null));
  }, [selectedTransaction]);

  useEffect(() => {
    if (selectedTransaction && !transactions.some((tx) => tx.transactionId === selectedTransaction)) {
      setSelectedTransaction(null);
      setTransactionDetail(null);
    }
  }, [transactions, selectedTransaction]);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("paydash-theme", theme);
  }, [theme]);

  useEffect(() => {
    const sse = new EventSource("/api/dashboard/stream");
    sse.addEventListener("metric", (event) => {
      const data = JSON.parse(event.data);
      setLive(data);
    });
    return () => sse.close();
  }, []);

  const healthState = useMemo(() => {
    if (overview.failedTransactions === 0) {
      return { key: "healthy", text: "All systems operational" };
    }
    if (overview.failedTransactions <= 2) {
      return { key: "partial", text: "Partial degradation" };
    }
    return { key: "critical", text: "Attention required" };
  }, [overview.failedTransactions]);
  const sectionTitle = useMemo(() => {
    if (activeTopTab !== "dashboard") {
      const tabTitles = {
        transactions: "Transactions",
        kafka: "Kafka",
        logs: "Logs",
        settings: "Settings"
      };
      return tabTitles[activeTopTab];
    }
    const titles = {
      live: "Live dashboard",
      analytics: "Analytics",
      kafka: "Kafka topics",
      rest: "REST endpoints",
      failed: "Failed transactions",
      oauth: "OAuth2 tokens",
      audit: "Audit log",
      k8s: "K8s pods",
      grafana: "Grafana"
    };
    return titles[activeSection] ?? "Dashboard";
  }, [activeSection, activeTopTab]);

  const sidebarItems = [
    { key: "live", label: "Live dashboard", section: "Overview" },
    { key: "analytics", label: "Analytics", section: "Overview" },
    { key: "kafka", label: "Kafka topics", section: "Processing", badge: partitions.length },
    { key: "rest", label: "REST endpoints", section: "Processing" },
    { key: "failed", label: "Failed txns", section: "Processing", badge: failed.length },
    { key: "oauth", label: "OAuth2 tokens", section: "Security" },
    { key: "audit", label: "Audit log", section: "Security" },
    { key: "k8s", label: "K8s pods", section: "Infra" },
    { key: "grafana", label: "Grafana", section: "Infra" }
  ];
  const topTabs = [
    { key: "dashboard", label: "Dashboard" },
    { key: "transactions", label: "Transactions" },
    { key: "kafka", label: "Kafka" },
    { key: "logs", label: "Logs" },
    { key: "settings", label: "Settings" }
  ];
  const nextTheme = theme === "dark" ? "light" : "dark";

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-left">
          <div className="brand-wrap">
            <PayDashWordmark />
          </div>
        </div>
        <div className="topbar-center">
          <nav>
            {topTabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={`top-tab ${activeTopTab === tab.key ? "active" : ""}`}
                onClick={() => setActiveTopTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>
        <div className="topbar-right">
          <button
            type="button"
            className="theme-toggle"
            onClick={() => setTheme(nextTheme)}
            aria-label={`Switch to ${nextTheme} mode`}
            title={`Switch to ${nextTheme} mode`}
          >
            <ThemeModeIcon theme={theme} />
            <span>{theme === "dark" ? "Dark mode" : "Light mode"}</span>
          </button>
          <div className={`health ${healthState.key}`}>● {healthState.text}</div>
        </div>
      </header>

      <div className="layout">
        <aside className="sidebar">
          {activeTopTab !== "dashboard" ? (
            <div className="pane placeholder-panel">
              <h3>{sectionTitle}</h3>
              <p>Select a dashboard tab to return to live operational views.</p>
            </div>
          ) : (
            ["Overview", "Processing", "Security", "Infra"].map((section) => (
              <div key={section}>
                <h4>{section}</h4>
                {sidebarItems
                  .filter((item) => item.section === section)
                  .map((item) => (
                    <button
                      key={item.key}
                      type="button"
                      className={`menu ${activeSection === item.key ? "active" : ""}`}
                      onClick={() => setActiveSection(item.key)}
                    >
                      <span>{item.label}</span>
                      {item.badge !== undefined ? <span>{item.badge}</span> : null}
                    </button>
                  ))}
              </div>
            ))
          )}
        </aside>

        <main className="content">
          <p role="note"><strong>Demo data:</strong> This dashboard uses synthetic transactions and telemetry. For measured service behavior, use the Actuator Prometheus endpoints and benchmark evidence.</p>
          <div className="title-row">
            <h2>{sectionTitle}</h2>
            <button className="primary">+ New endpoint</button>
          </div>

          {activeTopTab === "dashboard" && activeSection === "live" && (
            <>
              <section className="cards">
                <Card title="Throughput" value={`${live?.throughputPerSecond ?? overview.throughputPerSecond}`} meta="tx/s" />
                <Card title="Success rate" value={`${overview.successRatePercent}%`} meta="stable" />
                <Card title="Avg latency" value={`${live?.latencyMs ?? overview.avgLatencyMs}ms`} meta="p95" />
                <Card title="Failed txns" value={`${live?.failedTransactions ?? overview.failedTransactions}`} meta="last hour" />
              </section>

              <section className="panes">
                <div className="pane">
                  <h3>TPS telemetry</h3>
                  <div className="range-switch">
                    {["second", "minute", "hour", "day", "month", "year", "decade"].map((range) => (
                      <button
                        key={range}
                        type="button"
                        className={`range-chip ${telemetryRange === range ? "active" : ""}`}
                        onClick={() => setTelemetryRange(range)}
                      >
                        {range}
                      </button>
                    ))}
                  </div>
                  <TelemetryChart points={telemetry.points} />
                </div>
                <div className="pane">
                  <h3>Kafka partitions</h3>
                  {partitions.map((p) => (
                    <div key={p.topic} className="partition-row">
                      <span>{p.topic}</span>
                      <div className="bar"><div style={{ width: `${Math.min(100, p.messagesPerSecond / 10)}%` }} /></div>
                      <strong>{p.messagesPerSecond}/s</strong>
                    </div>
                  ))}
                </div>
              </section>

              <section className="table-wrap">
                <h3>Recent transactions</h3>
                <table>
                  <thead>
                    <tr>
                      <th>Transaction ID</th><th>Type</th><th>Amount</th><th>Source</th><th>Destination</th><th>Latency</th><th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions.map((tx) => (
                      <tr key={tx.transactionId} className="clickable-row" onClick={() => setSelectedTransaction(tx.transactionId)}>
                        <td>{tx.transactionId}</td>
                        <td>{tx.type}</td>
                        <td>{fmtMoney(tx.amount)}</td>
                        <td>{tx.source}</td>
                        <td>{tx.destination}</td>
                        <td>{tx.latencyMs ? `${tx.latencyMs}ms` : "-"}</td>
                        <td><span className={`status ${tx.status.toLowerCase()}`}>{tx.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>

              <section className="table-wrap">
                <h3>Cataloged transactions</h3>
                <table>
                  <thead>
                    <tr>
                      <th>Transaction ID</th><th>Type</th><th>Amount</th><th>Source</th><th>Destination</th><th>Latency</th><th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {catalogedTransactions.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="empty-row">No cataloged transactions yet.</td>
                      </tr>
                    ) : (
                      catalogedTransactions.map((tx) => (
                        <tr key={`catalog-${tx.transactionId}`} className="clickable-row" onClick={() => setSelectedTransaction(tx.transactionId)}>
                          <td>{tx.transactionId}</td>
                          <td>{tx.type}</td>
                          <td>{fmtMoney(tx.amount)}</td>
                          <td>{tx.source}</td>
                          <td>{tx.destination}</td>
                          <td>{tx.latencyMs ? `${tx.latencyMs}ms` : "-"}</td>
                          <td><span className={`status ${tx.status.toLowerCase()}`}>{tx.status}</span></td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </section>

              {selectedTransaction && transactionDetail && (
                <section className="pane detail-panel">
                  <div className="detail-header">
                    <h3>Transaction detail: {transactionDetail.transactionId}</h3>
                    <button type="button" className="theme-chip" onClick={() => setSelectedTransaction(null)}>Close</button>
                  </div>
                  <div className="detail-grid">
                    <DetailItem label="Type" value={transactionDetail.type} />
                    <DetailItem label="Amount" value={`${fmtMoney(transactionDetail.amount)} ${transactionDetail.currency}`} />
                    <DetailItem label="Status" value={transactionDetail.status} />
                    <DetailItem label="Latency" value={transactionDetail.latencyMs ? `${transactionDetail.latencyMs}ms` : "-"} />
                    <DetailItem label="Source" value={transactionDetail.source} />
                    <DetailItem label="Destination" value={transactionDetail.destination} />
                    <DetailItem label="Channel" value={transactionDetail.channel} />
                    <DetailItem label="Risk score" value={transactionDetail.riskScore} />
                    <DetailItem label="Trace ID" value={transactionDetail.traceId} />
                    <DetailItem label="Processor ref" value={transactionDetail.processorRef} />
                    <DetailItem label="Created at" value={transactionDetail.createdAt} />
                    <DetailItem label="Settled at" value={transactionDetail.settledAt || "-"} />
                  </div>
                  {transactionDetail.failureReason && (
                    <p className="failure-reason"><strong>Failure reason:</strong> {transactionDetail.failureReason}</p>
                  )}
                  <h4 className="timeline-title">Timeline</h4>
                  <div className="timeline">
                    {transactionDetail.timeline.map((event, idx) => (
                      <div className="timeline-item" key={`${event.timestamp}-${idx}`}>
                        <div className="timeline-dot" />
                        <div>
                          <p><strong>{event.status}</strong> - {event.timestamp}</p>
                          <small>{event.description}</small>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </>
          )}

          {activeTopTab === "dashboard" && activeSection !== "live" && (
            <section className="pane placeholder-panel">
              <h3>{sectionTitle}</h3>
              <p>
                This section is now clickable and selected. I can wire this view to real backend data next
                (for example, {sectionTitle.toLowerCase()} metrics, filters, and actions).
              </p>
            </section>
          )}

          {activeTopTab !== "dashboard" && (
            <section className="pane placeholder-panel">
              <h3>{sectionTitle}</h3>
              <p>
                This top navigation tab is now interactive. I can wire this panel with dedicated APIs and actions next.
              </p>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}

function Card({ title, value, meta }) {
  return (
    <div className="card">
      <p>{title}</p>
      <h3>{value}</h3>
      <small>{meta}</small>
    </div>
  );
}

function TelemetryChart({ points }) {
  if (!points?.length) {
    return <div className="sparkline">Loading telemetry...</div>;
  }

  const width = 760;
  const height = 180;
  const pad = 12;
  const min = Math.min(...points.map((p) => p.tps));
  const max = Math.max(...points.map((p) => p.tps));
  const span = Math.max(1, max - min);

  const coords = points.map((p, i) => {
    const x = pad + (i * (width - 2 * pad)) / Math.max(1, points.length - 1);
    const y = height - pad - ((p.tps - min) / span) * (height - 2 * pad);
    return { x, y, label: p.label, tps: p.tps };
  });
  const polyline = coords.map((c) => `${c.x},${c.y}`).join(" ");

  return (
    <div className="telemetry-chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} className="telemetry-chart">
        <polyline points={polyline} fill="none" stroke="#F63049" strokeWidth="3" />
      </svg>
      <div className="telemetry-axis">
        <span>{points[0].label}</span>
        <span>{points[Math.floor(points.length / 2)]?.label}</span>
        <span>{points[points.length - 1].label}</span>
      </div>
    </div>
  );
}

function DetailItem({ label, value }) {
  return (
    <div>
      <small>{label}</small>
      <p>{value}</p>
    </div>
  );
}

function PayDashWordmark() {
  return (
    <div className="wordmark-badge" aria-hidden="true">
      <svg viewBox="0 0 220 92" className="wordmark-svg" role="img" aria-label="PayDash">
        <rect x="0" y="0" width="220" height="92" rx="14" fill="#91E6A6" />
        <text
          x="18"
          y="63"
          fill="#6B2673"
          fontSize="56"
          fontWeight="700"
          fontStyle="italic"
          fontFamily="'Brush Script MT', 'Segoe Script', 'Lucida Handwriting', cursive"
          letterSpacing="1.5"
          transform="skewX(-7)"
        >
          PayDash
        </text>
      </svg>
    </div>
  );
}

function ThemeModeIcon({ theme }) {
  if (theme === "dark") {
    return (
      <svg viewBox="0 0 24 24" className="theme-toggle-icon" aria-hidden="true">
        <path
          d="M14.5 3.5a1 1 0 0 1 .88 1.47A7 7 0 1 0 19.03 12a1 1 0 0 1 1.48.87A9 9 0 1 1 14.5 3.5Z"
          fill="currentColor"
        />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" className="theme-toggle-icon" aria-hidden="true">
      <circle cx="12" cy="12" r="4.5" fill="currentColor" />
      <path d="M12 1.75v3M12 19.25v3M4.22 4.22l2.12 2.12M17.66 17.66l2.12 2.12M1.75 12h3M19.25 12h3M4.22 19.78l2.12-2.12M17.66 6.34l2.12-2.12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
