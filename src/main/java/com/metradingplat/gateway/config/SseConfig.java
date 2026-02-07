package com.metradingplat.gateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class SseConfig {

    @Bean
    public GlobalFilter sseHeaderFilter() {
        return (exchange, chain) -> {
            String accept = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
            if (accept != null && accept.contains("text/event-stream")) {
                exchange.getResponse().getHeaders().add("X-Accel-Buffering", "no");
                exchange.getResponse().getHeaders().add("Cache-Control", "no-cache, no-store");
            }
            return chain.filter(exchange);
        };
    }
}
