package com.paritosh.photosmigrator.model;

import java.util.Locale;

public enum AccountRole {
    SOURCE,
    DESTINATION;

    public static AccountRole fromPath(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
