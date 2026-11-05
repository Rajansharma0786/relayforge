import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, expect, test, vi } from "vitest";
import App from "./App";
import * as api from "./api";
vi.mock("./api");
const event: api.EventRecord = {
  id: "66aa9d9b-561a-47ef-bbdc-f90c5fa5450e",
  eventType: "payment.completed",
  destination: "https://example.com/hook",
  payload: { amount: 100 },
  status: "FORWARDED",
  createdAt: "2026-11-05T10:00:00Z",
};
beforeEach(() => {
  vi.mocked(api.listEvents).mockResolvedValue([]);
  vi.mocked(api.listDeliveries).mockResolvedValue([]);
});
function show() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  );
}
test("renders actual event and delivery records", async () => {
  vi.mocked(api.listEvents).mockResolvedValue([event]);
  vi.mocked(api.listDeliveries).mockResolvedValue([
    { ...event, eventId: event.id, status: "PENDING" },
  ]);
  show();
  expect((await screen.findAllByText("payment.completed")).length).toBe(2);
  expect(screen.getByText("Forwarded")).toBeVisible();
  expect(screen.getByText("Pending")).toBeVisible();
});
test("reports API failure and keeps the creation form available", async () => {
  vi.mocked(api.listEvents).mockRejectedValue(
    new Error("Gateway unavailable."),
  );
  show();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Gateway unavailable.",
  );
  expect(screen.getByRole("button", { name: "Accept event →" })).toBeEnabled();
});
test("rejects an array payload before making a request", async () => {
  show();
  fireEvent.change(screen.getByLabelText("JSON payload"), {
    target: { value: "[]" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Accept event →" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Payload must be a JSON object.",
  );
  expect(api.createEvent).not.toHaveBeenCalled();
});
test("submits typed event data and explains an idempotent replay", async () => {
  vi.mocked(api.createEvent).mockResolvedValue({ event, replayed: true });
  show();
  fireEvent.click(screen.getByRole("button", { name: "Accept event →" }));
  await waitFor(() =>
    expect(api.createEvent).toHaveBeenCalledWith(
      {
        eventType: "invoice.paid",
        destination: "https://example.com/webhooks",
        payload: { invoiceId: "inv_123", amount: 4999 },
      },
      expect.any(String),
    ),
  );
  expect(await screen.findByRole("status")).toHaveTextContent(
    "No duplicate was created.",
  );
});
