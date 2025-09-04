package com.networth.networthbackend.chartdata;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic entity metadata container for Chart Data API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityMetadata {
    private EntityType entityType;
    private Long entityId;
    private String entityName;
    private Map<String, Object> additionalProperties;

    /**
     * Get a property value by key
     */
    public Object getProperty(String key) {
        return additionalProperties != null ? additionalProperties.get(key) : null;
    }

    /**
     * Get a property value as String
     */
    public String getStringProperty(String key) {
        Object value = getProperty(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a property value as Long
     */
    public Long getLongProperty(String key) {
        Object value = getProperty(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Check if entity has a specific property
     */
    public boolean hasProperty(String key) {
        return additionalProperties != null && additionalProperties.containsKey(key);
    }

    /**
     * Add or update a property
     */
    public void setProperty(String key, Object value) {
        if (additionalProperties == null) {
            additionalProperties = new java.util.HashMap<>();
        }
        additionalProperties.put(key, value);
    }

    /**
     * Get display name for the entity
     */
    public String getDisplayName() {
        if (entityName != null && !entityName.isBlank()) {
            return entityName;
        }
        
        // Fallback to entity-specific display logic
        return switch (entityType) {
            case HOLDING -> {
                String symbol = getStringProperty("symbol");
                String name = getStringProperty("name");
                if (symbol != null && name != null) {
                    yield symbol + " - " + name;
                } else if (symbol != null) {
                    yield symbol;
                } else if (name != null) {
                    yield name;
                } else {
                    yield "Holding #" + entityId;
                }
            }
            case ACCOUNT -> {
                String accountName = getStringProperty("account_name");
                String accountType = getStringProperty("account_type");
                if (accountName != null) {
                    yield accountName + (accountType != null ? " (" + accountType + ")" : "");
                } else {
                    yield "Account #" + entityId;
                }
            }
            case WATCHLIST -> {
                String watchlistName = getStringProperty("watchlist_name");
                yield watchlistName != null ? watchlistName : "Watchlist #" + entityId;
            }
        };
    }

    /**
     * Get entity description for logging/debugging
     */
    public String getDescription() {
        return String.format("%s[id=%d, name=%s]", 
            entityType.getCode(), 
            entityId, 
            getDisplayName());
    }
}
