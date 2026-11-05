package dev.relayforge.delivery;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class DeliveryController {
    private final DeliveryService deliveries;
    DeliveryController(DeliveryService deliveries) { this.deliveries = deliveries; }

    @PostMapping("/internal/deliveries")
    ResponseEntity<DeliveryView> accept(@Valid @RequestBody DeliveryRequest input) {
        var result = deliveries.accept(input);
        return (result.replayed() ? ResponseEntity.ok() :
                ResponseEntity.created(URI.create("/api/deliveries/" + result.job().eventId()))).body(result.job());
    }

    @GetMapping("/api/deliveries")
    List<DeliveryView> list(@RequestParam(defaultValue = "20") int limit) { return deliveries.list(limit); }

    @GetMapping("/api/deliveries/{id}")
    DeliveryView get(@PathVariable UUID id) { return deliveries.get(id); }
}
