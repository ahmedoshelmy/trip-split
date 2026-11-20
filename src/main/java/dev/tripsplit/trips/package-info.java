/**
 * Trips: the event-sourced trip aggregate (members, expenses, settlements), its commands and the
 * inline read models. Publishes {@link dev.tripsplit.trips.TripChanged} and {@link
 * dev.tripsplit.trips.TripActivity}; other modules query through {@link
 * dev.tripsplit.trips.TripQueries}.
 */
@ApplicationModule(displayName = "Trips")
package dev.tripsplit.trips;

import org.springframework.modulith.ApplicationModule;
