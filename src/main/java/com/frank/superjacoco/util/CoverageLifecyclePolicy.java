package com.frank.superjacoco.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class CoverageLifecyclePolicy {

    public static final int DEFAULT_RUN_RETENTION_DAYS = 30;

    private CoverageLifecyclePolicy() {
    }

    public static String runExpireTimeUtc(int retentionDays) {
        return LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(retentionDays)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
