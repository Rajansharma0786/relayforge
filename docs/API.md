# HTTP API contract

The local public base URL is http://127.0.0.1:8080 through api-gateway.

## Event intake

POST /api/events accepts:

```json
{
  "eventType": "invoice.paid",
  "destination": "https://example.com/hooks",
  "payload": { "invoiceId": "inv_123", "amount": 4999 }
}
```

Content-Type must be application/json. Idempotency-Key is optional; when supplied, it must be nonblank and at most 200 characters.

- 201 Created: a new event and its outbox row are committed. Location identifies /api/events/{id}.
- 200 OK: the same key and same event returned the original event.
- 409 Conflict: the key was reused with another event type, destination, or payload.
- 400 Bad Request: invalid JSON, shape, event type, destination, or key.

The Idempotency-Replayed response header distinguishes new and replayed requests. Nested object-key ordering is normalized for comparison; arrays retain order. No key means each submission creates a distinct event.

An event response contains id, eventType, destination, payload, status, and createdAt. Status starts ACCEPTED and becomes FORWARDED after the delivery service acknowledges durable intake. createdAt is an ISO timestamp.

## Event queries

GET /api/events?limit=20 returns newest events first. limit must be 1-100. GET /api/events/{uuid} returns one event, or 404.

## Delivery queries

GET /api/deliveries?limit=20 returns newest jobs first, with the same limit bounds. GET /api/deliveries/{eventId} returns one job, or 404.

A job has eventId, eventType, destination, payload, status, and createdAt. The current status is PENDING. This does not claim delivery to the external URL.

## Internal forwarding

POST /internal/deliveries exists only on delivery-service and requires X-RelayForge-Token. The gateway returns 404 for this path.

Its request contains id, eventType, destination, and payload. Matching repeated event IDs return 200, new IDs return 201, conflicting IDs return 409, invalid requests return 400, and absent/incorrect service tokens return 401.

The producer may send additional event metadata, which is ignored by the receiver. Versioned contract evolution is a future milestone.

## Errors and health

MVC API errors use problem-detail responses with status and detail fields where applicable. Proxy connection failures may return a gateway error body; the frontend handles non-JSON failures.

Each Java application exposes /actuator/health and /actuator/health/readiness. Stateful services also expose actuator metrics. Health endpoints do not establish that downstream external webhook delivery is implemented.
