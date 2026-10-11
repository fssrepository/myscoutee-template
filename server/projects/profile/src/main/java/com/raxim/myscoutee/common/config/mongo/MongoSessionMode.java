package com.raxim.myscoutee.common.config.mongo;

import org.springframework.util.StringUtils;

public enum MongoSessionMode {
    REAL,
    DEMO;

    public static MongoSessionMode fromRequestValue(String value) {
        return StringUtils.hasText(value) && "demo".equalsIgnoreCase(value.trim())
                ? DEMO
                : REAL;
    }
}
