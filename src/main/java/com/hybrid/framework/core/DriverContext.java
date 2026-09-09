package com.hybrid.framework.core;

public final class DriverContext {
    private static final ThreadLocal<UiDriver> DRIVER = new ThreadLocal<>();

    private DriverContext() {
    }

    public static void set(UiDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("UiDriver cannot be null");
        }
        DRIVER.set(driver);
    }

    public static UiDriver get() {
        UiDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("UiDriver is not initialized for thread "
                    + Thread.currentThread().getName());
        }
        return driver;
    }

    public static UiDriver getOrNull() {
        return DRIVER.get();
    }

    public static void unload() {
        DRIVER.remove();
    }
}
