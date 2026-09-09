package com.hybrid.framework.data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExcelRow {
    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "y", "1");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "n", "0");

    private final String source;
    private final String sheet;
    private final int rowNumber;
    private final Map<String, String> values;

    public ExcelRow(String source, String sheet, int rowNumber, Map<String, String> values) {
        this.source = Objects.requireNonNull(source);
        this.sheet = Objects.requireNonNull(sheet);
        this.rowNumber = rowNumber;
        this.values = Map.copyOf(new LinkedHashMap<>(values));
    }

    public String source() { return source; }
    public String sheet() { return sheet; }
    public int rowNumber() { return rowNumber; }
    public String executionId() { return source + "#" + sheet + "#row-" + rowNumber; }

    public boolean containsColumn(String name) {
        return find(name) != null;
    }

    public String get(String name) {
        String key = find(name);
        return key == null ? "" : values.getOrDefault(key, "");
    }

    public String getRequiredString(String name) {
        String value = get(name);
        if (value.isBlank()) {
            throw new ExcelDataException("Missing required column value '" + name + "' at " + sheet + " row " + rowNumber);
        }
        return value;
    }

    public int getInt(String name) {
        try {
            return new BigDecimal(getRequiredString(name).replace(",", "").trim()).intValueExact();
        } catch (RuntimeException exception) {
            throw new ExcelDataException("Cannot convert '" + name + "' to integer at row " + rowNumber, exception);
        }
    }

    public boolean getBoolean(String name) {
        String value = getRequiredString(name).trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) return true;
        if (FALSE_VALUES.contains(value)) return false;
        throw new ExcelDataException("Cannot convert '" + name + "' to boolean at row " + rowNumber);
    }

    public Map<String, String> asMap() { return values; }

    private String find(String requested) {
        if (requested == null) return null;
        return values.keySet().stream().filter(key -> key.equalsIgnoreCase(requested.trim())).findFirst().orElse(null);
    }

    @Override
    public String toString() {
        Map<String, String> safe = new LinkedHashMap<>();
        values.forEach((key, value) -> safe.put(key, isSensitive(key) ? "<masked>" : value));
        return "ExcelRow{sheet='" + sheet + "', row=" + rowNumber + ", values=" + safe + "}";
    }

    private boolean isSensitive(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("apikey") || normalized.contains("authorization") || normalized.contains("cvv");
    }
}
