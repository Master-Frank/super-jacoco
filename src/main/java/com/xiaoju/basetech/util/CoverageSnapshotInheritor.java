package com.xiaoju.basetech.util;

import com.xiaoju.basetech.entity.CoverageSnapshotEntity;

import java.util.HashMap;
import java.util.Map;

public class CoverageSnapshotInheritor {

    public CoverageSnapshotEntity inherit(CoverageSnapshotEntity oldSnapshot,
                                          String newCommitId,
                                          GitDiffResult diffResult,
                                          Map<String, Map<Integer, String>> newSourceLines) {
        CoverageSnapshotEntity inherited = new CoverageSnapshotEntity();
        inherited.getMetadata().setCoverageSetId(oldSnapshot.getMetadata().getCoverageSetId());
        inherited.getMetadata().setGitUrl(oldSnapshot.getMetadata().getGitUrl());
        inherited.getMetadata().setBranch(oldSnapshot.getMetadata().getBranch());
        inherited.getMetadata().setCommitId(newCommitId);
        inherited.getMetadata().setGeneratedAt(String.valueOf(System.currentTimeMillis()));
        inherited.getMetadata().setGeneratorVersion("super-jacoco-cumulative-coverage");

        for (Map.Entry<String, CoverageSnapshotEntity.FileCoverage> fileEntry : oldSnapshot.getFiles().entrySet()) {
            String oldFilePath = fileEntry.getKey();
            if (diffResult != null && diffResult.isFileDeleted(oldFilePath)) {
                continue;
            }

            String newFilePath = oldFilePath;
            if (diffResult != null && diffResult.getNewPath(oldFilePath) != null) {
                newFilePath = diffResult.getNewPath(oldFilePath);
            }
            FileDiff fileDiff = diffResult == null ? null : diffResult.getFileDiff(oldFilePath);
            CoverageSnapshotEntity.FileCoverage oldFile = fileEntry.getValue();
            CoverageSnapshotEntity.FileCoverage newFile = new CoverageSnapshotEntity.FileCoverage();

            Map<String, Integer> hashToNewLineNo = buildHashToNewLineNo(newFilePath, newSourceLines);
            for (Map.Entry<String, CoverageSnapshotEntity.LineCoverage> lineEntry : oldFile.getLines().entrySet()) {
                int oldLineNo = Integer.parseInt(lineEntry.getKey());
                CoverageSnapshotEntity.LineCoverage oldLine = lineEntry.getValue();
                if (fileDiff == null || fileDiff.getChangedRanges().isEmpty()) {
                    newFile.getLines().put(String.valueOf(oldLineNo), oldLine);
                    continue;
                }
                if (!isInChangedRange(oldLineNo, fileDiff)) {
                    int newLineNo = mapUnchangedLineNo(oldLineNo, fileDiff);
                    newFile.getLines().put(String.valueOf(newLineNo), oldLine);
                    continue;
                }
                if (oldLine.getHash() != null && !oldLine.getHash().isEmpty()) {
                    Integer movedLineNo = hashToNewLineNo.get(oldLine.getHash());
                    if (movedLineNo != null && !isInNewChangedRange(movedLineNo, fileDiff)) {
                        newFile.getLines().put(String.valueOf(movedLineNo), oldLine);
                    }
                }
            }
            if (!newFile.getLines().isEmpty()) {
                inherited.getFiles().put(newFilePath, newFile);
            }
        }
        return inherited;
    }

    private boolean isInChangedRange(int oldLineNo, FileDiff fileDiff) {
        for (FileDiff.ChangedRange range : fileDiff.getChangedRanges()) {
            int start = range.getOldStart();
            int endExclusive = start + Math.max(range.getOldLen(), 0);
            if (oldLineNo >= start && oldLineNo < endExclusive) {
                return true;
            }
        }
        return false;
    }

    private int mapUnchangedLineNo(int oldLineNo, FileDiff fileDiff) {
        int delta = 0;
        for (FileDiff.ChangedRange range : fileDiff.getChangedRanges()) {
            if (oldLineNo > (range.getOldStart() + Math.max(range.getOldLen(), 0) - 1)) {
                delta += range.getNewLen() - range.getOldLen();
            }
        }
        return oldLineNo + delta;
    }

    private boolean isInNewChangedRange(int newLineNo, FileDiff fileDiff) {
        for (FileDiff.ChangedRange range : fileDiff.getChangedRanges()) {
            int start = range.getNewStart();
            int endExclusive = start + Math.max(range.getNewLen(), 0);
            if (newLineNo >= start && newLineNo < endExclusive) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> buildHashToNewLineNo(String filePath, Map<String, Map<Integer, String>> newSourceLines) {
        Map<String, Integer> hashToLine = new HashMap<>();
        if (newSourceLines == null) {
            return hashToLine;
        }
        Map<Integer, String> lineMap = newSourceLines.get(filePath);
        if (lineMap == null) {
            return hashToLine;
        }
        for (Map.Entry<Integer, String> entry : lineMap.entrySet()) {
            hashToLine.put(CoverageLineHashUtil.hash(entry.getValue()), entry.getKey());
        }
        return hashToLine;
    }
}
