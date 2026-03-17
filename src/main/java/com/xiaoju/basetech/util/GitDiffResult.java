package com.xiaoju.basetech.util;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class GitDiffResult {

    private final Map<String, FileDiff> fileDiffMap = new HashMap<>();

    public void addFileDiff(FileDiff fileDiff) {
        putPath(fileDiff.getOldPath(), fileDiff);
        putPath(normalizeJavaSourcePath(fileDiff.getOldPath()), fileDiff);
    }

    public FileDiff getFileDiff(String oldPath) {
        FileDiff direct = fileDiffMap.get(oldPath);
        if (direct != null) {
            return direct;
        }
        return fileDiffMap.get(normalizeJavaSourcePath(oldPath));
    }

    public boolean isFileDeleted(String oldPath) {
        FileDiff fileDiff = getFileDiff(oldPath);
        return fileDiff != null && fileDiff.isDeleted();
    }

    public String getNewPath(String oldPath) {
        FileDiff fileDiff = getFileDiff(oldPath);
        if (fileDiff == null) {
            return null;
        }
        String normalizedOldPath = normalizeJavaSourcePath(oldPath);
        String normalizedFileOldPath = normalizeJavaSourcePath(fileDiff.getOldPath());
        if (normalizedOldPath != null && normalizedOldPath.equals(normalizedFileOldPath)) {
            return normalizeJavaSourcePath(fileDiff.getNewPath());
        }
        return fileDiff.getNewPath();
    }

    private void putPath(String path, FileDiff fileDiff) {
        if (path != null && !path.isEmpty()) {
            fileDiffMap.put(path, fileDiff);
        }
    }

    private String normalizeJavaSourcePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String normalized = path.replace('\\', '/');
        int marker = normalized.indexOf("/src/main/java/");
        if (marker >= 0) {
            return normalized.substring(marker + "/src/main/java/".length());
        }
        marker = normalized.indexOf("src/main/java/");
        if (marker >= 0) {
            return normalized.substring(marker + "src/main/java/".length());
        }
        return normalized;
    }
}
