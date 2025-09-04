package com.networth.networthbackend.chartdata;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration-driven entity support for Chart Data API
 * Allows for easy extension and customization of entity behavior
 */
@Data
@Builder
public class EntityConfiguration {

    private EntityType entityType;
    private String displayName;
    private String pluralName;
    private boolean requiresJoin;
    private String primaryTable;
    private String primaryKeyColumn;
    private String parentKeyColumn;
    private List<String> metadataColumns;
    private Map<String, Object> customProperties;

    // SQL query templates
    private String chartDataQueryTemplate;
    private String earliestDateQueryTemplate;
    private String entityExistsQueryTemplate;

    // Custom validation rules
    private Function<Long, Boolean> customValidator;
    private Function<Long, String> customErrorMessage;
}
