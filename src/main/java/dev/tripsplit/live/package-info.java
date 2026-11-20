/**
 * Live updates: pushes new balances and timeline pings to browsers over STOMP on {@code /ws}, topic
 * {@code /topic/trips/{tripId}}.
 */
@ApplicationModule(displayName = "Live updates")
package dev.tripsplit.live;

import org.springframework.modulith.ApplicationModule;
