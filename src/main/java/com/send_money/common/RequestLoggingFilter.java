package com.send_money.common;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String suppliedId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = suppliedId != null && suppliedId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                ? suppliedId : UUID.randomUUID().toString();
        String userId = request.getHeader("X-User-Id");
        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put("userId", userId != null && userId.matches("[1-9][0-9]{0,18}") ? userId : "unknown");
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                // Exclude query strings, bodies, and arbitrary headers from logs.
                log.info("api_request method={} path={} status={} durationMs={}",
                        request.getMethod(), safePath(request.getRequestURI()),
                        completed ? response.getStatus() : 500, (System.nanoTime() - started) / 1_000_000);
            } finally {
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }

    private String safePath(String path) {
        String clean = path.replaceAll("[\\p{Cntrl}]", "_");
        return clean.substring(0, Math.min(clean.length(), 256));
    }
}
