package dev.relayforge.delivery;

import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.UUID;

public record DeliveryRequest(@NotNull UUID id,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}") String eventType,
        @NotBlank @Size(max = 2048) String destination, @NotNull Map<String, Object> payload) {}
