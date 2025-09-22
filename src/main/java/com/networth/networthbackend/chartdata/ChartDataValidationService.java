package com.networth.networthbackend.chartdata;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive validation service for Chart Data API
 * Handles validation for all entity types generically
 */
@Slf4j
public class ChartDataValidationService {

    /**
     * Parameter-only validation for chart data request (no entity existence check)
     */
    public ValidationResult validateChartDataParameters(String entityTypeParam, String entityIdParam, String timePeriod) {
        List<String> errors = new ArrayList<>();

        // Validate entity type
        if (entityTypeParam == null || entityTypeParam.isBlank()) {
            errors.add("entityType parameter is required");
            return ValidationResult.failure(errors);
        }

        EntityType entityType = EntityType.fromCode(entityTypeParam).orElse(null);
        if (entityType == null) {
            String supportedTypes = String.join(", ", EntityType.getAllCodes());
            errors.add(String.format("Invalid entityType '%s'. Supported values: %s", entityTypeParam, supportedTypes));
            return ValidationResult.failure(errors);
        }

        // Validate entity ID
        if (entityIdParam == null || entityIdParam.isBlank()) {
            errors.add("entityId parameter is required");
            return ValidationResult.failure(errors);
        }

        long entityId;
        try {
            entityId = Long.parseLong(entityIdParam);
        } catch (NumberFormatException e) {
            errors.add("entityId must be a valid number");
            return ValidationResult.failure(errors);
        }

        if (entityId <= 0) {
            errors.add("entityId must be a positive number");
            return ValidationResult.failure(errors);
        }

        // Validate time period
        if (timePeriod == null || timePeriod.isBlank()) {
            errors.add("timePeriod parameter is required");
            return ValidationResult.failure(errors);
        }

        try {
            ChartDataService.TimePeriod.fromCode(timePeriod);
        } catch (IllegalArgumentException e) {
            errors.add("Invalid timePeriod. Supported values: 1M, 3M, 6M, 1Y, 2Y, 5Y, ALL");
            return ValidationResult.failure(errors);
        }

        log.debug("Parameter validation successful for {}: entityId={}, timePeriod={}", entityType.getCode(), entityId, timePeriod);

        return ValidationResult.success(null);
    }

    /**
     * Get validation summary for logging/debugging
     */
    public String getValidationSummary(ValidationResult result) {
        if (result.valid()) {
            EntityMetadata metadata = result.entityMetadata();
            if (metadata != null) {
                return String.format("Valid request for %s", metadata.getDescription());
            } else {
                return "Valid request";
            }
        } else {
            return String.format("Invalid request: %s", String.join(", ", result.errors()));
        }
    }

    /**
     * Validation result container
     */
    public record ValidationResult(boolean valid, List<String> errors, EntityMetadata entityMetadata) {
        public ValidationResult(boolean valid, List<String> errors, EntityMetadata entityMetadata) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.entityMetadata = entityMetadata;
        }

        public static ValidationResult success(EntityMetadata metadata) {
            return new ValidationResult(true, new ArrayList<>(), metadata);
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, null);
        }

        public static ValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ValidationResult(false, errors, null);
        }

        public String getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
}
