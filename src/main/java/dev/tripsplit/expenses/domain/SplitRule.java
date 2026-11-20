package dev.tripsplit.expenses.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who shares an expense and in what proportion. */
public sealed interface SplitRule {

  /** Members taking part, in no particular order (the splitter orders them by join order). */
  List<UUID> participants();

  /** Everyone pays the same; leftover minor units go to the earliest-joined participants. */
  record Equal(List<UUID> participants) implements SplitRule {
    public Equal {
      participants = List.copyOf(participants);
    }
  }

  /** Fixed amounts in the expense currency's minor units; they must add up to the total. */
  record Exact(Map<UUID, Long> amounts) implements SplitRule {
    public Exact {
      amounts = Map.copyOf(amounts);
    }

    @Override
    public List<UUID> participants() {
      return List.copyOf(amounts.keySet());
    }
  }

  /** Proportional to whole-number shares, e.g. a couple counts 2 and a single traveller 1. */
  record Shares(Map<UUID, Integer> shares) implements SplitRule {
    public Shares {
      shares = Map.copyOf(shares);
    }

    @Override
    public List<UUID> participants() {
      return List.copyOf(shares.keySet());
    }
  }
}
