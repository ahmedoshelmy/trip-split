/**
 * Trip timeline: an asynchronous projection of {@link dev.tripsplit.trips.TripActivity} module
 * events, delivered through Spring Modulith's event publication registry (a transactional outbox).
 */
@ApplicationModule(displayName = "Timeline")
package dev.tripsplit.timeline;

import org.springframework.modulith.ApplicationModule;
