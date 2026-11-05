package dev.relayforge.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventView(UUID id, String eventType, String destination, Map<String, Object> payload,
                        String status, Instant createdAt) {}
