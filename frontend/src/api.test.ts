import { afterEach, expect, test, vi } from "vitest";
import { createEvent, listEvents } from "./api";
afterEach(() => vi.unstubAllGlobals());
test("uses problem details for API failures", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: "Conflicting event." }), {
        status: 409,
      }),
    ),
  );
  await expect(listEvents()).rejects.toThrow("Conflicting event.");
});
test("handles a non-JSON upstream failure", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response("Bad gateway", { status: 502 })),
  );
  await expect(listEvents()).rejects.toThrow("HTTP 502");
});
test("preserves the idempotency key and replay response", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ id: "evt-1" }), {
      status: 200,
      headers: { "Idempotency-Replayed": "true" },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const result = await createEvent(
    { eventType: "test", destination: "https://example.com", payload: {} },
    "stable-key",
  );
  expect(result.replayed).toBe(true);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/events",
    expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "Idempotency-Key": "stable-key" }),
    }),
  );
});
