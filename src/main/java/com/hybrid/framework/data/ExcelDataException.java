package com.hybrid.framework.data;

public final class ExcelDataException extends RuntimeException {
    public ExcelDataException(String message) {
        super(message);
    }

    public ExcelDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
