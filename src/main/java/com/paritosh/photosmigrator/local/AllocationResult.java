package com.paritosh.photosmigrator.local;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AllocationResult(
        List<Assignment> assignments,
        List<String> unassignedItemIds,
        Map<String, Long> assignedBytes,
        Map<String, Long> maxBytes
) {
    public AllocationResult {
        assignments = List.copyOf(assignments);
        unassignedItemIds = List.copyOf(unassignedItemIds);
        assignedBytes = Collections.unmodifiableMap(new LinkedHashMap<>(assignedBytes));
        maxBytes = Collections.unmodifiableMap(new LinkedHashMap<>(maxBytes));
    }

    public record Assignment(String itemId, String accountLabel, long sizeBytes) { }
}
