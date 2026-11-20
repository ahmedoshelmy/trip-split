package dev.tripsplit.trips.domain;

import java.util.UUID;

/** A person on the trip. Names are unique within a trip, ignoring case. */
public record Member(UUID id, String name) {}
