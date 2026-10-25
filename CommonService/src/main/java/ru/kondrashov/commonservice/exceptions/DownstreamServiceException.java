package ru.kondrashov.commonservice.exceptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public class DownstreamServiceException extends RuntimeException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpMethod method;
    private final String url;
    private final HttpStatus status;
    private final String responseBody;

    public DownstreamServiceException(HttpMethod method, String url, HttpStatus status, String responseBody, Throwable cause) {
        super(buildMessage(method, url, status, responseBody, cause), cause);
        this.method = method;
        this.url = url;
        this.status = status;
        this.responseBody = responseBody;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    /** Downstream HTTP status, or {@code null} when no response was received. */
    public HttpStatus getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getUserMessage() {
        if (status == null) {
            return "the downstream service is unavailable";
        }
        String userMessage = "the downstream service responded " + status.value() + " " + status.getReasonPhrase();
        // Only 4xx bodies carry caller-facing messages; 5xx details stay in the logs.
        String detail = status.is4xxClientError() ? extractDetail(responseBody) : null;
        return detail == null ? userMessage : userMessage + ": " + detail;
    }

    private static String buildMessage(HttpMethod method, String url, HttpStatus status, String responseBody, Throwable cause) {
        StringBuilder message = new StringBuilder().append(method).append(' ').append(url).append(" failed: ");
        if (status != null) {
            message.append(status.value()).append(' ').append(status.getReasonPhrase());
            if (responseBody != null && !responseBody.isEmpty()) {
                message.append(" body=").append(responseBody);
            }
        } else if (cause != null) {
            message.append(cause.getMessage());
        } else {
            message.append("no response");
        }
        return message.toString();
    }

    private static String extractDetail(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonNode message = OBJECT_MAPPER.readTree(body).get("message");
            return message != null && message.isTextual() && !message.asText().isEmpty() ? message.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
