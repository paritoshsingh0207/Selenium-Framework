package com.hybrid.framework.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExcelSource {
    String file();
    String sheet();
    int headerRow() default 0;
    int dataStartRow() default -1;
    String[] requiredColumns() default {};
    boolean filterByRunMode() default false;
    String runModeColumn() default "RunMode";
}
