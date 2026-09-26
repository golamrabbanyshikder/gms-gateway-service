package com.gms.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${internal.api-key}")
    private String internalApiKey;

    /**
     * Every internal service call goes through this single RestTemplate, so
     * attaching the X-API-Key header here covers backend/biometric/file-system
     * service calls uniformly - those services now reject any request without
     * a valid, active key (see ApiKeyAuthFilter in each of them).
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("X-API-Key", internalApiKey);
            return execution.execute(request, body);
        });
        return restTemplate;
    }

    /**
     * Dedicated RestTemplate for outbound calls to third-party APIs (e.g.
     * Anthropic Claude). Deliberately has no X-API-Key interceptor - the
     * internal gateway key would be leaked if we sent it to Anthropic.
     * Timeout is configurable so a slow LLM call never holds a request
     * thread for longer than the operator is willing to wait.
     */
    @Bean(name = "llmRestTemplate")
    public RestTemplate llmRestTemplate(
            @Value("${anthropic.timeout-ms:30000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }
}
