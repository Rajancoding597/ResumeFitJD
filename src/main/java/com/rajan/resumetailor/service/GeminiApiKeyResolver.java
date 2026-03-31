package com.rajan.resumetailor.service;

import com.rajan.resumetailor.config.GeminiProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiApiKeyResolver {

    public static final String HEADER_NAME = "X-Gemini-Api-Key";

    private final GeminiProperties properties;
    private final boolean userKeyRequired;

    public GeminiApiKeyResolver(
            GeminiProperties properties,
            @Value("${feature.user-gemini-key.required:true}") boolean userKeyRequired
    ) {
        this.properties = properties;
        this.userKeyRequired = userKeyRequired;
    }

    public String resolveForCurrentRequest() {
        String requestKey = sanitize(readRequestHeader(HEADER_NAME));
        if (requestKey != null) {
            return requestKey;
        }

        if (userKeyRequired) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Gemini API key is required for this hosted app.");
        }

        String configuredKey = sanitize(properties.getApiKey());
        if (configuredKey != null) {
            return configuredKey;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No Gemini API key is available. Enter your own key to continue.");
    }

    public boolean isUserKeyRequired() {
        return userKeyRequired;
    }

    private String readRequestHeader(String headerName) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return request == null ? null : request.getHeader(headerName);
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
