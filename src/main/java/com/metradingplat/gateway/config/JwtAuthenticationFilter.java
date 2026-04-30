package com.metradingplat.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String token = null;

            if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            } else {
                // Intentar obtener el token del query parameter (útil para SSE)
                token = request.getQueryParams().getFirst("token");
            }

            if (token == null || token.isEmpty()) {
                return this.onError(exchange, "No Authorization token found", HttpStatus.UNAUTHORIZED);
            }

            try {
                jwtUtil.validateToken(token);
                
                List<String> roles = jwtUtil.getRoles(token);
                String username = jwtUtil.getUsername(token);
                
                // Extrae los roles y los convierte en un string separado por comas
                String rolesStr = String.join(",", roles);
                
                // Validación de Permisos (RBAC global)
                // Si el usuario NO es editor, y la petición es de modificación (POST, PUT, DELETE, PATCH), bloqueamos.
                String method = request.getMethod().name();
                boolean isModifyRequest = method.equals("POST") || method.equals("PUT") || 
                                          method.equals("DELETE") || method.equals("PATCH");
                                          
                if (isModifyRequest && !roles.contains("ROLE_EDITOR")) {
                    return this.onError(exchange, "Access Denied: You do not have permission to modify.", HttpStatus.FORBIDDEN);
                }
                
                // Modifica el request para añadir headers adicionales a los microservicios
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Name", username)
                        .header("X-User-Roles", rolesStr)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
                return this.onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(org.springframework.web.server.ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    public static class Config {
        // configuration properties
    }
}
