package dev.tripsplit.trips.adapter.web;

import dev.tripsplit.eventstore.ConcurrencyConflictException;
import dev.tripsplit.trips.domain.NotFoundException;
import dev.tripsplit.trips.domain.TripRuleViolation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain errors to RFC 9457 problem details. */
@RestControllerAdvice
class ApiErrors {

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
  }

  @ExceptionHandler(TripRuleViolation.class)
  ProblemDetail ruleViolation(TripRuleViolation e) {
    return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Rule violated", e.getMessage());
  }

  /** The client's {@code If-Match} version is stale, or another write won the race. */
  @ExceptionHandler(ConcurrencyConflictException.class)
  ProblemDetail conflict(ConcurrencyConflictException e) {
    var problem =
        problem(
            HttpStatus.PRECONDITION_FAILED,
            "Trip changed",
            "Someone else changed this trip. Reload it and try again.");
    problem.setProperty("expectedVersion", e.expectedVersion());
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return problem;
  }
}
