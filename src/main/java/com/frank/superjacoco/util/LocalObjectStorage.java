package com.frank.superjacoco.util;

import com.frank.superjacoco.config.CovPathProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class LocalObjectStorage {

    private final CovPathProperties covPathProperties;

    @Autowired
    public LocalObjectStorage(CovPathProperties covPathProperties) {
        this.covPathProperties = covPathProperties;
    }

    public void putBytes(String objectKey, byte[] data) {
        Path target = resolveObjectPath(objectKey);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write object: " + objectKey, e);
        }
    }

    public byte[] getBytes(String objectKey) {
        try {
            return Files.readAllBytes(resolveObjectPath(objectKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read object: " + objectKey, e);
        }
    }

    public void copyFromPath(Path source, String objectKey) {
        Path target = resolveObjectPath(objectKey);
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy object: " + objectKey, e);
        }
    }

    public boolean exists(String objectKey) {
        return Files.exists(resolveObjectPath(objectKey));
    }

    public void deleteObject(String objectKey) {
        try {
            Files.deleteIfExists(resolveObjectPath(objectKey));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete object: " + objectKey, e);
        }
    }

    public Path resolveObjectPath(String objectKey) {
        return covPathProperties.reportRootPath().resolve(objectKey).toAbsolutePath().normalize();
    }
}
