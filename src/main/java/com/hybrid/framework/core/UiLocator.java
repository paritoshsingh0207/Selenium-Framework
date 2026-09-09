package com.hybrid.framework.core;

import com.hybrid.framework.enums.LocatorType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class UiLocator {
    private final String name;
    private final LocatorCandidate primary;
    private final List<LocatorCandidate> fallbacks;
    private final boolean healingEnabled;

    private UiLocator(String name, LocatorCandidate primary, List<LocatorCandidate> fallbacks,
                      boolean healingEnabled) {
        this.name = Objects.requireNonNull(name, "Locator name cannot be null");
        this.primary = Objects.requireNonNull(primary, "Primary locator cannot be null");
        this.fallbacks = List.copyOf(fallbacks);
        this.healingEnabled = healingEnabled;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static UiLocator id(String name, String value) {
        return builder(name).primary(LocatorType.ID, value).build();
    }

    public static UiLocator css(String name, String value) {
        return builder(name).primary(LocatorType.CSS, value).build();
    }

    public static UiLocator testId(String name, String value) {
        return builder(name).primary(LocatorType.TEST_ID, value).build();
    }

    public String name() {
        return name;
    }

    public LocatorCandidate primary() {
        return primary;
    }

    public List<LocatorCandidate> fallbacks() {
        return fallbacks;
    }

    public boolean healingEnabled() {
        return healingEnabled;
    }

    public LocatorType type() {
        return primary.type();
    }

    public String value() {
        return primary.value();
    }

    public String accessibleName() {
        return primary.accessibleName();
    }

    public List<LocatorCandidate> candidates() {
        LinkedHashSet<LocatorCandidate> values = new LinkedHashSet<>();
        values.add(primary);
        values.addAll(fallbacks);
        return List.copyOf(values);
    }

    public UiLocator resolved(LocatorCandidate candidate) {
        return new UiLocator(name, candidate, List.of(), false);
    }

    public boolean isSensitive() {
        String normalized = (name + " " + value()).toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
        return normalized.contains("password") || normalized.contains("passwd")
                || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("apikey") || normalized.contains("authorization")
                || normalized.contains("creditcard") || normalized.contains("cvv");
    }

    @Override
    public String toString() {
        return name + " [primary=" + primary.description() + ", fallbacks=" + fallbacks.size() + "]";
    }

    public static final class Builder {
        private final String name;
        private LocatorCandidate primary;
        private final List<LocatorCandidate> fallbacks = new ArrayList<>();
        private boolean healingEnabled = true;

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Locator name cannot be blank");
            }
            this.name = name;
        }

        public Builder primary(LocatorType type, String value) {
            this.primary = LocatorCandidate.of(type, value);
            return this;
        }

        public Builder primaryRole(String role, String accessibleName) {
            this.primary = LocatorCandidate.role(role, accessibleName);
            return this;
        }

        public Builder fallback(LocatorType type, String value) {
            fallbacks.add(LocatorCandidate.of(type, value));
            return this;
        }

        public Builder fallbackRole(String role, String accessibleName) {
            fallbacks.add(LocatorCandidate.role(role, accessibleName));
            return this;
        }

        public Builder healingEnabled(boolean enabled) {
            this.healingEnabled = enabled;
            return this;
        }

        public UiLocator build() {
            if (primary == null) {
                throw new IllegalStateException("Primary locator is required for " + name);
            }
            return new UiLocator(name, primary, fallbacks, healingEnabled);
        }
    }
}
