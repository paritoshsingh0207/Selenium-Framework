package com.paritosh.photosmigrator.local;

public record DestinationPlan(String accountLabel, long maxBytes) {
    public DestinationPlan {
        if (accountLabel == null || accountLabel.isBlank()) {
            throw new IllegalArgumentException("Destination account label is required");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Destination maxBytes must be greater than zero");
        }
    }
}
