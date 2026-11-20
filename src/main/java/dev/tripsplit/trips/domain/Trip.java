package dev.tripsplit.trips.domain;

import dev.tripsplit.expenses.domain.ExpenseSplitter;
import dev.tripsplit.expenses.domain.InvalidSplitException;
import dev.tripsplit.expenses.domain.Share;
import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import dev.tripsplit.trips.domain.TripEvent.ExpenseAdded;
import dev.tripsplit.trips.domain.TripEvent.ExpenseVoided;
import dev.tripsplit.trips.domain.TripEvent.MemberJoined;
import dev.tripsplit.trips.domain.TripEvent.SettlementRecorded;
import dev.tripsplit.trips.domain.TripEvent.SettlementVoided;
import dev.tripsplit.trips.domain.TripEvent.TripCreated;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The trip aggregate. State is never stored: it is rebuilt by replaying the trip's events, and
 * every command validates against that state and then records new events.
 *
 * <p>Commands follow one pattern: check the rules, build the event, {@link #raise} it (apply to
 * state and remember it as uncommitted).
 */
public final class Trip {

  public static final int MAX_NAME_LENGTH = 60;
  public static final int MAX_DESCRIPTION_LENGTH = 120;

  private UUID id;
  private String name;
  private String baseCurrency;
  private Instant createdAt;
  private long version;
  private final Map<UUID, Member> members = new LinkedHashMap<>();
  private final Map<UUID, Expense> expenses = new LinkedHashMap<>();
  private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
  private final List<TripEvent> uncommitted = new ArrayList<>();

  private Trip() {}

  /** Starts a trip with its first members (the people planning it). */
  public static Trip create(
      UUID id,
      String name,
      String baseCurrency,
      List<String> memberNames,
      Supplier<UUID> ids,
      Instant now) {
    var trimmed = requireText(name, "Trip name", MAX_NAME_LENGTH);
    if (!Money.Currencies.isKnown(baseCurrency)) {
      throw new TripRuleViolation("Unknown currency: " + baseCurrency);
    }
    var trip = new Trip();
    trip.raise(new TripCreated(id, trimmed, baseCurrency, now));
    for (var member : memberNames) {
      trip.join(ids.get(), member, now);
    }
    return trip;
  }

  /** Rebuilds a trip from its history; {@code version} is the number of events applied. */
  public static Trip replay(List<TripEvent> history) {
    if (history.isEmpty() || !(history.getFirst() instanceof TripCreated)) {
      throw new IllegalArgumentException("A trip history starts with TripCreated");
    }
    var trip = new Trip();
    history.forEach(trip::apply);
    return trip;
  }

  public void join(UUID memberId, String memberName, Instant now) {
    var trimmed = requireText(memberName, "Member name", MAX_NAME_LENGTH);
    var taken = members.values().stream().anyMatch(m -> m.name().equalsIgnoreCase(trimmed));
    if (taken) {
      throw new TripRuleViolation("There is already a member called " + trimmed);
    }
    raise(new MemberJoined(memberId, trimmed, now));
  }

  public void addExpense(UUID expenseId, ExpenseDraft draft, Instant now) {
    var description = requireText(draft.description(), "Description", MAX_DESCRIPTION_LENGTH);
    requireMember(draft.paidBy());
    var rate = rateFor(draft.amount(), draft.rate());
    try {
      var result =
          ExpenseSplitter.split(
              draft.amount(), rate, baseCurrency, draft.split(), List.copyOf(members.keySet()));
      raise(
          new ExpenseAdded(
              expenseId,
              description,
              draft.paidBy(),
              draft.amount().minor(),
              draft.amount().currency(),
              rate,
              result.baseAmount().minor(),
              kindOf(draft.split()),
              weightsOf(draft.split()),
              result.shares(),
              now));
    } catch (InvalidSplitException | IllegalArgumentException e) {
      throw new TripRuleViolation(e.getMessage());
    }
  }

  public void voidExpense(UUID expenseId, String reason, Instant now) {
    var expense = expenses.get(expenseId);
    if (expense == null) {
      throw new NotFoundException("No expense " + expenseId + " on this trip");
    }
    if (expense.voided()) {
      throw new TripRuleViolation("This expense is already voided");
    }
    var why = reason == null || reason.isBlank() ? "Undone" : reason.strip();
    raise(new ExpenseVoided(expenseId, truncate(why, MAX_DESCRIPTION_LENGTH), now));
  }

  public void recordSettlement(UUID settlementId, UUID from, UUID to, Money amount, Instant now) {
    requireMember(from);
    requireMember(to);
    if (from.equals(to)) {
      throw new TripRuleViolation("A member cannot settle with themselves");
    }
    if (!amount.currency().equals(baseCurrency)) {
      throw new TripRuleViolation("Settle up in the trip currency " + baseCurrency);
    }
    if (!amount.isPositive()) {
      throw new TripRuleViolation("Settlement amount must be positive");
    }
    raise(new SettlementRecorded(settlementId, from, to, amount.minor(), now));
  }

  public void voidSettlement(UUID settlementId, Instant now) {
    var settlement = settlements.get(settlementId);
    if (settlement == null) {
      throw new NotFoundException("No settlement " + settlementId + " on this trip");
    }
    if (settlement.voided()) {
      throw new TripRuleViolation("This settlement is already voided");
    }
    raise(new SettlementVoided(settlementId, now));
  }

