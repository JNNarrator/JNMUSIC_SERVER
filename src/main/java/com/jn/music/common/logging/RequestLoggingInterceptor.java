package com.jn.music.common.logging;

import com.jn.music.common.TraceIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("REQUEST");
    private static final Set<String> SENSITIVE_HEADERS = Stream.of("authorization", "cookie", "x-trace-id")
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!log.isInfoEnabled()) {
            return true;
        }

        String traceId = TraceIdContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = "req_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "");
            TraceIdContext.setTraceId(traceId);
        }
        request.setAttribute("requestStartTime", System.currentTimeMillis());
        request.setAttribute("requestTraceId", traceId);

        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                headers.put(name, request.getHeader(name));
            }
        });

        String requestInfo;
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            requestInfo = "query=" + request.getQueryString();
        } else {
            requestInfo = "contentType=" + request.getContentType() + ", contentLength=" + request.getContentLength();
        }

        String controllerMethod = "";
        if (handler instanceof HandlerMethod handlerMethod) {
            controllerMethod = handlerMethod.getMethod().toGenericString();
        }

        log.info("REQUEST_START traceId={} method={} uri={} controller={} headers={} requestInfo={}",
                traceId, request.getMethod(), request.getRequestURI(), controllerMethod, headers, requestInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("requestStartTime");
        String traceId = (String) request.getAttribute("requestTraceId");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        if (ex != null || response.getStatus() >= 500) {
            log.error("REQUEST_END traceId={} status={} duration={}ms error={}", traceId, response.getStatus(), duration, ex != null ? ex.getMessage() : response.getStatus());
        } else {
            log.info("REQUEST_END traceId={} status={} duration={}ms", traceId, response.getStatus(), duration);
        }
        TraceIdContext.clearTraceId();
    }
}
