package com.send_money;

import java.io.IOException;

import com.send_money.common.RequestLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTests {

    RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void reusesCorrelationAndLogsRequestWithoutQuery(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wallet");
        request.addHeader("X-Request-Id", "postman-123");
        request.addHeader("X-User-Id", "1");
        request.setQueryString("secret=do-not-log");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("postman-123");
            assertThat(MDC.get("userId")).isEqualTo("1");
            ((MockHttpServletResponse) res).setStatus(404);
        });
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("postman-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(output.getOut()).contains("api_request method=GET path=/api/v1/wallet status=404 durationMs=")
                .doesNotContain("do-not-log");
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-Id"))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeIdsAndRestoresContextAfterException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "unsafe\r\ninjected");
        MDC.put("requestId", "previous-context");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new IOException("test error");
        })).isInstanceOf(IOException.class);
        assertThat(response.getHeader("X-Request-Id")).doesNotContain("unsafe", "\r", "\n");
        assertThat(MDC.get("requestId")).isEqualTo("previous-context");
        assertThat(MDC.get("userId")).isNull();
    }
}
