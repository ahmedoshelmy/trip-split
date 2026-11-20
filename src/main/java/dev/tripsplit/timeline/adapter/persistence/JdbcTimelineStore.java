package dev.tripsplit.timeline.adapter.persistence;

import dev.tripsplit.timeline.application.TimelineEntry;
import dev.tripsplit.timeline.application.TimelineStore;
import dev.tripsplit.trips.TripActivity;
import dev.tripsplit.trips.TripActivity.Kind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTimelineStore implements TimelineStore {

  private static final EnumSet<Kind> UNDOABLE =
      EnumSet.of(Kind.EXPENSE_ADDED, Kind.SETTLEMENT_RECORDED);

  private final JdbcClient jdbc;

  JdbcTimelineStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insert(TripActivity a) {
    return jdbc.sql(
                """
                INSERT INTO timeline_entries (trip_id, version, kind, ref_id, summary, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (trip_id, version) DO NOTHING
                """)
            .params(
                a.tripId(),
                a.version(),
                a.kind().name(),
                a.refId(),
                a.summary(),
                Timestamp.from(a.occurredAt()))
            .update()
        == 1;
  }

  @Override
  public void markUndone(UUID tripId, UUID refId, Instant at) {
    jdbc.sql(
            """
            UPDATE timeline_entries SET undone_at = ?
            WHERE trip_id = ? AND ref_id = ? AND kind IN ('EXPENSE_ADDED', 'SETTLEMENT_RECORDED')
            """)
        .params(Timestamp.from(at), tripId, refId)
        .update();
  }

  @Override
  public List<TimelineEntry> forTrip(UUID tripId) {
    return jdbc.sql(
            """
            SELECT version, kind, ref_id, summary, occurred_at, undone_at
            FROM timeline_entries WHERE trip_id = ? ORDER BY version DESC
            """)
        .param(tripId)
        .query(JdbcTimelineStore::map)
        .list();
  }

  private static TimelineEntry map(ResultSet rs, int row) throws SQLException {
    var kind = Kind.valueOf(rs.getString("kind"));
    var undone = rs.getTimestamp("undone_at");
    return new TimelineEntry(
        rs.getLong("version"),
        kind,
        rs.getObject("ref_id", UUID.class),
        rs.getString("summary"),
        rs.getTimestamp("occurred_at").toInstant(),
        undone == null ? null : undone.toInstant(),
        undone == null && UNDOABLE.contains(kind));
  }
}
