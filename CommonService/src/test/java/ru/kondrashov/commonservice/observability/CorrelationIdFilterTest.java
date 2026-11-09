package ru.kondrashov.commonservice.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesIncomingCorrelationIdAndEchoesItOnResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(mdcDuringRequest));

        assertThat(mdcDuringRequest.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(mdcDuringRequest));

        String generated = response.getHeader(CORRELATION_ID_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        assertThat(mdcDuringRequest.get()).isEqualTo(generated);
    }

    @Test
    void replacesBlankOrUnsafeCorrelationId() throws Exception {
        for (String unsafe : new String[]{"", "   ", "bad\r\nvalue", "<script>", repeat('a', 129)}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CORRELATION_ID_HEADER, unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

            filter.doFilter(request, response, capturingChain(mdcDuringRequest));

            String echoed = response.getHeader(CORRELATION_ID_HEADER);
            assertThat(echoed).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(echoed).toString()).isEqualTo(echoed);
            assertThat(mdcDuringRequest.get()).isEqualTo(echoed);
        }
    }

    @Test
    void clearsMdcAfterRequestEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo("abc-123");
    }

    private static FilterChain capturingChain(AtomicReference<String> mdcDuringRequest) {
        return (req, res) -> mdcDuringRequest.set(MDC.get(CORRELATION_ID_MDC_KEY));
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
