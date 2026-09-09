package com.hybrid.framework.healing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class HealingEventStore {
    private static final ConcurrentLinkedQueue<HealingEvent> EVENTS = new ConcurrentLinkedQueue<>();

    private HealingEventStore() {
    }

    public static void add(HealingEvent event) {
        EVENTS.add(event);
    }

    public static List<HealingEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<HealingEvent>(EVENTS));
    }

    public static void clear() {
        EVENTS.clear();
    }
}
