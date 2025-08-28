package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.SummaryMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WatchlistSummary extends SummaryMetrics {
    private long id;
    private long userId;
    private String name;
}
