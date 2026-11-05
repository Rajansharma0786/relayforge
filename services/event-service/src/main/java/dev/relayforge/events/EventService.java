package dev.relayforge.events;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class EventService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TransactionTemplate transactions;

    public EventService(JdbcTemplate jdbc, JsonMapper json, TransactionTemplate transactions) {
        this.jdbc = jdbc; this.json = json; this.transactions = transactions;
    }

    public record Acceptance(EventView event, boolean replayed) {}

    public Acceptance accept(CreateEvent input, String key) {
        validateDestination(input.destination());
        if (key != null && (key.isBlank() || key.length() > 200)) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-200 nonblank characters.");
        }
        String payload = json.writeValueAsString(sorted(input.payload()));
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO events(id,event_type,destination,payload,created_at,idempotency_key) VALUES(?,?,?,?,?,?)",
                        id, input.eventType(), input.destination(), payload, Timestamp.from(now), key);
                jdbc.update("INSERT INTO event_outbox(event_id,next_attempt_at) VALUES(?,?)", id, Timestamp.from(now));
            });
            return new Acceptance(get(id), false);
        } catch (DuplicateKeyException duplicate) {
            // The failed transaction is rolled back before reading the winning insert.
            if (key == null) throw duplicate;
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,event_type,destination,payload FROM events WHERE idempotency_key=?", key);
            if (rows.isEmpty()) throw duplicate;
            Map<String, Object> existing = rows.getFirst();
            if (!input.eventType().equals(existing.get("event_type")) ||
                    !input.destination().equals(existing.get("destination")) ||
                    !payload.equals(existing.get("payload").toString())) {
                throw new IdempotencyConflict();
            }
            return new Acceptance(get((UUID) existing.get("id")), true);
        }
    }

    public EventView get(UUID id) {
        return jdbc.query("SELECT * FROM events WHERE id=?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found."));
    }

    public List<EventView> list(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100.");
        return jdbc.query("SELECT * FROM events ORDER BY created_at DESC,id DESC LIMIT ?", this::map, limit);
    }

    EventView map(ResultSet row, int index) throws SQLException {
        return new EventView(row.getObject("id", UUID.class), row.getString("event_type"),
                row.getString("destination"), json.readValue(row.getString("payload"), new TypeReference<>() {}),
                row.getString("status"), row.getTimestamp("created_at").toInstant());
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, nested) -> result.put((String) key, sorted(nested)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(EventService::sorted).toList();
        return value;
    }

    static void validateDestination(String value) {
        try {
            URI uri = URI.create(value);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getFragment() != null ||
                    uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException("destination must be an absolute HTTP(S) URL without credentials or a fragment.");
        }
    }
}
