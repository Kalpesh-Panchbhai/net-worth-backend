package com.networth.networthbackend.common;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One dated cash movement in the display currency: negative for money invested, positive for money
 * returned.
 * <p>
 * Shipped on {@link com.networth.networthbackend.accounts.AccountSummary} so a client can pool an
 * arbitrary subset of accounts — whatever the user has filtered to — and get an exact XIRR for it.
 * A money-weighted return over a group is the IRR of the group's pooled flows, so it cannot be
 * recovered from the members' individual {@code xirr} values by any kind of average. Sending the
 * flows keeps that number exact without the client having to fetch every account's transactions,
 * which is what it used to do.
 */
public record CashFlow(LocalDate date, BigDecimal amount) {
}
