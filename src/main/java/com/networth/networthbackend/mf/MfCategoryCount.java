package com.networth.networthbackend.mf;

/**
 * One (asset class, sub-category) peer group and how many live Direct+Growth funds it holds.
 * Backs the category browser that a leaderboard request drills into.
 */
public record MfCategoryCount(String assetClass, String subCategory, int liveCount) {
}
