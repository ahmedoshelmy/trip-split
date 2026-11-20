package dev.tripsplit.expenses.domain;

import java.util.UUID;

/** One participant's part of an expense, in the trip's base currency minor units. */
public record Share(UUID memberId, long baseMinor) {}
