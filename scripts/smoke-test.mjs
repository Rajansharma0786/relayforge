import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { mkdirSync, openSync, closeSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import assert from "node:assert/strict";

const root = fileURLToPath(new URL("../", import.meta.url));
const processes = [];
const postgres = Boolean(process.env.SMOKE_EVENTS_DATABASE_URL && process.env.SMOKE_DELIVERIES_DATABASE_URL);
const database = (name) => postgres ? {
  SPRING_PROFILES_ACTIVE: "postgres",
  DATABASE_URL: process.env[name === "events" ? "SMOKE_EVENTS_DATABASE_URL" : "SMOKE_DELIVERIES_DATABASE_URL"],
  DATABASE_USER: process.env.SMOKE_DATABASE_USER || "relayforge",
  DATABASE_PASSWORD: process.env.SMOKE_DATABASE_PASSWORD || ""
} : {
  SPRING_DATASOURCE_URL: `jdbc:h2:mem:smoke_${name};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`
};
const delay = ms => new Promise(done => setTimeout(done, ms));
async function freePort() {
  const server = createServer();
  await new Promise((done, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", done); });
  const port = server.address().port;
  await new Promise(done => server.close(done));
  return port;
}
function start(name, port, extra = {}) {
  mkdirSync(resolve(root, ".smoke-logs"), { recursive: true });
  const log = openSync(resolve(root, ".smoke-logs", name + ".log"), "w");
  const process = spawn("java", ["-jar", resolve(root, "services", name, "target", name + "-0.1.0-SNAPSHOT.jar")], {
    cwd: root, stdio: ["ignore", log, log],
    env: { ...globalThis.process.env, SERVER_PORT: String(port), SERVER_ADDRESS: "127.0.0.1",
      SPRING_PROFILES_ACTIVE: "local", INTERNAL_SERVICE_TOKEN: "smoke-test-internal-token", ...extra }
  });
  closeSync(log);
  processes.push(process);
  return process;
}
async function ready(port, process) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    if (process.exitCode !== null) throw new Error("Service exited; inspect .smoke-logs.");
    try {
      const response = await fetch(`http://127.0.0.1:${port}/actuator/health/readiness`, { signal: AbortSignal.timeout(1500) });
      if (response.ok) return;
    } catch { /* wait for startup */ }
    await delay(300);
  }
  throw new Error("Service startup timed out; inspect .smoke-logs.");
}
try {
  const [eventsPort, deliveryPort, gatewayPort] = await Promise.all([freePort(), freePort(), freePort()]);
  const delivery = start("delivery-service", deliveryPort, {
    ...database("deliveries")
  });
  const events = start("event-service", eventsPort, {
    ...database("events"),
    DELIVERY_SERVICE_URL: `http://127.0.0.1:${deliveryPort}`, OUTBOX_DELAY_MS: "300"
  });
  const gateway = start("api-gateway", gatewayPort, {
    EVENT_SERVICE_URL: `http://127.0.0.1:${eventsPort}`, DELIVERY_SERVICE_URL: `http://127.0.0.1:${deliveryPort}`
  });
  await Promise.all([ready(eventsPort, events), ready(deliveryPort, delivery), ready(gatewayPort, gateway)]);
  const base = `http://127.0.0.1:${gatewayPort}`;
  const body = { eventType: "invoice.paid", destination: "https://example.com/hooks", payload: { invoiceId: "smoke_123" } };
  const submit = value => fetch(base + "/api/events", { method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": "smoke-event" }, body: JSON.stringify(value) });
  const created = await submit(body);
  assert.equal(created.status, 201);
  const event = await created.json();
  const replay = await submit(body);
  assert.equal(replay.status, 200);
  assert.equal((await replay.json()).id, event.id);
  assert.equal(replay.headers.get("Idempotency-Replayed"), "true");
  const conflict = await submit({ ...body, payload: { changed: true } });
  assert.equal(conflict.status, 409);
  const blocked = await fetch(base + "/internal/deliveries", { method: "POST", body: "{}" });
  assert.equal(blocked.status, 404);
  let deliveredToQueue = false;
  for (let attempt = 0; attempt < 40; attempt++) {
    const response = await fetch(base + "/api/deliveries");
    assert.equal(response.status, 200);
    const jobs = await response.json();
    if (jobs.length === 1 && jobs[0].eventId === event.id) { deliveredToQueue = true; break; }
    await delay(250);
  }
  assert.equal(deliveredToQueue, true, "Outbox must reach the real delivery service");
  const finalEvent = await (await fetch(base + "/api/events/" + event.id)).json();
  assert.equal(finalEvent.status, "FORWARDED");
  console.log(`PASS (${postgres ? "PostgreSQL" : "H2"}): three live Java services; durable intake, idempotent replay, conflict, gateway isolation, and outbox forwarding.`);
} finally {
  for (const process of processes) process.kill("SIGTERM");
  await Promise.all(processes.map(process => new Promise(done => {
    if (process.exitCode !== null) return done();
    const timer = setTimeout(() => { process.kill("SIGKILL"); done(); }, 5000);
    process.once("exit", () => { clearTimeout(timer); done(); });
  })));
}
