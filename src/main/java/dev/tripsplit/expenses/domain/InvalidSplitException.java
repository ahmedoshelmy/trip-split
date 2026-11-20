package dev.tripsplit.expenses.domain;

import java.io.Serial;

/** The split cannot be applied to this expense (unknown member, amounts that do not add up...). */
public class InvalidSplitException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public InvalidSplitException(String message) {
    super(message);
  }
}
