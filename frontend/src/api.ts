export interface EventRecord {
  id: string;
  eventType: string;
  destination: string;
  payload: Record<string, unknown>;
  status: "ACCEPTED" | "FORWARDED";
  createdAt: string;
}
export interface DeliveryRecord {
  eventId: string;
  eventType: string;
  destination: string;
  payload: Record<string, unknown>;
  status: "PENDING";
  createdAt: string;
}
export interface CreateEventInput {
  eventType: string;
  destination: string;
  payload: Record<string, unknown>;
}

async function checked(response: Response): Promise<Response> {
  if (response.ok) return response;
  let detail = `Request failed (HTTP ${response.status}).`;
  try {
    const problem = (await response.json()) as { detail?: unknown };
    if (typeof problem.detail === "string") detail = problem.detail;
  } catch {
    /* retain an actionable fallback for non-JSON upstream failures */
  }
  throw new Error(detail);
}
export async function listEvents(signal?: AbortSignal): Promise<EventRecord[]> {
  return (await checked(await fetch("/api/events", { signal }))).json();
}
export async function listDeliveries(
  signal?: AbortSignal,
): Promise<DeliveryRecord[]> {
  return (await checked(await fetch("/api/deliveries", { signal }))).json();
}
export async function createEvent(input: CreateEventInput, key: string) {
  const response = await checked(
    await fetch("/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": key },
      body: JSON.stringify(input),
    }),
  );
  return {
    event: (await response.json()) as EventRecord,
    replayed: response.headers.get("Idempotency-Replayed") === "true",
  };
}
