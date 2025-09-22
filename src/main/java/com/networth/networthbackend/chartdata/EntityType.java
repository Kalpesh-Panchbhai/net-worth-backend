package com.networth.networthbackend.chartdata;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum defining all supported entity types for Chart Data API with their metadata
 */
public enum EntityType {
    HOLDING("holding", "holdings", "holding_id", "account_id", true), ACCOUNT("account", "accounts", "account_id", "user_id", false), WATCHLIST("watchlist", "watchlists", "watchlist_id", "user_id", false);

    private final String code;
    private final String tableName;
    private final String primaryKeyColumn;
    private final String parentKeyColumn;
    private final boolean requiresJoin;

    EntityType(String code, String tableName, String primaryKeyColumn, String parentKeyColumn, boolean requiresJoin) {
        this.code = code;
        this.tableName = tableName;
        this.primaryKeyColumn = primaryKeyColumn;
        this.parentKeyColumn = parentKeyColumn;
        this.requiresJoin = requiresJoin;
    }

    /**
     * Get EntityType from string code
     */
    public static Optional<EntityType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(values()).filter(type -> type.code.equalsIgnoreCase(code.trim())).findFirst();
    }

    /**
     * Get all supported entity type codes
     */
    public static String[] getAllCodes() {
        return Arrays.stream(values()).map(EntityType::getCode).toArray(String[]::new);
    }

    public String getCode() {
        return code;
    }
}
