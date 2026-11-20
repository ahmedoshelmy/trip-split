package dev.tripsplit.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

  @ParameterizedTest
  @CsvSource({"600, EGP, 60000", "600.5, EGP, 60050", "0.01, USD, 1", "1500, JPY, 1500"})
  void parsesDecimalsExactlyIntoMinorUnits(String text, String currency, long minor) {
    assertThat(Money.parse(text, currency)).isEqualTo(Money.of(minor, currency));
  }

  @Test
  void refusesMoreDecimalsThanTheCurrencyHas() {
    assertThatThrownBy(() -> Money.parse("1.005", "EGP"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most 2 decimals");
    assertThatThrownBy(() -> Money.parse("10.5", "JPY"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesUnknownCurrenciesAndGarbage() {
    assertThatThrownBy(() -> Money.of(1, "XYZ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.parse("ten", "EGP"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertsWithHalfEvenRoundingToTheTargetMinorUnit() {
    var usd = Money.parse("40.00", "USD");
    assertThat(usd.convert(new BigDecimal("48.75"), "EGP")).isEqualTo(Money.parse("1950", "EGP"));
    // 0.01 USD * 48.755 = 0.48755 EGP -> 0.49 (half-even on 48.755 piasters rounds to 49)
    assertThat(Money.of(1, "USD").convert(new BigDecimal("48.755"), "EGP").minor()).isEqualTo(49);
    // 2 decimals -> 0 decimals
    assertThat(Money.parse("10.00", "USD").convert(new BigDecimal("150.5"), "JPY"))
        .isEqualTo(Money.of(1505, "JPY"));
  }

  @Test
  void arithmeticStaysInOneCurrency() {
    var a = Money.parse("10.00", "EGP");
    assertThat(a.plus(a).minus(Money.parse("5", "EGP")).toDecimalString()).isEqualTo("15.00");
    assertThat(a.negate().toString()).isEqualTo("EGP -10.00");
    assertThatThrownBy(() -> a.plus(Money.parse("1", "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
