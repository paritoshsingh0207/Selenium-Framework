package com.paritosh.photosmigrator.local;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationAllocatorTest {
    @Test
    void assignsUniqueMediaChronologicallyAcrossDestinationCapacities() {
        TakeoutMediaItem old = item("old", 6, "2019-01-01T00:00:00Z", false);
        TakeoutMediaItem middle = item("middle", 4, "2020-01-01T00:00:00Z", false);
        TakeoutMediaItem newest = item("new", 7, "2021-01-01T00:00:00Z", false);
        TakeoutMediaItem duplicate = item("dup", 6, "2019-01-01T00:00:00Z", true);

        AllocationResult result = new DestinationAllocator().allocate(
                List.of(newest, duplicate, middle, old),
                List.of(new DestinationPlan("Account B", 10), new DestinationPlan("Account C", 10)));

        assertThat(result.assignments()).extracting(AllocationResult.Assignment::itemId)
                .containsExactly("old", "middle", "new");
        assertThat(result.assignments()).extracting(AllocationResult.Assignment::accountLabel)
                .containsExactly("Account B", "Account B", "Account C");
        assertThat(result.assignedBytes()).containsEntry("Account B", 10L).containsEntry("Account C", 7L);
        assertThat(result.unassignedItemIds()).isEmpty();
    }

    private TakeoutMediaItem item(String id, long size, String time, boolean duplicate) {
        return new TakeoutMediaItem(id, "a.zip", id + ".jpg", id + ".jpg", "image/jpeg", size,
                id + "hash", Instant.parse(time), duplicate, duplicate ? "old" : "");
    }
}
