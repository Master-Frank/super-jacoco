package com.frank.superjacoco.util;

import com.frank.superjacoco.entity.CoverageReportEntity;
import com.frank.superjacoco.config.CovPathProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;



/**
 * @description:
 * @author: charlynegaoweiwei
 * @time: 2020/4/27 3:38 PM
 */
@Component
@Slf4j
public class UnitTester {
    // 单元测试命令设置超时时间1小时
    private static final Long UNITTEST_TIMEOUT = 3600000L;

    @Autowired
    private CovPathProperties covPathProperties;

    private static String mavenCommand() {
        String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase().contains("win")) {
            return "mvn.cmd";
        }
        return "mvn";
    }

    private static Path resolveMavenWorkDir(CoverageReportEntity coverageReport) {
        return Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
    }

    private static void applySubModuleArgs(List<String> cmd, CoverageReportEntity coverageReport) {
        String subModule = coverageReport.getSubModule();
        if (StringUtils.isEmpty(subModule)) {
            return;
        }
        cmd.add("-pl");
        cmd.add(subModule);
        cmd.add("-am");
    }

    public void executeUnitTest(CoverageReportEntity coverageReport) {
        long startTime = System.currentTimeMillis();
        String logFile = coverageReport.getLogFile().replace(LocalIpUtils.getTomcatBaseUrl() + "logs/", covPathProperties.getLogRoot());
        Path logFilePath = Paths.get(logFile);
        List<String> cmd = new ArrayList<>();
        cmd.add(mavenCommand());
        if (!StringUtils.isEmpty(coverageReport.getEnvType())) {
            cmd.add("-P" + coverageReport.getEnvType());
        }
        applySubModuleArgs(cmd, coverageReport);
        cmd.add("clean");
        cmd.add("-Dmaven.test.skip=false");
        cmd.add("org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent");
        cmd.add("compile");
        cmd.add("test-compile");
        cmd.add("org.apache.maven.plugins:maven-surefire-plugin:2.22.1:test");
        cmd.add("org.apache.maven.plugins:maven-jar-plugin:2.4:jar");
        cmd.add("-Dmaven.test.failure.ignore=true");
        cmd.add("-Dfile.encoding=UTF-8");
        // 超时时间设置为一小时,
        int exitCode;
        try {
            Path workDir = resolveMavenWorkDir(coverageReport);
            exitCode = CmdExecutor.executeCmd(cmd, workDir, UNITTEST_TIMEOUT, logFilePath);
            log.info("单元测试执行结果exitCode={} uuid={}", exitCode, coverageReport.getUuid());
            if (exitCode == 0) {
                log.info("执行单元测试成功...");
                coverageReport.setRequestStatus(Constants.JobStatus.UNITTEST_DONE.val());
            } else {
                    coverageReport.setRequestStatus(Constants.JobStatus.UNITTEST_FAIL.val());
                    coverageReport.setErrMsg("执行单元测试报错");
            }
        } catch (TimeoutException e) {
            coverageReport.setRequestStatus(Constants.JobStatus.TIMEOUT.val());
            coverageReport.setErrMsg("执行单元测试超时");
        } catch (Exception e) {
            log.error("执行单元测试异常", e);
            coverageReport.setErrMsg("执行单元测试异常:" + e.getMessage());
            coverageReport.setRequestStatus(Constants.JobStatus.UNITTEST_FAIL.val());
        } finally {
            log.info("uuid={} 执行单元测试耗时{}ms", coverageReport.getUuid(), (System.currentTimeMillis() - startTime));
        }
    }
}
