package com.networth.networthbackend.mf;

/** Top level of the fund taxonomy. Stored as a string, so adding a member is backwards-compatible. */
public enum AssetClass {
    EQUITY,
    DEBT,
    HYBRID,
    /** Retirement, children's and life-cycle funds: SEBI's "solution oriented" bucket. */
    SOLUTION,
    /** Index funds, ETFs, gold/silver, fund-of-funds. */
    OTHER,
    /** AMFI shipped a category string the resolver does not know. Excluded from rankings. */
    UNKNOWN
}
