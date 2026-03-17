package com.xiaoju.basetech.service.impl;

import com.xiaoju.basetech.dao.CoverageNodeStatsDao;
import com.xiaoju.basetech.dao.CoverageRunDao;
import com.xiaoju.basetech.dao.CoverageSetDao;
import com.xiaoju.basetech.dao.ReportArtifactDao;
import com.xiaoju.basetech.entity.CoverageRunEntity;
import com.xiaoju.basetech.entity.CoverageSetEntity;
import com.xiaoju.basetech.entity.CoverageSnapshotEntity;
import com.xiaoju.basetech.util.CoverageSnapshotCodec;
import com.xiaoju.basetech.util.LocalObjectStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;

import static org.mockito.ArgumentMatchers.eq;

class CumulativeCoverageServiceImplTest {

    @Test
    void shouldMarkFailedWhenCommitChangedButRepoPathMissing() throws Exception {
        CoverageSetDao setDao = Mockito.mock(CoverageSetDao.class);
        CoverageRunDao runDao = Mockito.mock(CoverageRunDao.class);
        CoverageNodeStatsDao nodeStatsDao = Mockito.mock(CoverageNodeStatsDao.class);
        ReportArtifactDao artifactDao = Mockito.mock(ReportArtifactDao.class);
        LocalObjectStorage storage = Mockito.mock(LocalObjectStorage.class);
        Executor executor = Runnable::run;

        CumulativeCoverageServiceImpl service = new CumulativeCoverageServiceImpl(
                setDao, runDao, nodeStatsDao, artifactDao, storage, executor
        );

        CoverageRunEntity run = new CoverageRunEntity();
        run.setRunId("r1");
        run.setCoverageSetId("s1");
        run.setCommitId("newc");
        run.setXmlObjectKey("reports/s1/runs/r1/jacoco.xml.gz");

        CoverageSetEntity set = new CoverageSetEntity();
        set.setCoverageSetId("s1");
        set.setCurrentCommitId("oldc");
        set.setCurrentSnapshotKey("reports/s1/snapshots/oldc/coverage_snapshot.json.gz");
        set.setRepoLocalPath(null);

        CoverageSnapshotEntity oldSnapshot = new CoverageSnapshotEntity();
        oldSnapshot.getMetadata().setCommitId("oldc");

        Mockito.when(runDao.selectById("r1")).thenReturn(run);
        Mockito.when(setDao.selectById("s1")).thenReturn(set);
        Mockito.when(storage.getBytes("reports/s1/runs/r1/jacoco.xml.gz")).thenReturn(gzip("<report/>"));
        Mockito.when(storage.getBytes("reports/s1/snapshots/oldc/coverage_snapshot.json.gz"))
                .thenReturn(CoverageSnapshotCodec.toGzipBytes(oldSnapshot));

        ReflectionTestUtils.invokeMethod(service, "processRun", "r1");

        Mockito.verify(runDao).updateStatus("r1", "PROCESSING", "");
        Mockito.verify(runDao).updateStatus(eq("r1"), eq("FAILED"), ArgumentMatchers.contains("git diff failed"));
    }

    private byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}
