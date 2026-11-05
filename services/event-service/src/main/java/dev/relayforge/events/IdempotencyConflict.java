package dev.relayforge.events;

public class IdempotencyConflict extends RuntimeException {
    public IdempotencyConflict() { super("Idempotency key already belongs to a different event."); }
}
