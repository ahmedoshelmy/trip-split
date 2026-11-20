package dev.tripsplit.trips.application;

import dev.tripsplit.money.Money;
import dev.tripsplit.trips.TripChanged;
import dev.tripsplit.trips.domain.ExpenseDraft;
import dev.tripsplit.trips.domain.NotFoundException;
import dev.tripsplit.trips.domain.Trip;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command side. Each command runs in one transaction: load by replay, check {@code If-Match},
 * decide, append, update the inline projections, publish module events. The module events are
 * stored in Spring Modulith's publication registry in the same transaction (a transactional
 * outbox), so listeners such as the timeline never miss a change.
 */
@Service
@Transactional
public class TripCommands {

  private final TripStreams streams;
  private final TripReadModels readModels;
  private final ApplicationEventPublisher events;
  private final ActivityDescriber describer;
  private final Clock clock;

  public TripCommands(
      TripStreams streams,
      TripReadModels readModels,
      ApplicationEventPublisher events,
      ActivityDescriber describer,
      Clock clock) {
    this.streams = streams;
    this.readModels = readModels;
    this.events = events;
    this.describer = describer;
    this.clock = clock;
  }

  public CommandResult createTrip(String name, String baseCurrency, List<String> memberNames) {
    var trip =
        Trip.create(
            UUID.randomUUID(), name, baseCurrency, memberNames, UUID::randomUUID, clock.instant());
    return commit(trip, trip.id());
  }

  public CommandResult addMember(UUID tripId, Long expectedVersion, String memberName) {
    var memberId = UUID.randomUUID();
    return change(tripId, expectedVersion, memberId, t -> t.join(memberId, memberName, now()));
  }

  public CommandResult addExpense(UUID tripId, Long expectedVersion, ExpenseDraft draft) {
    var expenseId = UUID.randomUUID();
    return change(tripId, expectedVersion, expenseId, t -> t.addExpense(expenseId, draft, now()));
  }

  public CommandResult voidExpense(
      UUID tripId, Long expectedVersion, UUID expenseId, String reason) {
    return change(tripId, expectedVersion, null, t -> t.voidExpense(expenseId, reason, now()));
  }

  public CommandResult recordSettlement(
      UUID tripId, Long expectedVersion, UUID from, UUID to, Money amount) {
    var settlementId = UUID.randomUUID();
    return change(
        tripId,
        expectedVersion,
        settlementId,
        t -> t.recordSettlement(settlementId, from, to, amount, now()));
  }

  public CommandResult voidSettlement(UUID tripId, Long expectedVersion, UUID settlementId) {
    return change(tripId, expectedVersion, null, t -> t.voidSettlement(settlementId, now()));
  }

  /** Replays every trip and rewrites the inline projections (after a projection change). */
  public int rebuildReadModels() {
    var ids = streams.allTripIds();
    ids.forEach(id -> streams.load(id).ifPresent(readModels::project));
    return ids.size();
  }

  private CommandResult change(
      UUID tripId, Long expectedVersion, UUID createdId, Consumer<Trip> decision) {
    var trip = streams.load(tripId).orElseThrow(() -> new NotFoundException("No trip " + tripId));
    ConcurrencyGuard.check(trip, expectedVersion);
    decision.accept(trip);
    return commit(trip, createdId);
  }

  private CommandResult commit(Trip trip, UUID createdId) {
    var firstNewVersion = trip.committedVersion() + 1;
    var newEvents = trip.uncommittedEvents();
    var version = streams.append(trip);
    trip.markCommitted();
    readModels.project(trip);
    for (int i = 0; i < newEvents.size(); i++) {
      events.publishEvent(describer.describe(trip, firstNewVersion + i, newEvents.get(i)));
    }
    events.publishEvent(new TripChanged(trip.id(), version));
    return new CommandResult(trip.id(), version, createdId);
  }

  private Instant now() {
    return clock.instant();
  }
}