  /**
   * Net position per member in base minor units, in join order: positive means the group owes them,
   * negative means they owe the group. Always sums to zero.
   */
  public Map<UUID, Long> balances() {
    var balances = new LinkedHashMap<UUID, Long>();
    members.keySet().forEach(id -> balances.put(id, 0L));
    for (var expense : expenses.values()) {
      if (expense.voided()) {
        continue;
      }
      balances.merge(expense.paidBy(), expense.baseAmount().minor(), Math::addExact);
      for (Share share : expense.shares()) {
        balances.merge(share.memberId(), -share.baseMinor(), Math::addExact);
      }
    }
    for (var settlement : settlements.values()) {
      if (settlement.voided()) {
        continue;
      }
      balances.merge(settlement.from(), settlement.amount().minor(), Math::addExact);
      balances.merge(settlement.to(), -settlement.amount().minor(), Math::addExact);
    }
    return Collections.unmodifiableMap(balances);
  }

  /** Sum of all non-voided expenses in base currency. */
  public Money totalSpent() {
    var total =
        expenses.values().stream()
            .filter(e -> !e.voided())
            .mapToLong(e -> e.baseAmount().minor())
            .sum();
    return Money.of(total, baseCurrency);
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String baseCurrency() {
    return baseCurrency;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /** Number of events in the stream, including uncommitted ones. */
  public long version() {
    return version;
  }

  /** The version the stream had before the uncommitted events. */
  public long committedVersion() {
    return version - uncommitted.size();
  }

  public List<Member> members() {
    return List.copyOf(members.values());
  }

  public Member member(UUID memberId) {
    var member = members.get(memberId);
    if (member == null) {
      throw new NotFoundException("No member " + memberId + " on this trip");
    }
    return member;
  }

  public List<Expense> expenses() {
    return List.copyOf(expenses.values());
  }

  public Expense expense(UUID expenseId) {
    var expense = expenses.get(expenseId);
    if (expense == null) {
      throw new NotFoundException("No expense " + expenseId + " on this trip");
    }
    return expense;
  }

  public List<Settlement> settlements() {
    return List.copyOf(settlements.values());
  }

  public Settlement settlement(UUID settlementId) {
    var settlement = settlements.get(settlementId);
    if (settlement == null) {
      throw new NotFoundException("No settlement " + settlementId + " on this trip");
    }
    return settlement;
  }

  public List<TripEvent> uncommittedEvents() {
    return List.copyOf(uncommitted);
  }

  public void markCommitted() {
    uncommitted.clear();
  }

  private void raise(TripEvent event) {
    apply(event);
    uncommitted.add(event);
  }

  private void apply(TripEvent event) {
    switch (event) {
      case TripCreated e -> {
        id = e.tripId();
        name = e.name();
        baseCurrency = e.baseCurrency();
        createdAt = e.occurredAt();
      }
      case MemberJoined e -> members.put(e.memberId(), new Member(e.memberId(), e.name()));
      case ExpenseAdded e ->
          expenses.put(
              e.expenseId(),
              new Expense(
                  e.expenseId(),
                  e.description(),
                  e.paidBy(),
                  Money.of(e.amountMinor(), e.currency()),
                  e.rate(),
                  Money.of(e.baseAmountMinor(), baseCurrency),
                  e.splitKind(),
                  e.shares(),
                  e.occurredAt(),
                  false));
      case ExpenseVoided e -> expenses.computeIfPresent(e.expenseId(), (k, v) -> v.markVoided());
      case SettlementRecorded e ->
          settlements.put(
              e.settlementId(),
              new Settlement(
                  e.settlementId(),
                  e.from(),
                  e.to(),
                  Money.of(e.amountMinor(), baseCurrency),
                  e.occurredAt(),
                  false));
      case SettlementVoided e ->
          settlements.computeIfPresent(e.settlementId(), (k, v) -> v.markVoided());
    }
    version++;
  }

  private BigDecimal rateFor(Money amount, BigDecimal rate) {
    if (amount.currency().equals(baseCurrency)) {
      if (rate != null && rate.compareTo(BigDecimal.ONE) != 0) {
        throw new TripRuleViolation("An expense in the trip currency has rate 1");
      }
      return BigDecimal.ONE;
    }
    if (rate == null || rate.signum() <= 0) {
      throw new TripRuleViolation(
          "Give the rate: how many %s is one %s".formatted(baseCurrency, amount.currency()));
    }
    return rate.stripTrailingZeros();
  }

  private void requireMember(UUID memberId) {
    if (memberId == null || !members.containsKey(memberId)) {
      throw new TripRuleViolation("Not a member of this trip: " + memberId);
    }
  }

  private static SplitKind kindOf(SplitRule rule) {
    return switch (rule) {
      case SplitRule.Equal ignored -> SplitKind.EQUAL;
      case SplitRule.Exact ignored -> SplitKind.EXACT;
      case SplitRule.Shares ignored -> SplitKind.SHARES;
    };
  }

  private static Map<UUID, Long> weightsOf(SplitRule rule) {
    return switch (rule) {
      case SplitRule.Equal equal ->
          equal.participants().stream().collect(Collectors.toMap(id -> id, id -> 1L, (a, b) -> a));
      case SplitRule.Exact exact -> exact.amounts();
      case SplitRule.Shares shares ->
          shares.shares().entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue()));
    };
  }

  private static String requireText(String value, String label, int max) {
    if (value == null || value.isBlank()) {
      throw new TripRuleViolation(label + " is required");
    }
    var trimmed = value.strip();
    if (trimmed.length() > max) {
      throw new TripRuleViolation("%s is longer than %d characters".formatted(label, max));
    }
    return trimmed;
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
