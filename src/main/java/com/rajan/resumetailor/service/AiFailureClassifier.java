package com.rajan.resumetailor.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiFailureClassifier {

    public boolean isQuotaExhausted(Throwable throwable) {
        ResponseStatusException response = findResponseStatusException(throwable);
        if (response != null) {
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return true;
            }
            String reason = response.getReason() == null ? "" : response.getReason().toLowerCase(Locale.ROOT);
            if (reason.contains("quota") || reason.contains("resource_exhausted") || reason.contains("rate limit")) {
                return true;
            }
        }

        String message = throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("quota") || message.contains("resource_exhausted") || message.contains("rate limit");
    }

    public boolean isTransient(Throwable throwable) {
        ResponseStatusException response = findResponseStatusException(throwable);
        if (response != null) {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            if (status == null) {
                return false;
            }
            return status == HttpStatus.TOO_MANY_REQUESTS
                    || status == HttpStatus.BAD_GATEWAY
                    || status == HttpStatus.SERVICE_UNAVAILABLE
                    || status == HttpStatus.GATEWAY_TIMEOUT;
        }
        return false;
    }

    private ResponseStatusException findResponseStatusException(Throwable throwable) {
        Throwable current = throwable;
        for (int i = 0; i < 8 && current != null; i++) {
            if (current instanceof ResponseStatusException response) {
                return response;
            }
            current = current.getCause();
        }
        return null;
    }
}

