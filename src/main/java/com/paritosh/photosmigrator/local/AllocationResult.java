package com.paritosh.photosmigrator.local;

import java.util.List;
import java.util.Map;

public record AllocationResult(
        List<Assignment> assignments,
        List<String> unassignedItemIds,
        Map<String, Long> assignedBytes,
        Map<String, Long> maxBytes
) {
    public record Assignment(String itemId, String accountLabel, long sizeBytes) { }
}
