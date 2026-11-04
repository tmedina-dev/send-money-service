package com.send_money.common;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class DownstreamLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DownstreamLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = MDC.get(RequestLoggingFilter.REQUEST_ID_KEY);
        if (requestId != null) {
            request.getHeaders().set(RequestLoggingFilter.REQUEST_ID_HEADER, requestId);
        }
        long started = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.info("downstream_request method={} host={} path={} status={} durationMs={}",
                    request.getMethod(), request.getURI().getHost(), request.getURI().getRawPath(),
                    response.getStatusCode().value(), (System.nanoTime() - started) / 1_000_000);
            return response;
        } catch (IOException | RuntimeException ex) {
            log.warn("downstream_request method={} host={} path={} error={} durationMs={}",
                    request.getMethod(), request.getURI().getHost(), request.getURI().getRawPath(),
                    ex.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        }
    }
}
