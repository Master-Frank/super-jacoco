package com.xiaoju.basetech.util;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileDiff {

    private final String oldPath;
    private final String newPath;
    private boolean renamed;
    private boolean deleted;
    private final List<ChangedRange> changedRanges = new ArrayList<>();

    public FileDiff(String oldPath, String newPath) {
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    public void addChangedRange(int oldStart, int oldLen, int newStart, int newLen) {
        changedRanges.add(new ChangedRange(oldStart, oldLen, newStart, newLen));
    }

    @Data
    public static class ChangedRange {
        private final int oldStart;
        private final int oldLen;
        private final int newStart;
        private final int newLen;
    }
}
