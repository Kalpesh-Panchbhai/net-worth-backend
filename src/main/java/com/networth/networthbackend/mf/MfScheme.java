package com.networth.networthbackend.mf;

import lombok.Builder;

import java.time.LocalDate;

/**
 * One row of {@code mf_schemes}: a single share class of an AMFI-registered scheme.
 *
 * @param schemeCode  AMFI code — the join key to NAV history and to mfapi.in
 * @param name        scheme name without the plan/option suffix, for display
 * @param navName     AMFI's "Scheme NAV Name", the field plan and option are parsed from
 * @param rawCategory AMFI's category string, verbatim
 * @param launchDate  scheme launch, not share-class launch: a Direct share class of a 1995 scheme
 *                    still reports 1995 here, while its NAV history cannot start before 2013-01-01
 * @param isLive      present in the most recent NAVAll snapshot
 */
@Builder
public record MfScheme(
        int schemeCode,
        String isin,
        String amc,
        String name,
        String navName,
        String rawCategory,
        AssetClass assetClass,
        String subCategory,
        MfPlanOption.Plan plan,
        MfPlanOption.Option option,
        LocalDate launchDate,
        boolean isLive,
        LocalDate lastNavDate
) {
}
