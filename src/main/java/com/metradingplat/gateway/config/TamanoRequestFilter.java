package com.metradingplat.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Filtro global que rechaza peticiones con payload demasiado grande.
 * Previene ataques de payload masivo que saturarían la memoria del gateway.
 * Límite: 2 MB.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TamanoRequestFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(TamanoRequestFilter.class);

    private static final long TAMANO_MAXIMO_BYTES = 2L * 1024 * 1024; // 2 MB

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String contentLengthHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                long tamano = Long.parseLong(contentLengthHeader);
                if (tamano > TAMANO_MAXIMO_BYTES) {
                    log.warn("[TAMANO-REQUEST] Payload demasiado grande: {} bytes desde IP: {}",
                            tamano, exchange.getRequest().getRemoteAddress());
                    exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
                    exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    byte[] cuerpo = "{\"error\":\"Payload demasiado grande\",\"mensaje\":\"El tamaño máximo permitido es 2MB.\"}".getBytes(StandardCharsets.UTF_8);
                    var buffer = exchange.getResponse().bufferFactory().wrap(cuerpo);
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                }
            } catch (NumberFormatException ignorar) {
                // Content-Length inválido: Netty lo manejará internamente
            }
        }
        return chain.filter(exchange);
    }
}
