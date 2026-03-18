package com.frank.superjacoco.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class CovSecurityProperties {

    @Value("${cov.security.enabled:true}")
    private boolean enabled;

    @Value("${cov.security.token:}")
    private String token;

    @Value("${cov.security.header:Authorization}")
    private String header;

    @Value("${cov.security.trustXForwardedFor:false}")
    private boolean trustXForwardedFor;

    @Value("${cov.security.ipAllowlist.enabled:false}")
    private boolean ipAllowlistEnabled;

    @Value("${cov.security.ipAllowlist:}")
    private String ipAllowlist;
}

