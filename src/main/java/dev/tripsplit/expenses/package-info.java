/**
 * How an expense is shared: split rules (equal, exact amounts, shares), currency conversion and the
 * remainder rule. Pure functions; the trips module records their results as facts.
 */
@ApplicationModule(displayName = "Expenses")
package dev.tripsplit.expenses;

import org.springframework.modulith.ApplicationModule;
