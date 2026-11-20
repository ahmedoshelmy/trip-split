package dev.tripsplit.trips.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The stream version as an HTTP entity tag: {@code ETag: "7"}, {@code If-Match: "7"}. */
final class Versions {
  private Versions() {}

  static String etag(long version) {
    return "\"" + version + "\"";
  }

  /** Parses {@code If-Match}; null when absent or {@code *} (no precondition). */
  static Long fromIfMatch(String header) {
    if (header == null || header.isBlank() || header.strip().equals("*")) {
      return null;
    }
    var value = header.strip();
    if (value.startsWith("W/")) {
      value = value.substring(2);
    }
    value = value.replace("\"", "");
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "If-Match must be a trip version");
    }
  }
}
