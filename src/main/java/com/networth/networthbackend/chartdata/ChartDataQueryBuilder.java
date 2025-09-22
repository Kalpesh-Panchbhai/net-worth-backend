package com.networth.networthbackend.chartdata;

/**
 * Generic SQL query builder for Chart Data API that constructs queries dynamically based on entity type
 */
public class ChartDataQueryBuilder {

    /**
     * Build chart data query for any entity type using configuration registry
     */
    public static String buildChartDataQuery(EntityType entityType) {
        return EntityConfigurationRegistry.getChartDataQuery(entityType);
    }

    /**
     * Build earliest date query for any entity type using configuration registry
     */
    public static String buildEarliestDateQuery(EntityType entityType) {
        return EntityConfigurationRegistry.getEarliestDateQuery(entityType);
    }
}
