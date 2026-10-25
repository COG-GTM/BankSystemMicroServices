package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RequestLoggingInterceptorTest {

    private final Logger logger = mock(Logger.class);
    private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor(logger);

    @Test
    void logsMatchedPatternMethodHostAndContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/things/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/things/{id}");
        request.addHeader("Host", "gateway:8080");
        request.setContentType("application/json");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod()));

        verify(logger).info(anyString(), eq("/v1/things/{id}"), eq("PUT"), eq("gateway:8080"), eq("application/json"));
    }

    @Test
    void stripsLineBreaksFromHeaderValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/things");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/things");
        request.addHeader("Host", "evil\r\nINFO\u2028forged\u2029x\u0085y\u000Bz\fw");

        interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());

        verify(logger).info(anyString(), eq("/v1/things"), eq("GET"), eq("evil__INFO_forged_x_y_z_w"), eq((Object) null));
    }

    @Test
    void ignoresNonControllerHandlers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/static.css");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verify(logger, never()).info(anyString(), any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }

    private HandlerMethod handlerMethod() throws NoSuchMethodException {
        return new HandlerMethod(this, getClass().getDeclaredMethod("handlerMethod"));
    }
}
