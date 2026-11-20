package dev.tripsplit.timeline.adapter.web;

import dev.tripsplit.timeline.application.TimelineEntry;
import dev.tripsplit.timeline.application.TimelineProjector;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/trips/{id}/timeline}: newest first; may lag the trip by a few milliseconds. */
@RestController
class TimelineController {

  private final TimelineProjector timeline;

  TimelineController(TimelineProjector timeline) {
    this.timeline = timeline;
  }

  @GetMapping("/api/trips/{tripId}/timeline")
  List<TimelineEntry> timeline(@PathVariable UUID tripId) {
    return timeline.timeline(tripId);
  }
}
