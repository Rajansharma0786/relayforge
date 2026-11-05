package dev.relayforge.delivery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeliveryView(UUID eventId, String eventType, String destination, Map<String, Object> payload,
                           String status, Instant createdAt) {}
