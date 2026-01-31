package com.xiaoju.basetech.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class InitConfig implements CommandLineRunner {

    @Autowired
    private CovPathProperties covPathProperties;

    @Override
    public void run(String... args) throws Exception {
        mkdirIfAbsent(covPathProperties.getLogRoot());
        mkdirIfAbsent(covPathProperties.getReportRoot());
        mkdirIfAbsent(covPathProperties.getCodeRoot());
        mkdirIfAbsent(covPathProperties.getLocalCovRoot());

    }

    private void mkdirIfAbsent(String path) {
        if (path == null) {
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }
    }
}
