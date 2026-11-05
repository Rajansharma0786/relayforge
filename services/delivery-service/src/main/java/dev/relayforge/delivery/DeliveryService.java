package dev.relayforge.delivery;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class DeliveryService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    DeliveryService(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    record Acceptance(DeliveryView job, boolean replayed) {}

    Acceptance accept(DeliveryRequest input) {
        String payload = json.writeValueAsString(sorted(input.payload()));
        try {
            jdbc.update("INSERT INTO delivery_jobs(event_id,event_type,destination,payload,created_at) VALUES(?,?,?,?,?)",
                    input.id(), input.eventType(), input.destination(), payload, Timestamp.from(Instant.now()));
            return new Acceptance(get(input.id()), false);
        } catch (DuplicateKeyException duplicate) {
            var existing = get(input.id());
            if (!existing.eventType().equals(input.eventType()) ||
                    !existing.destination().equals(input.destination()) ||
                    !json.writeValueAsString(sorted(existing.payload())).equals(payload)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Event ID already belongs to a different delivery.");
            }
            return new Acceptance(existing, true);
        }
    }

    DeliveryView get(UUID id) {
        return jdbc.query("SELECT * FROM delivery_jobs WHERE event_id=?", this::map, id)
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery not found."));
    }

    List<DeliveryView> list(int limit) {
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
        return jdbc.query("SELECT * FROM delivery_jobs ORDER BY created_at DESC,event_id DESC LIMIT ?", this::map, limit);
    }

    private DeliveryView map(ResultSet row, int index) throws SQLException {
        return new DeliveryView(row.getObject("event_id", UUID.class), row.getString("event_type"),
                row.getString("destination"), json.readValue(row.getString("payload"), new TypeReference<>() {}),
                row.getString("status"), row.getTimestamp("created_at").toInstant());
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, nested) -> result.put((String) key, sorted(nested)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(DeliveryService::sorted).toList();
        return value;
    }
}
