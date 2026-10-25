package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LogManager.getLogger(RequestLoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            LOGGER.info("The controller \"{}\" received a {} request {} from the server {} with content type {}",
                    request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                    request.getMethod(),
                    logSafe(request.getRequestURI()),
                    logSafe(request.getHeader(HttpHeaders.HOST)),
                    logSafe(request.getContentType()));
        }
        return true;
    }

    static String logSafe(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            safe.append(Character.isISOControl(c) || c == '\u2028' || c == '\u2029' ? '_' : c);
        }
        return safe.toString();
    }
}
