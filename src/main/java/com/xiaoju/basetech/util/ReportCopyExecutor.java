package com.xiaoju.basetech.util;

import com.xiaoju.basetech.entity.CoverageReportEntity;
import com.xiaoju.basetech.config.CovPathProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

/**
 * @description:
 * @author: charlynegaoweiwei
 * @time: 2020/4/28 5:38 下午
 */
@Slf4j
@Component
public class ReportCopyExecutor {

    @Autowired
    private CovPathProperties covPathProperties;

    public void copyReport(CoverageReportEntity coverageReport) {
        try {
            File reportFile = new File(coverageReport.getReportFile());
            Path sourceDir = reportFile.getParentFile().toPath();
            Path targetDir = covPathProperties.reportRootPath().resolve(coverageReport.getUuid());

            Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
            Path normalizedSourceDir = sourceDir.toAbsolutePath().normalize();
            if (normalizedSourceDir.startsWith(normalizedTargetDir)) {
                coverageReport.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + coverageReport.getUuid() + "/index.html");
                coverageReport.setRequestStatus(Constants.JobStatus.COPYREPORT_DONE.val());
                return;
            }

            SafeFileOps.copyDirectory(covPathProperties.codeRootPath(), sourceDir, covPathProperties.reportRootPath(), targetDir);
            coverageReport.setReportUrl(LocalIpUtils.getTomcatBaseUrl() + coverageReport.getUuid() + "/index.html");
            coverageReport.setRequestStatus(Constants.JobStatus.COPYREPORT_DONE.val());
            return;
        } catch (Exception e) {
            log.error("uuid={}复制报告异常",coverageReport.getUuid(), e);
            coverageReport.setRequestStatus(Constants.JobStatus.COPYREPORT_FAIL.val());
            return;
        }
    }
}
