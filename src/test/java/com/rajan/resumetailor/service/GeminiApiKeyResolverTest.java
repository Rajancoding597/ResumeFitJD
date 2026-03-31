package com.rajan.resumetailor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rajan.resumetailor.config.GeminiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpServletRequest;

class GeminiApiKeyResolverTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void prefersRequestHeaderOverConfiguredKey() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("server-key");
        GeminiApiKeyResolver resolver = new GeminiApiKeyResolver(properties, false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GeminiApiKeyResolver.HEADER_NAME, "user-key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("user-key", resolver.resolveForCurrentRequest());
    }

    @Test
    void usesConfiguredKeyWhenFallbackAllowed() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey("server-key");
        GeminiApiKeyResolver resolver = new GeminiApiKeyResolver(properties, false);

        assertEquals("server-key", resolver.resolveForCurrentRequest());
    }

    @Test
    void failsWhenUserKeyIsRequiredAndMissing() {
        GeminiProperties properties = new GeminiProperties();
        GeminiApiKeyResolver resolver = new GeminiApiKeyResolver(properties, true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, resolver::resolveForCurrentRequest);
        assertEquals(400, exception.getStatusCode().value());
        assertEquals("A Gemini API key is required for this hosted app.", exception.getReason());
    }
}
