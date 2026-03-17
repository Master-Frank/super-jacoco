package com.xiaoju.basetech.service.impl;

import com.xiaoju.basetech.config.CovPathProperties;
import com.xiaoju.basetech.util.CoverageLifecyclePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class CoverageLifecycleScheduler {

    private final CovPathProperties covPathProperties;

    @Autowired
    public CoverageLifecycleScheduler(CovPathProperties covPathProperties) {
        this.covPathProperties = covPathProperties;
    }

    @Scheduled(fixedDelayString = "${cov.cumulative.lifecycle.fixedDelayMs:3600000}", initialDelayString = "${cov.cumulative.lifecycle.initialDelayMs:120000}")
    public void cleanupObjects() {
        try {
            cleanupRuns();
            cleanupSnapshotsKeepLatest();
        } catch (Exception e) {
            log.error("coverage lifecycle cleanup failed", e);
        }
    }

    private void cleanupRuns() throws IOException {
        Path reportsRoot = covPathProperties.reportRootPath();
        if (!Files.exists(reportsRoot)) {
            return;
        }
        Instant expire = Instant.now().minus(CoverageLifecyclePolicy.DEFAULT_RUN_RETENTION_DAYS, ChronoUnit.DAYS);
        try (Stream<Path> stream = Files.walk(reportsRoot)) {
            stream.filter(p -> p.toString().contains("\\runs\\") || p.toString().contains("/runs/"))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            FileTime modifiedTime = Files.getLastModifiedTime(path);
                            if (modifiedTime.toInstant().isBefore(expire)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (Exception e) {
                            log.warn("cleanup run object failed: {}", path, e);
                        }
                    });
        }
    }

    private void cleanupSnapshotsKeepLatest() throws IOException {
        Path reportsRoot = covPathProperties.reportRootPath();
        if (!Files.exists(reportsRoot)) {
            return;
        }
        try (Stream<Path> setDirs = Files.list(reportsRoot)) {
            for (Path setDir : setDirs.collect(Collectors.toList())) {
                Path snapshotsDir = setDir.resolve("snapshots");
                if (!Files.isDirectory(snapshotsDir)) {
                    continue;
                }
                List<Path> snapshotDirs;
                try (Stream<Path> stream = Files.list(snapshotsDir)) {
                    snapshotDirs = stream.filter(Files::isDirectory)
                            .sorted(Comparator.comparing(this::safeLastModified).reversed())
                            .collect(Collectors.toList());
                }
                for (int i = 1; i < snapshotDirs.size(); i++) {
                    deleteDir(snapshotDirs.get(i));
                }
            }
        }
    }

    private FileTime safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private void deleteDir(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("delete path failed: {}", path, e);
                }
            });
        }
    }
}
