package ru.kondrashov.commonservice.services;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public class DownstreamServiceException extends RuntimeException {

    private static final int MAX_BODY_LENGTH = 1000;

    private final HttpMethod method;
    private final String url;
    private final Integer statusCode;
    private final String responseBody;

    public DownstreamServiceException(HttpMethod method, String url, Integer statusCode, String responseBody, Throwable cause) {
        super(describe(method, url, statusCode, responseBody, cause), cause);
        this.method = method;
        this.url = url;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getStatusDescription() {
        if (statusCode == null) {
            return "no response";
        }
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status == null ? String.valueOf(statusCode) : status.value() + " " + status.getReasonPhrase();
    }

    private static String describe(HttpMethod method, String url, Integer statusCode, String responseBody, Throwable cause) {
        StringBuilder message = new StringBuilder()
                .append(method).append(' ').append(url).append(" failed: ");
        if (statusCode != null) {
            message.append("status ").append(statusCode);
        } else {
            message.append(cause == null ? "no response" : cause.toString());
        }
        if (responseBody != null && !responseBody.isEmpty()) {
            message.append(", body: ").append(responseBody.length() > MAX_BODY_LENGTH
                    ? responseBody.substring(0, MAX_BODY_LENGTH) + "..."
                    : responseBody);
        }
        return message.toString();
    }
}
