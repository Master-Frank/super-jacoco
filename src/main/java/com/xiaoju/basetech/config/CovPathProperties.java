package com.xiaoju.basetech.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Component
public class CovPathProperties {

    @Value("${cov.paths.codeRoot:${user.home}/app/super_jacoco/clonecode/}")
    private String codeRoot;

    @Value("${cov.paths.logRoot:${user.home}/report/logs/}")
    private String logRoot;

    @Value("${cov.paths.reportRoot:${user.home}/report/}")
    private String reportRoot;

    @Value("${cov.paths.jacocoResourceRoot:${user.home}/resource/jacoco-resources}")
    private String jacocoResourceRoot;

    @Value("${cov.paths.jacocoCliJar:${user.home}/org.jacoco.cli-0.8.14-nodeps.jar}")
    private String jacocoCliJar;

    @Value("${cov.paths.localCovRoot:${user.home}/cover/}")
    private String localCovRoot;

    public Path codeRootPath() {
        return Paths.get(codeRoot).toAbsolutePath().normalize();
    }

    public Path logRootPath() {
        return Paths.get(logRoot).toAbsolutePath().normalize();
    }

    public Path reportRootPath() {
        return Paths.get(reportRoot).toAbsolutePath().normalize();
    }

    public Path jacocoResourceRootPath() {
        return Paths.get(jacocoResourceRoot).toAbsolutePath().normalize();
    }

    public Path localCovRootPath() {
        return Paths.get(localCovRoot).toAbsolutePath().normalize();
    }
}
