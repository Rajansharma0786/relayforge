import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";

const formatTime = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric",
  }).format(new Date(value));

export default function App() {
  const client = useQueryClient();
  const events = useQuery({
    queryKey: ["events"],
    queryFn: ({ signal }) => api.listEvents(signal),
    refetchInterval: 5000,
  });
  const deliveries = useQuery({
    queryKey: ["deliveries"],
    queryFn: ({ signal }) => api.listDeliveries(signal),
    refetchInterval: 5000,
  });
  const [eventType, setEventType] = useState("invoice.paid");
  const [destination, setDestination] = useState(
    "https://example.com/webhooks",
  );
  const [payload, setPayload] = useState(
    '{\n  "invoiceId": "inv_123",\n  "amount": 4999\n}',
  );
  const [key, setKey] = useState(() => crypto.randomUUID() as string);
  const [validation, setValidation] = useState("");
  const create = useMutation({
    mutationFn: (input: api.CreateEventInput) => api.createEvent(input, key),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["events"] });
      void client.invalidateQueries({ queryKey: ["deliveries"] });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setValidation("");
    create.reset();
    try {
      const parsed: unknown = JSON.parse(payload);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed))
        throw new Error("Payload must be a JSON object.");
      create.mutate({
        eventType,
        destination,
        payload: parsed as Record<string, unknown>,
      });
    } catch (error) {
      setValidation(
        error instanceof Error ? error.message : "Enter valid JSON.",
      );
    }
  }

  const connected = events.isSuccess && deliveries.isSuccess;
  const unavailable = events.isError || deliveries.isError;
  return (
    <div className="app">
      <aside className="sidebar">
        <a className="brand" href="#overview">
          <span className="brand-symbol">
            R<span>↗</span>
          </span>
          <span>
            relayforge<small>WEBHOOK OPERATIONS</small>
          </span>
        </a>
        <p className="nav-label">WORKSPACE</p>
        <nav aria-label="Main navigation">
          <a className="active" href="#overview">
            <span>▦</span> Overview <span className="nav-arrow">↗</span>
          </a>
          <a href="#activity">
            <span>⇄</span> Event activity
          </a>
          <a href="#queue">
            <span>≋</span> Delivery queue
          </a>
          <a href="#create-event">
            <span>＋</span> Create event
          </a>
        </nav>
        <div className="sidebar-bottom">
          <span className="avatar">RS</span>
          <div>
            Rajan's workspace<small>Local development</small>
          </div>
          <span>⌄</span>
        </div>
      </aside>
      <main id="overview">
        <header className="topbar">
          <span>
            Workspace <span className="slash">/</span> <strong>Overview</strong>
          </span>
          <div>
            <span className="environment">
              <i /> DEVELOPMENT
            </span>
            <a
              href="https://github.com/Rajansharma0786/relayforge"
              target="_blank"
              rel="noreferrer"
            >
              View source ↗
            </a>
          </div>
        </header>
        <div className="content">
          <section className="heading">
            <div>
              <p className="eyebrow">RELIABLE BY DESIGN</p>
              <h1>
                Your events.
                <br />
                <span>Under control.</span>
              </h1>
              <p className="subtitle">
                Accept, track, and inspect webhook events from one workspace.
              </p>
            </div>
            <a className="primary jump" href="#create-event">
              ＋ Create an event
            </a>
          </section>
          <div className="system-bar">
            <span className={connected ? "signal" : "signal waiting"} />
            <span>
              {connected
                ? "Event and delivery services connected"
                : unavailable
                  ? "Some services are unavailable"
                  : "Connecting to your services"}
            </span>
            <span className="system-detail">Refreshes every 5 seconds</span>
            <button
              className="text-button"
              onClick={() => {
                void events.refetch();
                void deliveries.refetch();
              }}
            >
              Refresh ↻
            </button>
          </div>
          <section className="metrics" aria-label="Recent activity summary">
            <article>
              <span>RECENT EVENTS</span>
              <strong>{events.data?.length ?? "—"}</strong>
              <small>Latest 20 accepted events</small>
              <b>↗</b>
            </article>
            <article>
              <span>FORWARDED TO DELIVERY</span>
              <strong>
                {events.data?.filter((event) => event.status === "FORWARDED")
                  .length ?? "—"}
              </strong>
              <small>From the recent event ledger</small>
              <b>⇄</b>
            </article>
            <article>
              <span>QUEUED JOBS</span>
              <strong>{deliveries.data?.length ?? "—"}</strong>
              <small>Latest 20 pending delivery jobs</small>
              <b>≋</b>
            </article>
          </section>
          <section className="panel activity" id="activity">
            <div className="panel-heading">
              <div>
                <h2>Event activity</h2>
                <p>A durable record of everything you accept.</p>
              </div>
              <span className="tag">LIVE LEDGER</span>
            </div>
            {events.isPending && <p className="empty">Loading events…</p>}
            {events.isError && (
              <p className="notice error" role="alert">
                Event service: {events.error.message}
              </p>
            )}
            {events.data?.length === 0 && (
              <div className="empty">
                <span className="empty-icon">⇄</span>
                <h3>Your first event starts here</h3>
                <p>Use the form below to add an event to your ledger.</p>
              </div>
            )}
            {!!events.data?.length && (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>EVENT</th>
                      <th>DESTINATION</th>
                      <th>STATUS</th>
                      <th>ACCEPTED</th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.data.map((event) => (
                      <tr key={event.id}>
                        <td>
                          <strong>{event.eventType}</strong>
                          <code title={event.id}>{event.id.slice(0, 8)}</code>
                        </td>
                        <td className="destination" title={event.destination}>
                          {event.destination}
                        </td>
                        <td>
                          <span
                            className={
                              event.status === "FORWARDED"
                                ? "status forwarded"
                                : "status accepted"
                            }
                          >
                            {event.status === "FORWARDED"
                              ? "Forwarded"
                              : "Accepted"}
                          </span>
                        </td>
                        <td className="timestamp">
                          {formatTime(event.createdAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          <div className="lower-grid">
            <section className="panel" id="create-event">
              <div className="panel-heading">
                <div>
                  <h2>Create an event</h2>
                  <p>Persist a payload and queue it for delivery processing.</p>
                </div>
                <span className="step">01</span>
              </div>
              <form onSubmit={submit}>
                <div className="form-row">
                  <label>
                    Event type
                    <input
                      required
                      pattern="[A-Za-z0-9_.-]{1,100}"
                      maxLength={100}
                      value={eventType}
                      onChange={(event) => setEventType(event.target.value)}
                    />
                  </label>
                  <label>
                    Destination URL
                    <input
                      required
                      type="url"
                      value={destination}
                      onChange={(event) => setDestination(event.target.value)}
                    />
                  </label>
                </div>
                <label>
                  JSON payload
                  <textarea
                    required
                    rows={6}
                    spellCheck={false}
                    value={payload}
                    onChange={(event) => setPayload(event.target.value)}
                  />
                </label>
                <label>
                  Idempotency key
                  <div className="key-field">
                    <input
                      required
                      maxLength={200}
                      value={key}
                      onChange={(event) => setKey(event.target.value)}
                    />
                    <button
                      type="button"
                      aria-label="Generate new idempotency key"
                      onClick={() => setKey(crypto.randomUUID())}
                    >
                      ↻
                    </button>
                  </div>
                </label>
                <p className="field-help">
                  Retry with the same key to return the existing event.
                </p>
                {(validation || create.isError) && (
                  <p className="notice error" role="alert">
                    {validation || create.error?.message}
                  </p>
                )}
                {create.isSuccess && (
                  <p className="notice success" role="status">
                    {create.data.replayed
                      ? "Existing event returned. No duplicate was created."
                      : "Event accepted. It will appear in the delivery queue shortly."}
                  </p>
                )}
                <div className="form-footer">
                  <span>Durable intake · Safe retries</span>
                  <button className="primary" disabled={create.isPending}>
                    {create.isPending ? "Accepting…" : "Accept event →"}
                  </button>
                </div>
              </form>
            </section>
            <section className="panel queue" id="queue">
              <div className="panel-heading">
                <div>
                  <h2>Delivery queue</h2>
                  <p>Jobs received by the delivery service.</p>
                </div>
                <span className="step">02</span>
              </div>
              {deliveries.isPending && (
                <p className="empty">Loading delivery jobs…</p>
              )}
              {deliveries.isError && (
                <p className="notice error" role="alert">
                  Delivery service: {deliveries.error.message}
                </p>
              )}
              {deliveries.data?.length === 0 && (
                <p className="empty">
                  No delivery jobs yet. Accepted events are forwarded
                  automatically.
                </p>
              )}
              <div className="queue-list">
                {deliveries.data?.map((job) => (
                  <div className="queue-item" key={job.eventId}>
                    <span className="queue-icon">↗</span>
                    <div>
                      <strong>{job.eventType}</strong>
                      <code>{job.eventId.slice(0, 8)}</code>
                    </div>
                    <span className="status pending">Pending</span>
                  </div>
                ))}
              </div>
              <div className="queue-note">
                <strong>Intake is ready. Dispatch comes next.</strong>
                <p>
                  Pending jobs have not been sent to their destinations.
                  Outbound delivery and retries are the next milestone.
                </p>
              </div>
            </section>
          </div>
          <footer>
            RelayForge <span>Built for the moments between systems.</span>
            <a href="https://github.com/Rajansharma0786/relayforge/tree/main/docs">
              Documentation ↗
            </a>
          </footer>
        </div>
      </main>
    </div>
  );
}
