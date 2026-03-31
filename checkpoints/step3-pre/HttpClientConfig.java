package com.rajan.resumetailor.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        // Shared client across all AI calls. Per-request timeouts are applied on HttpRequest.
        return HttpClient.newBuilder().build();
    }
}

