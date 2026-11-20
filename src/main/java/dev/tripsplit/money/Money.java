package dev.tripsplit.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in integer minor units (piasters, cents) of an ISO-4217 currency. No floating point
 * anywhere: parsing is exact and refuses more decimals than the currency has.
 */
public record Money(long minor, String currency) {

  public Money {
    Objects.requireNonNull(currency, "currency");
    if (!Currencies.isKnown(currency)) {
      throw new IllegalArgumentException("Unknown currency: " + currency);
    }
  }

  public static Money of(long minor, String currency) {
    return new Money(minor, currency);
  }

  public static Money zero(String currency) {
    return new Money(0, currency);
  }

  /** Parses {@code "600.50"} exactly; {@code "1.005"} in a two-decimal currency is rejected. */
  public static Money parse(String amount, String currency) {
    Objects.requireNonNull(amount, "amount");
    var digits = Currencies.minorDigits(currency);
    BigDecimal value;
    try {
      value = new BigDecimal(amount.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not a number: " + amount, e);
    }
    try {
      var minor = value.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits);
      return new Money(minor.longValueExact(), currency);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "%s takes at most %d decimals: %s".formatted(currency, digits, amount), e);
    }
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(minor, other.minor), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(minor, other.minor), currency);
  }

  public Money negate() {
    return new Money(Math.negateExact(minor), currency);
  }

  public boolean isPositive() {
    return minor > 0;
  }

  /** Decimal text without grouping, e.g. {@code "-12.50"}. */
  public String toDecimalString() {
    return BigDecimal.valueOf(minor, Currencies.minorDigits(currency)).toPlainString();
  }

  /**
   * Converts to another currency at {@code rate} target units per one unit of this currency,
   * rounding half-even to the target's minor unit.
   */
  public Money convert(BigDecimal rate, String targetCurrency) {
    Objects.requireNonNull(rate, "rate");
    if (rate.signum() <= 0) {
      throw new IllegalArgumentException("Rate must be positive");
    }
    if (targetCurrency.equals(currency)) {
      return this;
    }
    var shift = Currencies.minorDigits(targetCurrency) - Currencies.minorDigits(currency);
    var converted =
        BigDecimal.valueOf(minor)
            .multiply(rate)
            .movePointRight(shift)
            .setScale(0, RoundingMode.HALF_EVEN);
    return new Money(converted.longValueExact(), targetCurrency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + currency + " vs " + other.currency);
    }
  }

  @Override
  public String toString() {
    return currency + " " + toDecimalString();
  }

  /** Lookup helpers over {@link Currency}. */
  public static final class Currencies {
    private Currencies() {}

    public static boolean isKnown(String code) {
      if (code == null || code.length() != 3) {
        return false;
      }
      try {
        return Currency.getInstance(code).getDefaultFractionDigits() >= 0;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }

    public static int minorDigits(String code) {
      if (!isKnown(code)) {
        throw new IllegalArgumentException("Unknown currency: " + code);
      }
      return Currency.getInstance(code).getDefaultFractionDigits();
    }
  }
}
