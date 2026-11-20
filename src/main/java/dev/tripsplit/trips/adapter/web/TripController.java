package dev.tripsplit.trips.adapter.web;

import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.TripSummary;
import dev.tripsplit.trips.adapter.web.TripRequests.AddExpense;
import dev.tripsplit.trips.adapter.web.TripRequests.AddMember;
import dev.tripsplit.trips.adapter.web.TripRequests.CreateTrip;
import dev.tripsplit.trips.adapter.web.TripRequests.RecordSettlement;
import dev.tripsplit.trips.adapter.web.TripRequests.Split;
import dev.tripsplit.trips.adapter.web.TripRequests.VoidExpense;
import dev.tripsplit.trips.adapter.web.TripResponses.CommandView;
import dev.tripsplit.trips.adapter.web.TripResponses.EventView;
import dev.tripsplit.trips.adapter.web.TripResponses.TripView;
import dev.tripsplit.trips.application.CommandResult;
import dev.tripsplit.trips.application.TripCommands;
import dev.tripsplit.trips.application.TripQueryService;
import dev.tripsplit.trips.domain.ExpenseDraft;
import dev.tripsplit.trips.domain.NotFoundException;
import dev.tripsplit.trips.domain.TripRuleViolation;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for trips. Every command accepts {@code If-Match: "<version>"} (412 when stale) and
 * answers with the new version as {@code ETag}.
 */
@RestController
@RequestMapping("/api/trips")
class TripController {

  private final TripCommands commands;
  private final TripQueryService queries;

  TripController(TripCommands commands, TripQueryService queries) {
    this.commands = commands;
    this.queries = queries;
  }

  @GetMapping
  List<TripSummary> list() {
    return queries.trips();
  }

  @PostMapping
  ResponseEntity<TripView> create(@Valid @RequestBody CreateTrip body) {
    var members = body.members() == null ? List.<String>of() : body.members();
    var result = commands.createTrip(body.name(), body.baseCurrency(), members);
    var trip = queries.trip(result.tripId()).orElseThrow();
    return ResponseEntity.created(URI.create("/api/trips/" + result.tripId()))
        .eTag(Versions.etag(result.version()))
        .body(TripView.of(trip, null));
  }

  @GetMapping("/{tripId}")
  ResponseEntity<TripView> get(
      @PathVariable UUID tripId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant asOf) {
    var trip =
        (asOf == null ? queries.trip(tripId) : queries.tripAsOf(tripId, asOf))
            .orElseThrow(() -> notFound(tripId, asOf));
    return ResponseEntity.ok().eTag(Versions.etag(trip.version())).body(TripView.of(trip, asOf));
  }

  @GetMapping("/{tripId}/balances")
  ResponseEntity<BalanceSheet> balances(
      @PathVariable UUID tripId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant asOf) {
    var sheet =
        (asOf == null ? queries.balances(tripId) : queries.balancesAsOf(tripId, asOf))
            .orElseThrow(() -> notFound(tripId, asOf));
    return ResponseEntity.ok().eTag(Versions.etag(sheet.version())).body(sheet);
  }

  @GetMapping("/{tripId}/events")
  List<EventView> events(@PathVariable UUID tripId) {
    var history = queries.history(tripId);
    if (history.isEmpty()) {
      throw notFound(tripId, null);
    }
    return history.stream().map(e -> new EventView(e.version(), e.type(), e.event())).toList();
  }

  @PostMapping("/{tripId}/members")
  ResponseEntity<CommandView> addMember(
      @PathVariable UUID tripId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody AddMember body) {
    return created(commands.addMember(tripId, Versions.fromIfMatch(ifMatch), body.name()));
  }

  @PostMapping("/{tripId}/expenses")
  ResponseEntity<CommandView> addExpense(
      @PathVariable UUID tripId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody AddExpense body) {
    var amount = parse(body.amount(), body.currency());
    var draft =
        new ExpenseDraft(
            body.description(), body.paidBy(), amount, rate(body.rate()), rule(body.split(), body));
    return created(commands.addExpense(tripId, Versions.fromIfMatch(ifMatch), draft));
  }

  @PostMapping("/{tripId}/expenses/{expenseId}/void")
  ResponseEntity<CommandView> voidExpense(
      @PathVariable UUID tripId,
      @PathVariable UUID expenseId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody(required = false) VoidExpense body) {
    var reason = body == null ? null : body.reason();
    return ok(commands.voidExpense(tripId, Versions.fromIfMatch(ifMatch), expenseId, reason));
  }

  @PostMapping("/{tripId}/settlements")
  ResponseEntity<CommandView> recordSettlement(
      @PathVariable UUID tripId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody RecordSettlement body) {
    var currency = queries.trip(tripId).orElseThrow(() -> notFound(tripId, null)).baseCurrency();
    var amount = parse(body.amount(), currency);
    return created(
        commands.recordSettlement(
            tripId, Versions.fromIfMatch(ifMatch), body.from(), body.to(), amount));
  }

  @PostMapping("/{tripId}/settlements/{settlementId}/void")
  ResponseEntity<CommandView> voidSettlement(
      @PathVariable UUID tripId,
      @PathVariable UUID settlementId,
      @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return ok(commands.voidSettlement(tripId, Versions.fromIfMatch(ifMatch), settlementId));
  }

  private static SplitRule rule(Split split, AddExpense body) {
    return switch (split.kind()) {
      case EQUAL -> new SplitRule.Equal(require(split.participants(), "participants"));
      case EXACT -> {
        var amounts = new LinkedHashMap<UUID, Long>();
        require(split.amounts(), "amounts")
            .forEach((id, value) -> amounts.put(id, parse(value, body.currency()).minor()));
        yield new SplitRule.Exact(amounts);
      }
      case SHARES -> new SplitRule.Shares(require(split.shares(), "shares"));
    };
  }

  private static <T> T require(T value, String field) {
    if (value == null || (value instanceof Map<?, ?> m && m.isEmpty())) {
      throw new TripRuleViolation("split." + field + " is required for this split");
    }
    return value;
  }

  private static Money parse(String amount, String currency) {
    try {
      return Money.parse(amount, currency.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new TripRuleViolation(e.getMessage());
    }
  }

  private static BigDecimal rate(String rate) {
    if (rate == null || rate.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(rate.strip());
    } catch (NumberFormatException e) {
      throw new TripRuleViolation("Rate is not a number: " + rate);
    }
  }

  private static NotFoundException notFound(UUID tripId, Instant asOf) {
    return new NotFoundException(
        asOf == null ? "No trip " + tripId : "Trip %s did not exist at %s".formatted(tripId, asOf));
  }

  private static ResponseEntity<CommandView> created(CommandResult result) {
    return ResponseEntity.status(201)
        .eTag(Versions.etag(result.version()))
        .body(new CommandView(result.tripId(), result.version(), result.createdId()));
  }

  private static ResponseEntity<CommandView> ok(CommandResult result) {
    return ResponseEntity.ok()
        .eTag(Versions.etag(result.version()))
        .body(new CommandView(result.tripId(), result.version(), result.createdId()));
  }
}
