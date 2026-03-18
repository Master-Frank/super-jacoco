package com.frank.superjacoco.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private CovSecurityInterceptor covSecurityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(covSecurityInterceptor).addPathPatterns("/cov/**");
        registry.addInterceptor(gzipJsonInterceptor()).addPathPatterns("/**/*.json.gz");
    }

    private HandlerInterceptor gzipJsonInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Content-Encoding", "gzip");
                response.setHeader("Vary", "Accept-Encoding");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                return true;
            }
        };
    }
}
