package com.xiaoju.basetech.util;


import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public class SafeFileOps {

    public static void deleteRecursively(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("target path is outside root");
        }
        if (!Files.exists(normalizedTarget)) {
            return;
        }
        Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyDirectory(Path sourceRoot, Path sourceDir, Path targetRoot, Path targetDir) throws IOException {
        Path normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();
        Path normalizedTargetRoot = targetRoot.toAbsolutePath().normalize();
        Path normalizedSource = sourceDir.toAbsolutePath().normalize();
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();

        if (!normalizedSource.startsWith(normalizedSourceRoot) || !normalizedTarget.startsWith(normalizedTargetRoot)) {
            throw new IllegalArgumentException("source/target path is outside root");
        }
        if (!Files.exists(normalizedSource) || !Files.isDirectory(normalizedSource)) {
            throw new IllegalArgumentException("source is not a directory");
        }

        Files.createDirectories(normalizedTarget);
        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = normalizedSource.relativize(dir);
                Files.createDirectories(normalizedTarget.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = normalizedSource.relativize(file);
                Files.copy(file, normalizedTarget.resolve(rel), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
