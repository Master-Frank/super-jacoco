package com.xiaoju.basetech.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CovJobExecutorConfig {

    @Bean(name = "covJobExecutor")
    public Executor covJobExecutor(
            @Value("${cov.job.executor.corePoolSize:2}") int corePoolSize,
            @Value("${cov.job.executor.maxPoolSize:2}") int maxPoolSize,
            @Value("${cov.job.executor.queueCapacity:0}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("cov-job-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}

