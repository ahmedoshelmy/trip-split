package dev.tripsplit.eventstore.internal;

import dev.tripsplit.eventstore.ConcurrencyConflictException;
import dev.tripsplit.eventstore.EventStore;
import dev.tripsplit.eventstore.NewEvent;
import dev.tripsplit.eventstore.StoredEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres implementation. The unique key on {@code (stream_id, version)} is the whole concurrency
 * story: two writers that read version N both try to insert N+1 and exactly one wins.
 */
@Repository
class JdbcEventStore implements EventStore {

  private static final String SELECT =
      "SELECT stream_id, version, type, payload::text AS payload, occurred_at FROM events ";

  private final JdbcClient jdbc;

  JdbcEventStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StoredEvent> read(UUID streamId) {
    return jdbc.sql(SELECT + "WHERE stream_id = ? ORDER BY version")
        .param(streamId)
        .query(JdbcEventStore::map)
        .list();
  }

  @Override
  @Transactional(readOnly = true)
  public List<StoredEvent> readUntil(UUID streamId, Instant asOf) {
    return jdbc.sql(SELECT + "WHERE stream_id = ? AND occurred_at <= ? ORDER BY version")
        .param(streamId)
        .param(Timestamp.from(asOf))
        .query(JdbcEventStore::map)
        .list();
  }

  @Override
  @Transactional
  public long append(
      UUID streamId, String streamType, long expectedVersion, List<NewEvent> events) {
    var current =
        jdbc.sql("SELECT COALESCE(MAX(version), 0) FROM events WHERE stream_id = ?")
            .param(streamId)
            .query(Long.class)
            .single();
    if (current != expectedVersion) {
      throw new ConcurrencyConflictException(streamId, expectedVersion);
    }
    var version = expectedVersion;
    try {
      for (var event : events) {
        version++;
        jdbc.sql(
                """
                INSERT INTO events (stream_id, stream_type, version, type, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?)
                """)
            .params(
                streamId,
                streamType,
                version,
                event.type(),
                event.payloadJson(),
                Timestamp.from(event.occurredAt()))
            .update();
      }
    } catch (DuplicateKeyException e) {
      throw new ConcurrencyConflictException(streamId, expectedVersion);
    }
    return version;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> streamIds(String streamType) {
    return jdbc.sql(
            "SELECT stream_id FROM events WHERE stream_type = ? AND version = 1 ORDER BY position")
        .param(streamType)
        .query(UUID.class)
        .list();
  }

  private static StoredEvent map(ResultSet rs, int row) throws SQLException {
    return new StoredEvent(
        rs.getObject("stream_id", UUID.class),
        rs.getLong("version"),
        rs.getString("type"),
        rs.getString("payload"),
        rs.getTimestamp("occurred_at").toInstant());
  }
}
