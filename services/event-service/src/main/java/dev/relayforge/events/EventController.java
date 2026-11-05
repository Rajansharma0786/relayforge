package dev.relayforge.events;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
class EventController {
    private final EventService events;
    EventController(EventService events) { this.events = events; }

    @PostMapping
    ResponseEntity<EventView> create(@Valid @RequestBody CreateEvent input,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var accepted = events.accept(input, key);
        var builder = accepted.replayed() ? ResponseEntity.ok() :
                ResponseEntity.created(URI.create("/api/events/" + accepted.event().id()));
        return builder.header("Idempotency-Replayed", Boolean.toString(accepted.replayed())).body(accepted.event());
    }

    @GetMapping
    List<EventView> list(@RequestParam(defaultValue = "20") int limit) { return events.list(limit); }

    @GetMapping("/{id}")
    EventView get(@PathVariable UUID id) { return events.get(id); }
}
