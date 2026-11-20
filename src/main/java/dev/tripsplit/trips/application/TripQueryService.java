package dev.tripsplit.trips.application;

import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.BalanceSheet.MemberBalance;
import dev.tripsplit.trips.TripQueries;
import dev.tripsplit.trips.TripSummary;
import dev.tripsplit.trips.domain.Trip;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Query side: projections for "now", replay for anything else. */
@Service
@Transactional(readOnly = true)
public class TripQueryService implements TripQueries {

  private final TripStreams streams;
  private final TripReadModels readModels;

  public TripQueryService(TripStreams streams, TripReadModels readModels) {
    this.streams = streams;
    this.readModels = readModels;
  }

  public Optional<Trip> trip(UUID tripId) {
    return streams.load(tripId);
  }

  public Optional<Trip> tripAsOf(UUID tripId, Instant asOf) {
    return streams.loadAsOf(tripId, asOf);
  }

  public List<TripStreams.RecordedTripEvent> history(UUID tripId) {
    return streams.history(tripId);
  }

  public List<TripSummary> trips() {
    return readModels.summaries();
  }

  @Override
  public Optional<BalanceSheet> balances(UUID tripId) {
    return readModels.balances(tripId);
  }

  @Override
  public Optional<BalanceSheet> balancesAsOf(UUID tripId, Instant asOf) {
    return streams.loadAsOf(tripId, asOf).map(trip -> sheet(trip, asOf));
  }

  /** Builds a balance sheet straight from an aggregate. */
  public static BalanceSheet sheet(Trip trip, Instant asOf) {
    var rows =
        trip.balances().entrySet().stream()
            .map(e -> new MemberBalance(e.getKey(), trip.member(e.getKey()).name(), e.getValue()))
            .toList();
    return new BalanceSheet(trip.id(), trip.version(), trip.baseCurrency(), asOf, rows);
  }
}
