package com.metradingplat.gateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Filtro global de rate limiting e IP banning para el gateway.
 *
 * Comportamiento:
 * - IPs baneadas reciben 403 Forbidden de inmediato.
 * - IPs que superan el límite normal reciben 429 Too Many Requests.
 * - IPs que superan el umbral de baneo automático quedan baneadas por N minutos.
 * - Conexiones SSE (Accept: text/event-stream) están excluidas del rate limiting
 *   porque son de larga duración y ya tienen heartbeats cada 30s.
 *
 * No requiere Redis: todo en memoria con Caffeine + Bucket4j.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties props;

    /** Cache de buckets de rate limit normal por IP */
    private final Cache<String, Bucket> cacheBuckets;

    /** Cache de IPs baneadas. Valor = Instant de expiración del baneo */
    private final Cache<String, Instant> cacheIpsBaneadas;

    /** Cache de buckets para detección de comportamiento abusivo */
    private final Cache<String, Bucket> cacheBucketsBaneo;

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;

        this.cacheBuckets = Caffeine.newBuilder()
                .expireAfterAccess(props.getExpiracionBucketMinutos(), TimeUnit.MINUTES)
                .maximumSize(props.getMaximoIpsEnCache())
                .build();

        this.cacheIpsBaneadas = Caffeine.newBuilder()
                .expireAfterWrite(props.getDuracionBaneoMinutos(), TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();

        this.cacheBucketsBaneo = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .maximumSize(props.getMaximoIpsEnCache())
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.isHabilitado()) {
            return chain.filter(exchange);
        }

        // Excluir SSE: conexiones de larga duración con heartbeats propios
        String acceptHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
        if (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return chain.filter(exchange);
        }

        String ip = extraerIp(exchange);

        // Verificar si la IP está baneada
        Instant expiracionBaneo = cacheIpsBaneadas.getIfPresent(ip);
        if (expiracionBaneo != null && Instant.now().isBefore(expiracionBaneo)) {
            log.warn("[RATE-LIMIT] IP baneada bloqueada: {} - path: {}", ip, exchange.getRequest().getPath());
            return responderForbidden(exchange);
        }

        // Verificar bucket de detección de abuso (umbral de baneo automático)
        Bucket bucketBaneo = cacheBucketsBaneo.get(ip, k -> crearBucketBaneo());
        if (!bucketBaneo.tryConsume(1)) {
            Instant hasta = Instant.now().plus(Duration.ofMinutes(props.getDuracionBaneoMinutos()));
            cacheIpsBaneadas.put(ip, hasta);
            log.warn("[RATE-LIMIT] IP baneada automáticamente: {} por {} minutos - path: {}",
                    ip, props.getDuracionBaneoMinutos(), exchange.getRequest().getPath());
            return responderForbidden(exchange);
        }

        // Verificar bucket de rate limit normal
        Bucket bucket = cacheBuckets.get(ip, k -> crearBucketNormal());
        if (!bucket.tryConsume(1)) {
            log.debug("[RATE-LIMIT] Rate limit superado para IP: {} - path: {}", ip, exchange.getRequest().getPath());
            return responderTooManyRequests(exchange);
        }

        return chain.filter(exchange);
    }

    /**
     * Bucket normal: limitePeticionesPorMinuto tokens, recarga completa cada minuto.
     */
    private Bucket crearBucketNormal() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(props.getLimitePeticionesPorMinuto())
                .refillIntervally(props.getLimitePeticionesPorMinuto(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }

    /**
     * Bucket de detección de abuso: umbralBaneoAutomatico tokens por minuto.
     * Si se agota, la IP queda baneada automáticamente.
     */
    private Bucket crearBucketBaneo() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(props.getUmbralBaneoAutomatico())
                .refillIntervally(props.getUmbralBaneoAutomatico(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }

    /**
     * Extrae la IP real del cliente.
     * Cloudflare envía CF-Connecting-IP con la IP original (más confiable que X-Forwarded-For).
     */
    private String extraerIp(ServerWebExchange exchange) {
        String cfIp = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }

        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    private Mono<Void> responderTooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        byte[] cuerpo = "{\"error\":\"Demasiadas peticiones\",\"mensaje\":\"Límite de peticiones superado. Intente nuevamente en 1 minuto.\"}".getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(cuerpo);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> responderForbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        byte[] cuerpo = "{\"error\":\"Acceso bloqueado\",\"mensaje\":\"IP bloqueada temporalmente por comportamiento abusivo.\"}".getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(cuerpo);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
