package com.frank.superjacoco.config;


import com.frank.superjacoco.entity.ErrorCode;
import com.frank.superjacoco.entity.ResponseException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class CovSecurityInterceptor implements HandlerInterceptor {

    private static final String ATTR_CONCURRENCY_ACQUIRED = CovSecurityInterceptor.class.getName() + ".concurrencyAcquired";

    private final CovSecurityProperties properties;
    private final CovConcurrencyLimiter concurrencyLimiter;

    public CovSecurityInterceptor(CovSecurityProperties properties, CovConcurrencyLimiter concurrencyLimiter) {
        this.properties = properties;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }

        if (properties.isIpAllowlistEnabled()) {
            String ip = getClientIp(request);
            if (!isAllowedIp(ip, properties.getIpAllowlist())) {
                throw new ResponseException(ErrorCode.FORBIDDEN, "IP not allowlisted");
            }
        }

        String expectedToken = properties.getToken();
        if (StringUtils.isEmpty(expectedToken)) {
            throw new ResponseException(ErrorCode.UNAUTHORIZED, "Token is not configured");
        }

        String got = extractToken(request, properties.getHeader());
        if (StringUtils.isEmpty(got) || !MessageDigest.isEqual(got.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseException(ErrorCode.UNAUTHORIZED, "Invalid token");
        }

        if (isHighCostEndpoint(request)) {
            boolean acquired = concurrencyLimiter.tryAcquire();
            if (!acquired) {
                throw new ResponseException(ErrorCode.TOO_MANY_REQUESTS, "Too many concurrent requests");
            }
            request.setAttribute(ATTR_CONCURRENCY_ACQUIRED, Boolean.TRUE);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object acquired = request.getAttribute(ATTR_CONCURRENCY_ACQUIRED);
        if (Boolean.TRUE.equals(acquired)) {
            concurrencyLimiter.release();
        }
    }

    private boolean isHighCostEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (StringUtils.isEmpty(path)) {
            return false;
        }
        return path.endsWith("/triggerUnitCover")
                || path.endsWith("/triggerEnvCov")
                || path.endsWith("/getLocalCoverResult");
    }

    private String extractToken(HttpServletRequest request, String headerName) {
        String raw = request.getHeader(headerName);
        if (StringUtils.isEmpty(raw)) {
            return "";
        }
        if ("Authorization".equalsIgnoreCase(headerName) && raw.startsWith("Bearer ")) {
            return raw.substring("Bearer ".length()).trim();
        }
        return raw.trim();
    }

    private String getClientIp(HttpServletRequest request) {
        if (properties.isTrustXForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (!StringUtils.isEmpty(xff)) {
                String first = xff.split(",")[0].trim();
                if (!StringUtils.isEmpty(first)) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isAllowedIp(String ip, String allowlist) {
        if (StringUtils.isEmpty(ip)) {
            return false;
        }
        List<String> rules = splitCsv(allowlist);
        if (rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (rule.equals(ip)) {
                return true;
            }
            if (rule.contains("/")) {
                if (isIpInCidr(ip, rule)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress netAddr = InetAddress.getByName(parts[0]);
            if (!(ipAddr instanceof Inet4Address) || !(netAddr instanceof Inet4Address)) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            int ipInt = ipv4ToInt((Inet4Address) ipAddr);
            int netInt = ipv4ToInt((Inet4Address) netAddr);
            int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
            return (ipInt & mask) == (netInt & mask);
        } catch (Exception ignore) {
            return false;
        }
    }

    private int ipv4ToInt(Inet4Address addr) {
        byte[] b = addr.getAddress();
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (StringUtils.isEmpty(csv)) {
            return out;
        }
        for (String raw : csv.split(",")) {
            String v = raw.trim();
            if (!StringUtils.isEmpty(v)) {
                out.add(v);
            }
        }
        return out;
    }
}
