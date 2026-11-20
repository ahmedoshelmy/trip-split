package dev.tripsplit.trips.domain;

import dev.tripsplit.expenses.domain.SplitRule;
import dev.tripsplit.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a member wants to record. {@code rate} is base units per unit of {@code amount}'s currency
 * and may be null when the expense is already in the base currency.
 */
public record ExpenseDraft(
    String description, UUID paidBy, Money amount, BigDecimal rate, SplitRule split) {}
