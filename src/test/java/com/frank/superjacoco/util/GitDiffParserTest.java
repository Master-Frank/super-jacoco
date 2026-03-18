package com.frank.superjacoco.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GitDiffParserTest {

    @Test
    void shouldParseDiffOutput() {
        String diff = ""
                + "diff --git a/src/Foo.java b/src/Foo.java\n"
                + "@@ -10,2 +10,3 @@\n"
                + "diff --git a/src/Bar.java b/src/Baz.java\n"
                + "rename from src/Bar.java\n"
                + "rename to src/Baz.java\n"
                + "@@ -1 +1 @@\n";

        GitDiffParser parser = new GitDiffParser();
        GitDiffResult result = parser.parseDiffOutput(diff);
        FileDiff foo = result.getFileDiff("src/Foo.java");
        Assertions.assertNotNull(foo);
        Assertions.assertEquals(1, foo.getChangedRanges().size());

        FileDiff bar = result.getFileDiff("src/Bar.java");
        Assertions.assertNotNull(bar);
        Assertions.assertTrue(bar.isRenamed());
        Assertions.assertEquals("src/Baz.java", bar.getNewPath());
    }

    @Test
    void shouldResolveDiffByNormalizedJavaSourcePath() {
        String diff = ""
                + "diff --git a/mod-a/src/main/java/com/example/Foo.java b/mod-a/src/main/java/com/example/Foo.java\n"
                + "@@ -9,1 +9,2 @@\n";

        GitDiffParser parser = new GitDiffParser();
        GitDiffResult result = parser.parseDiffOutput(diff);
        FileDiff fileDiff = result.getFileDiff("com/example/Foo.java");
        Assertions.assertNotNull(fileDiff);
        Assertions.assertEquals("com/example/Foo.java", result.getNewPath("com/example/Foo.java"));
    }
}
