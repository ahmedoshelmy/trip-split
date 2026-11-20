# 3. Money as integer minor units, one conversion, a deterministic remainder rule

Date: 2026-11-20 · Status: accepted

## Context

EGP 100 split three ways is 33.333… Floating point drifts, and rounding each share separately makes the shares
add up to 99.99 or 100.01. Over a trip those piasters become a balance that never reaches zero, and nobody trusts
the app again. Trips also mix currencies: a boat trip paid in USD, dinner in EGP.

## Decision

- `Money(long minor, String currency)` everywhere; amounts cross the API as decimal strings (`"600.50"`) and are
  parsed exactly. More decimals than the currency allows (`1.005 EGP`) is an error, not a rounding.
- Every trip has a **base currency**. An expense in another currency carries the **rate used** (base units per
  unit), stored in the event. It is converted **once**, half-even to the base minor unit.
- The converted total is divided by the **largest-remainder method**: each participant gets
  `floor(total × weight / Σweights)`; the leftover minor units go one each to the largest fractional remainders,
  **ties broken by trip join order**. Weights are 1 for an equal split, the exact amounts (in the expense
  currency) for an exact split, the share counts for a shares split.
- Shares are stored in the event, so the rule is applied exactly once per expense.

## Consequences

- Shares always add up to the expense total, so balances always net to zero (property-tested with jqwik).
- The extra piaster is predictable ("the first person who joined pays it") and the web preview shows it.
- A foreign-currency exact split keeps its proportions after conversion even when the converted total rounds.
- Rates are entered by hand in the MVP; fetching them from a free API is a stretch goal, and would still store
  the rate used in the event.
