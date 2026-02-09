package com.xiaoju.basetech.util;


import com.xiaoju.basetech.entity.ErrorCode;
import com.xiaoju.basetech.entity.ResponseException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InputValidator {

    private static final int UUID_MAX_LEN = 80;

    public static void requireSafeUuid(String uuid) {
        if (StringUtils.isEmpty(uuid)) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "uuid不能为空");
        }
        if (uuid.length() > UUID_MAX_LEN) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "uuid过长");
        }
        if (!uuid.matches("[A-Za-z0-9_-]+")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "uuid不合法");
        }
    }

    public static void requireSafeVersion(String version, String field) {
        if (StringUtils.isEmpty(version)) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        if (version.length() > 200) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "过长");
        }
        if (!version.matches("[A-Za-z0-9_./-]+")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不合法");
        }
        if (version.contains("..") || version.startsWith("/") || version.startsWith(".")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不合法");
        }
    }

    public static void requireSafeSubModule(String subModule) {
        if (StringUtils.isEmpty(subModule)) {
            return;
        }
        if (subModule.length() > 500) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "subModule过长");
        }
        if (!subModule.matches("[A-Za-z0-9_./-]+")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "subModule不合法");
        }
        if (subModule.contains("..") || subModule.startsWith("/") || subModule.startsWith(".")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "subModule不合法");
        }
    }

    public static void requireSafeGitUrl(String gitUrl) {
        if (StringUtils.isEmpty(gitUrl)) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不能为空");
        }
        if (gitUrl.length() > 500) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl过长");
        }
        if (gitUrl.contains("\n") || gitUrl.contains("\r") || gitUrl.contains("\t")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
        }

        if (gitUrl.matches("^[A-Za-z]:[\\\\/].*") || gitUrl.startsWith("\\\\")) {
            Path p = Paths.get(gitUrl).toAbsolutePath().normalize();
            if (!p.isAbsolute()) {
                throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
            }
            return;
        }

        if (gitUrl.startsWith("git@")) {
            if (!gitUrl.matches("git@[A-Za-z0-9._-]+:[A-Za-z0-9._/-]+(\\.git)?")) {
                throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
            }
            return;
        }

        try {
            URI uri = URI.create(gitUrl);
            String scheme = uri.getScheme();
            if (scheme == null) {
                throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("ssh") && !scheme.equalsIgnoreCase("file")) {
                throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, "gitUrl不合法");
        }
    }

    public static Path requirePathWithinRoot(String path, Path root, String field) {
        if (StringUtils.isEmpty(path)) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        if (path.contains("\n") || path.contains("\r") || path.contains("\t")) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不合法");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = Paths.get(path).toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new ResponseException(ErrorCode.BAD_REQUEST, field + "不合法");
        }
        return normalized;
    }
}
