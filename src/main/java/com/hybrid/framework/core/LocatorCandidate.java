package com.hybrid.framework.core;

import com.hybrid.framework.enums.LocatorType;

import java.util.Objects;

public record LocatorCandidate(LocatorType type, String value, String accessibleName) {
    public LocatorCandidate {
        Objects.requireNonNull(type, "Locator type cannot be null");
        Objects.requireNonNull(value, "Locator value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Locator value cannot be blank");
        }
    }

    public static LocatorCandidate of(LocatorType type, String value) {
        return new LocatorCandidate(type, value, null);
    }

    public static LocatorCandidate role(String role, String accessibleName) {
        if (accessibleName == null || accessibleName.isBlank()) {
            throw new IllegalArgumentException("Accessible name is required for a role locator");
        }
        return new LocatorCandidate(LocatorType.ROLE, role, accessibleName);
    }

    public String description() {
        return type == LocatorType.ROLE
                ? "ROLE=" + value + ", accessibleName=" + accessibleName
                : type + "=" + value;
    }
}
