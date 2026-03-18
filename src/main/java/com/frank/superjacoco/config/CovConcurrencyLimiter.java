package com.frank.superjacoco.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

@Component
public class CovConcurrencyLimiter {

    private final Semaphore semaphore;
    private final boolean enabled;

    public CovConcurrencyLimiter(@Value("${cov.security.concurrencyLimit:2}") int limit) {
        if (limit <= 0) {
            this.enabled = false;
            this.semaphore = null;
        } else {
            this.enabled = true;
            this.semaphore = new Semaphore(limit);
        }
    }

    public boolean tryAcquire() {
        if (!enabled) {
            return true;
        }
        return semaphore.tryAcquire();
    }

    public void release() {
        if (!enabled) {
            return;
        }
        semaphore.release();
    }
}
