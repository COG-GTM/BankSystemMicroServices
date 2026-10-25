package ru.kondrashov.personservice.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestLoggingInterceptor implements HandlerInterceptor {

    private final Logger logger;

    public RequestLoggingInterceptor() {
        this(LogManager.getLogger(RequestLoggingInterceptor.class));
    }

    RequestLoggingInterceptor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            logger.info("The controller \"{}\" received a {} request from the server {} with content type {}",
                    request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                    request.getMethod(),
                    stripLineBreaks(request.getHeader(HttpHeaders.HOST)),
                    stripLineBreaks(request.getContentType()));
        }
        return true;
    }

    private static String stripLineBreaks(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }
}
