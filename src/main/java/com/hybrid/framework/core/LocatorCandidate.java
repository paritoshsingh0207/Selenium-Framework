package com.hybrid.framework.core;

import com.hybrid.framework.enums.LocatorType;

import java.util.Objects;

/**
 * One concrete way of locating an element.
 *
 * Kept as a normal class instead of a record so the framework remains
 * compatible with JDK 8. The small accessor methods intentionally keep the
 * same names the rest of the framework already uses.
 */
public final class LocatorCandidate {
    private final LocatorType type;
    private final String value;
    private final String accessibleName;

    public LocatorCandidate(LocatorType type, String value, String accessibleName) {
        this.type = Objects.requireNonNull(type, "Locator type cannot be null");
        this.value = Objects.requireNonNull(value, "Locator value cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Locator value cannot be blank");
        }
        this.accessibleName = accessibleName;
    }

    public static LocatorCandidate of(LocatorType type, String value) {
        return new LocatorCandidate(type, value, null);
    }

    public static LocatorCandidate role(String role, String accessibleName) {
        if (accessibleName == null || accessibleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Accessible name is required for a role locator");
        }
        return new LocatorCandidate(LocatorType.ROLE, role, accessibleName);
    }

    public LocatorType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public String accessibleName() {
        return accessibleName;
    }

    public String description() {
        return type == LocatorType.ROLE
                ? "ROLE=" + value + ", accessibleName=" + accessibleName
                : type + "=" + value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocatorCandidate)) {
            return false;
        }
        LocatorCandidate that = (LocatorCandidate) other;
        return type == that.type
                && Objects.equals(value, that.value)
                && Objects.equals(accessibleName, that.accessibleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, accessibleName);
    }

    @Override
    public String toString() {
        return description();
    }
}
