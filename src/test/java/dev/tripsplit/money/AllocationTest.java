package dev.tripsplit.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationTest {

  @Test
  void equalSplitGivesLeftoverCentsToTheEarliestParts() {
    assertThat(Allocation.equally(100, 3)).containsExactly(34, 33, 33);
    assertThat(Allocation.equally(101, 3)).containsExactly(34, 34, 33);
    assertThat(Allocation.equally(99, 3)).containsExactly(33, 33, 33);
  }

  @Test
  void weightedSplitGivesLeftoverToLargestFractionFirst() {
    // 1000 by 1:1:2:1 -> 200, 200, 400, 200 exactly
    assertThat(Allocation.byWeights(1000, List.of(1L, 1L, 2L, 1L)))
        .containsExactly(200, 200, 400, 200);
    // 100 by 1:2 -> 33.33 / 66.67: the 0.67 fraction wins the leftover cent
    assertThat(Allocation.byWeights(100, List.of(1L, 2L))).containsExactly(33, 67);
  }

  @Test
  void zeroWeightGetsNothing() {
    assertThat(Allocation.byWeights(7, List.of(0L, 1L, 1L))).containsExactly(0, 4, 3);
  }

  @Test
  void rejectsBadInput() {
    assertThatThrownBy(() -> Allocation.byWeights(10, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Allocation.byWeights(10, List.of(0L, 0L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Allocation.byWeights(-1, List.of(1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
