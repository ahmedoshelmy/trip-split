package dev.tripsplit.settlement.domain;

/** {@code from} should pay {@code to} this many minor units. */
public record Transfer<K>(K from, K to, long amountMinor) {}
