package dev.relayforge.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateEvent(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}") String eventType,
        @NotBlank @Size(max = 2048) String destination,
        @NotNull Map<String, Object> payload) {}
