package com.frank.superjacoco.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitDiffParser {

    private static final Pattern HUNK_PATTERN =
            Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    public GitDiffResult diff(String oldCommit, String newCommit, String localRepoPath) {
        String output = executeGitDiff(localRepoPath, oldCommit, newCommit);
        return parseDiffOutput(output);
    }

    private String executeGitDiff(String repoPath, String oldCommit, String newCommit) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "git", "diff", "--find-renames", "--unified=0", oldCommit, newCommit
        );
        processBuilder.directory(new File(repoPath));
        try {
            Process process = processBuilder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            String output = new String(bytes, StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                throw new RuntimeException("git diff exited with code " + code);
            }
            return output;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute git diff.", e);
        }
    }

    public GitDiffResult parseDiffOutput(String diffOutput) {
        GitDiffResult result = new GitDiffResult();
        String[] lines = diffOutput.split("\n");
        FileDiff currentFileDiff = null;
        for (String line : lines) {
            if (line.startsWith("diff --git ")) {
                String[] parts = line.split(" ");
                String oldPath = parts[2].replaceFirst("^a/", "");
                String newPath = parts[3].replaceFirst("^b/", "");
                currentFileDiff = new FileDiff(oldPath, newPath);
                result.addFileDiff(currentFileDiff);
                continue;
            }
            if (currentFileDiff == null) {
                continue;
            }
            if (line.startsWith("rename from ")) {
                currentFileDiff.setRenamed(true);
                continue;
            }
            if (line.startsWith("deleted file mode ")) {
                currentFileDiff.setDeleted(true);
                continue;
            }
            if (line.startsWith("@@ ")) {
                parseHunk(line, currentFileDiff);
            }
        }
        return result;
    }

    private void parseHunk(String line, FileDiff fileDiff) {
        Matcher matcher = HUNK_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }
        int oldStart = Integer.parseInt(matcher.group(1));
        int oldLen = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
        int newStart = Integer.parseInt(matcher.group(3));
        int newLen = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
        fileDiff.addChangedRange(oldStart, oldLen, newStart, newLen);
    }
}
