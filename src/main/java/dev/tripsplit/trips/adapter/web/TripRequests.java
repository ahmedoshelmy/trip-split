package dev.tripsplit.trips.adapter.web;

import dev.tripsplit.trips.domain.SplitKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request bodies. Amounts travel as decimal strings ("600.50") and are parsed exactly. */
final class TripRequests {
  private TripRequests() {}

  record CreateTrip(
      @NotBlank @Size(max = 60) String name,
      @NotBlank @Size(min = 3, max = 3) String baseCurrency,
      @Size(max = 20) List<@NotBlank String> members) {}

  record AddMember(@NotBlank @Size(max = 60) String name) {}

  record AddExpense(
      @NotBlank @Size(max = 120) String description,
      @NotNull UUID paidBy,
      @NotBlank String amount,
      @NotBlank @Size(min = 3, max = 3) String currency,
      String rate,
      @NotNull @Valid Split split) {}

  /**
   * {@code EQUAL} uses {@code participants}, {@code EXACT} uses {@code amounts} (decimal strings in
   * the expense currency), {@code SHARES} uses {@code shares}.
   */
  record Split(
      @NotNull SplitKind kind,
      List<UUID> participants,
      Map<UUID, String> amounts,
      Map<UUID, Integer> shares) {}

  record VoidExpense(@Size(max = 120) String reason) {}

  record RecordSettlement(@NotNull UUID from, @NotNull UUID to, @NotBlank String amount) {}
}
