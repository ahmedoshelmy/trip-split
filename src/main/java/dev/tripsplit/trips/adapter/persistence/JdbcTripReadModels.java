package dev.tripsplit.trips.adapter.persistence;

import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.BalanceSheet.MemberBalance;
import dev.tripsplit.trips.TripSummary;
import dev.tripsplit.trips.application.TripReadModels;
import dev.tripsplit.trips.domain.Trip;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Inline projections: rewritten from the aggregate inside the command's transaction, so they are
 * never behind the event stream.
 */
@Repository
class JdbcTripReadModels implements TripReadModels {

  private final JdbcClient jdbc;

  JdbcTripReadModels(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void project(Trip trip) {
    jdbc.sql(
            """
            INSERT INTO trip_summaries
                (trip_id, name, base_currency, member_count, total_spent_minor, version, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (trip_id) DO UPDATE SET
                name = EXCLUDED.name, member_count = EXCLUDED.member_count,
                total_spent_minor = EXCLUDED.total_spent_minor, version = EXCLUDED.version,
                updated_at = EXCLUDED.updated_at
            """)
        .params(
            trip.id(),
            trip.name(),
            trip.baseCurrency(),
            trip.members().size(),
            trip.totalSpent().minor(),
            trip.version())
        .update();
    jdbc.sql("DELETE FROM trip_balances WHERE trip_id = ?").param(trip.id()).update();
    var position = 0;
    for (var entry : trip.balances().entrySet()) {
      jdbc.sql(
              """
              INSERT INTO trip_balances (trip_id, member_id, member_name, position, balance_minor)
              VALUES (?, ?, ?, ?, ?)
              """)
          .params(
              trip.id(),
              entry.getKey(),
              trip.member(entry.getKey()).name(),
              position++,
              entry.getValue())
          .update();
    }
  }

  @Override
  public Optional<BalanceSheet> balances(UUID tripId) {
    return jdbc.sql("SELECT base_currency, version FROM trip_summaries WHERE trip_id = ?")
        .param(tripId)
        .query((rs, i) -> new Header(rs.getString(1), rs.getLong(2)))
        .optional()
        .map(header ->
            new BalanceSheet(
                tripId, header.version(), header.currency(), null, memberBalances(tripId)));
  }

  @Override
  public List<TripSummary> summaries() {
    return jdbc.sql(
            """
            SELECT trip_id, name, base_currency, member_count, total_spent_minor, version, updated_at
            FROM trip_summaries ORDER BY updated_at DESC
            """)
        .query((rs, i) ->
            new TripSummary(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getInt(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getObject(7, Timestamp.class).toInstant()))
        .list();
  }

  private List<MemberBalance> memberBalances(UUID tripId) {
    return jdbc.sql(
            """
            SELECT member_id, member_name, balance_minor FROM trip_balances
            WHERE trip_id = ? ORDER BY position
            """)
        .param(tripId)
        .query((rs, i) ->
            new MemberBalance(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)))
        .list();
  }

  private record Header(String currency, long version) {}
}
