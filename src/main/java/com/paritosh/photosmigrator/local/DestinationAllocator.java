package com.paritosh.photosmigrator.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DestinationAllocator {
    public AllocationResult allocate(List<TakeoutMediaItem> items, List<DestinationPlan> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("At least one destination account is required");
        }

        List<TakeoutMediaItem> unique = items.stream()
                .filter(item -> !item.duplicate())
                .sorted(Comparator
                        .comparing(TakeoutMediaItem::takenTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TakeoutMediaItem::sourceRef))
                .toList();

        List<AllocationResult.Assignment> assignments = new ArrayList<>();
        List<String> unassigned = new ArrayList<>();
        Map<String, Long> assignedBytes = new LinkedHashMap<>();
        Map<String, Long> maxBytes = new LinkedHashMap<>();
        for (DestinationPlan destination : destinations) {
            assignedBytes.put(destination.accountLabel(), 0L);
            maxBytes.put(destination.accountLabel(), destination.maxBytes());
        }

        int destinationIndex = 0;
        for (TakeoutMediaItem item : unique) {
            boolean assigned = false;
            while (destinationIndex < destinations.size()) {
                DestinationPlan destination = destinations.get(destinationIndex);
                long used = assignedBytes.get(destination.accountLabel());
                if (used + item.sizeBytes() <= destination.maxBytes()) {
                    assignments.add(new AllocationResult.Assignment(item.id(), destination.accountLabel(), item.sizeBytes()));
                    assignedBytes.put(destination.accountLabel(), used + item.sizeBytes());
                    assigned = true;
                    break;
                }
                destinationIndex++;
            }
            if (!assigned) unassigned.add(item.id());
        }

        return new AllocationResult(List.copyOf(assignments), List.copyOf(unassigned),
                Map.copyOf(assignedBytes), Map.copyOf(maxBytes));
    }
}
