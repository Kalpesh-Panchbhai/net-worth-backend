package com.networth.networthbackend.chartdata;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for entity configurations in the Chart Data API
 * Provides centralized management of entity-specific behavior and queries
 */
@Slf4j
public class EntityConfigurationRegistry {

    private static final Map<EntityType, EntityConfiguration> configurations = new HashMap<>();

    static {
        // Initialize default configurations for all entity types
        initializeDefaultConfigurations();
    }

    /**
     * Initialize default configurations for all supported entity types
     */
    private static void initializeDefaultConfigurations() {
        // Holding configuration
        EntityConfiguration holdingConfig = EntityConfiguration.builder().entityType(EntityType.HOLDING).displayName("Holding").pluralName("Holdings").requiresJoin(true).primaryTable("holdings").primaryKeyColumn("holding_id").parentKeyColumn("account_id").metadataColumns(List.of("holding_id", "symbol", "name", "account_name")).chartDataQueryTemplate("""
                SELECT ahvd.date, ahvd.value, ahvd.invested
                FROM account_holding_values_daily ahvd
                INNER JOIN holdings h ON ahvd.holding_id = h.id
                WHERE ahvd.holding_id = ? AND ahvd.date >= ? AND ahvd.date <= ?
                ORDER BY ahvd.date ASC
                """).earliestDateQueryTemplate("""
                SELECT MIN(ahvd.date) as earliest_date
                FROM account_holding_values_daily ahvd
                WHERE ahvd.holding_id = ?
                """).build();

        // Account configuration
        EntityConfiguration accountConfig = EntityConfiguration.builder().entityType(EntityType.ACCOUNT).displayName("Account").pluralName("Accounts").requiresJoin(false).primaryTable("accounts").primaryKeyColumn("account_id").parentKeyColumn("user_id").metadataColumns(List.of("account_id", "account_name", "account_type")).chartDataQueryTemplate("""
                SELECT date, SUM(value) as value, SUM(invested) as invested
                FROM account_holding_values_daily
                WHERE account_id = ? AND date >= ? AND date <= ?
                GROUP BY date
                ORDER BY date ASC
                """).earliestDateQueryTemplate("""
                SELECT MIN(date) as earliest_date
                FROM account_holding_values_daily
                WHERE account_id = ?
                """).build();

        // Watchlist configuration
        EntityConfiguration watchlistConfig = EntityConfiguration.builder().entityType(EntityType.WATCHLIST).displayName("Watchlist").pluralName("Watchlists").requiresJoin(true).primaryTable("watchlists").primaryKeyColumn("watchlist_id").parentKeyColumn("user_id").metadataColumns(List.of("watchlist_id", "watchlist_name", "user_id")).chartDataQueryTemplate("""
                SELECT ahvd.date, SUM(ahvd.value) as value, SUM(ahvd.invested) as invested
                FROM account_holding_values_daily ahvd
                INNER JOIN watchlist_accounts wa ON ahvd.account_id = wa.account_id
                WHERE wa.watchlist_id = ? AND ahvd.date >= ? AND ahvd.date <= ?
                GROUP BY ahvd.date
                ORDER BY ahvd.date ASC
                """).earliestDateQueryTemplate("""
                SELECT MIN(ahvd.date) as earliest_date
                FROM account_holding_values_daily ahvd
                INNER JOIN watchlist_accounts wa ON ahvd.account_id = wa.account_id
                WHERE wa.watchlist_id = ?
                """).build();

        // Register all configurations
        configurations.put(EntityType.HOLDING, holdingConfig);
        configurations.put(EntityType.ACCOUNT, accountConfig);
        configurations.put(EntityType.WATCHLIST, watchlistConfig);

        log.info("Initialized entity configurations for {} entity types", configurations.size());
    }

    /**
     * Get configuration for a specific entity type
     */
    public static Optional<EntityConfiguration> getConfiguration(EntityType entityType) {
        return Optional.ofNullable(configurations.get(entityType));
    }

    /**
     * Get chart data query for an entity type
     */
    public static String getChartDataQuery(EntityType entityType) {
        return getConfiguration(entityType).map(EntityConfiguration::getChartDataQueryTemplate).orElseThrow(() -> new IllegalArgumentException("No configuration found for entity type: " + entityType.getCode()));
    }

    /**
     * Get earliest date query for an entity type
     */
    public static String getEarliestDateQuery(EntityType entityType) {
        return getConfiguration(entityType).map(EntityConfiguration::getEarliestDateQueryTemplate).orElseThrow(() -> new IllegalArgumentException("No configuration found for entity type: " + entityType.getCode()));
    }
}
