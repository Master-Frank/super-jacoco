package com.xiaoju.basetech.util;

import com.xiaoju.basetech.entity.CoverageReportEntity;
import com.xiaoju.basetech.config.CovPathProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;


/**
 * @description:
 * @author: charlynegaoweiwei
 * @time: 2020/4/28 4:59 下午
 */
@Component
public class CodeCompilerExecutor {

    @Autowired
    private CovPathProperties covPathProperties;

    public void compileCode(CoverageReportEntity coverageReport) {
        String logFile = coverageReport.getLogFile().replace(LocalIpUtils.getTomcatBaseUrl() + "logs/", covPathProperties.getLogRoot());
        Path logFilePath = Paths.get(logFile);
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        if (!StringUtils.isEmpty(coverageReport.getEnvType())) {
            cmd.add("-P" + coverageReport.getEnvType());
        }
        cmd.add("clean");
        cmd.add("compile");
        try {
            int exitCode = CmdExecutor.executeCmd(cmd, Paths.get(coverageReport.getNowLocalPath()), 600000L, logFilePath);
            if (exitCode != 0) {
                coverageReport.setRequestStatus(Constants.JobStatus.COMPILE_FAIL.val());
                coverageReport.setErrMsg("编译代码出错");
            } else {
                coverageReport.setRequestStatus(Constants.JobStatus.COMPILE_DONE.val());
            }
        } catch (TimeoutException e) {
            coverageReport.setRequestStatus(Constants.JobStatus.COMPILE_FAIL.val());
            coverageReport.setErrMsg("编译代码超过了10分钟");
        } catch (Exception e) {
            coverageReport.setErrMsg("编译代码发生未知错误");
            coverageReport.setRequestStatus(Constants.JobStatus.COMPILE_FAIL.val());
        }
    }

}
