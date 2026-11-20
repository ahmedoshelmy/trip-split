package dev.tripsplit.settlement.adapter.web;

import dev.tripsplit.settlement.domain.SettleUp;
import dev.tripsplit.trips.BalanceSheet;
import dev.tripsplit.trips.BalanceSheet.MemberBalance;
import dev.tripsplit.trips.TripQueries;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** {@code GET /api/trips/{id}/settle-up}: who should pay whom to close the trip. */
@RestController
class SettleUpController {

  private final TripQueries trips;

  SettleUpController(TripQueries trips) {
    this.trips = trips;
  }

  @GetMapping("/api/trips/{tripId}/settle-up")
  SettleUpView settleUp(
      @PathVariable UUID tripId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant asOf) {
    var sheet =
        (asOf == null ? trips.balances(tripId) : trips.balancesAsOf(tripId, asOf))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No trip " + tripId));
    return view(sheet);
  }

  private static SettleUpView view(BalanceSheet sheet) {
    var byId =
        sheet.balances().stream()
            .collect(Collectors.toMap(MemberBalance::memberId, Function.identity()));
    var balances = new LinkedHashMap<UUID, Long>();
    sheet.balances().forEach(b -> balances.put(b.memberId(), b.balanceMinor()));
    var transfers =
        SettleUp.suggest(balances).stream()
            .map(t ->
                new TransferView(
                    t.from(),
                    byId.get(t.from()).name(),
                    t.to(),
                    byId.get(t.to()).name(),
                    t.amountMinor()))
            .toList();
    return new SettleUpView(sheet.tripId(), sheet.version(), sheet.currency(), transfers);
  }

  record SettleUpView(UUID tripId, long version, String currency, List<TransferView> transfers) {}

  record TransferView(UUID from, String fromName, UUID to, String toName, long amountMinor) {}
}
