# Gateway - Spring Cloud Gateway

API Gateway para la plataforma **MeTradingPlat**. Punto de entrada único (Single Entry Point) que enruta solicitudes HTTP a los microservicios apropiados usando **Spring Cloud Gateway**.

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Arquitectura](#arquitectura)
- [Tecnologías](#tecnologías)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Rutas Configuradas](#rutas-configuradas)
- [Filtros y Middleware](#filtros-y-middleware)
- [CORS](#cors)
- [Balanceo de Carga](#balanceo-de-carga)
- [Desarrollo](#desarrollo)
- [Monitoreo](#monitoreo)
- [Seguridad](#seguridad)

---

## Descripción General

**Gateway** actúa como el punto de entrada único para todos los clientes (web, móvil, terceros) hacia la arquitectura de microservicios. Proporciona:

- **Enrutamiento inteligente** a microservicios basado en paths
- **Balanceo de carga** automático entre instancias
- **CORS** configurado para aplicaciones web
- **Descubrimiento de servicios** vía Eureka
- **Filtros personalizados** para headers, logging, etc.

### Características Principales

- **Reactive Stack**: WebFlux para alta concurrencia
- **Service Discovery**: Integración con Eureka para descubrimiento dinámico
- **Virtual Threads**: Java 21 para rendimiento mejorado
- **CORS Global**: Configuración centralizada de CORS
- **Actuator**: Endpoints de monitoreo y métricas
- **Auto-configuración**: Descubrimiento automático de rutas desde Eureka

---

## Arquitectura

### Patrón: API Gateway

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENTES                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   Web    │  │  Mobile  │  │  Admin   │  │  Otros   │   │
│  │ Browser  │  │   App    │  │  Panel   │  │  APIs    │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
└───────┼─────────────┼─────────────┼─────────────┼──────────┘
        │             │             │             │
        └─────────────┴─────────────┴─────────────┘
                      │
                      │ HTTP/HTTPS
                      ▼
        ┌─────────────────────────────────┐
        │      SPRING CLOUD GATEWAY       │
        │         (Port 8080)             │
        ├─────────────────────────────────┤
        │                                 │
        │  ┌───────────────────────────┐  │
        │  │    ROUTING ENGINE         │  │
        │  │  - Path Predicates        │  │
        │  │  - Service Discovery      │  │
        │  │  - Load Balancing         │  │
        │  └───────────────────────────┘  │
        │                                 │
        │  ┌───────────────────────────┐  │
        │  │    FILTERS                │  │
        │  │  - CORS Filter            │  │
        │  │  - AddRequestHeader       │  │
        │  │  - Logging                │  │
        │  └───────────────────────────┘  │
        │                                 │
        └─────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │    EUREKA CLIENT          │
        │  "¿Dónde está el servicio │
        │   gestion-escaneres?"     │
        └─────────────┬─────────────┘
                      │
                      ▼
        ┌─────────────────────────────────┐
        │     EUREKA SERVER               │
        │    (directory:8761)             │
        │  "localhost:8081"               │
        └─────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        │                           │
        ▼                           ▼
┌─────────────────┐      ┌─────────────────┐
│ GESTION-        │      │ Otros           │
│ ESCANERES       │      │ Microservicios  │
│ (Port 8081)     │      │ ...             │
└─────────────────┘      └─────────────────┘
```

### Flujo de una Petición

1. **Cliente** → Envía request a `http://gateway:8080/api/escaner/...`
2. **Gateway** → Aplica filtros (CORS, headers)
3. **Gateway** → Consulta Eureka: "¿Dónde está `gestion-escaneres`?"
4. **Eureka** → Responde: `localhost:8081`
5. **Gateway** → Enruta a `http://localhost:8081/api/escaner/...`
6. **Microservicio** → Procesa y responde
7. **Gateway** → Devuelve respuesta al cliente

---

## Tecnologías

| Tecnología | Versión | Descripción |
|-----------|---------|-------------|
| **Java** | 21 | Lenguaje principal (Virtual Threads) |
| **Spring Boot** | 3.4.11 | Framework base |
| **Spring Cloud** | 2024.0.0 | Suite de microservicios |
| **Spring Cloud Gateway** | Latest | API Gateway reactivo |
| **Spring WebFlux** | 3.4.11 | Stack reactivo (Netty) |
| **Netflix Eureka Client** | Latest | Service Discovery |
| **Spring Actuator** | 3.4.11 | Monitoreo y métricas |
| **Maven** | 3.9+ | Gestión de dependencias |

---

## Instalación

### Prerrequisitos

- **Java 21+** (con soporte para Virtual Threads)
- **Maven 3.9+**
- **Eureka Server** ejecutándose en `http://localhost:8761`

### Clonar el Repositorio

```bash
git clone https://github.com/tu-org/metradingplat.git
cd metradingplat/gateway
```

### Compilar el Proyecto

```bash
./mvnw clean install
```

---

## Configuración

### Archivo: `application.properties`

```properties
# Nombre de la aplicación
spring.application.name=gateway

# Puerto del Gateway
server.port=8080

# Virtual Threads para alta concurrencia
spring.threads.virtual.enabled=true

# ============================================================
# CONFIGURACIÓN DE RUTAS
# ============================================================

# Ruta 1: Gestión de Escaners
spring.cloud.gateway.routes[0].id=gestion-escaneres
spring.cloud.gateway.routes[0].uri=lb://gestion-escaneres
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/escaner/**
spring.cloud.gateway.routes[0].filters[0]=AddRequestHeader=X-Gateway-Passed,true

# ============================================================
# DESCUBRIMIENTO AUTOMÁTICO
# ============================================================

# Habilitar descubrimiento automático desde Eureka
spring.cloud.gateway.discovery.locator.enabled=true
spring.cloud.gateway.discovery.locator.lower-case-service-id=true

# Perfil activo
spring.profiles.active=dev
```

### Configuración de Eureka Client

El Gateway se registra automáticamente en Eureka usando la configuración por defecto:

```properties
# URL de Eureka Server (por defecto)
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

### Ejecutar el Servicio

```bash
./mvnw spring-boot:run
```

El gateway estará disponible en: `http://localhost:8080`

---

## Rutas Configuradas

### Rutas Estáticas

| Path | Servicio Destino | Descripción |
|------|-----------------|-------------|
| `/api/escaner/**` | `gestion-escaneres` | API de gestión de escaners de mercado |

### Rutas Dinámicas (Auto-descubiertas)

Con `spring.cloud.gateway.discovery.locator.enabled=true`, el Gateway crea automáticamente rutas para todos los servicios registrados en Eureka:

```
http://localhost:8080/{service-name}/**
```

**Ejemplo:**
```
http://localhost:8080/gestion-escaneres/api/escaneres
```

### Configuración de Rutas Adicionales

Para agregar nuevas rutas manualmente:

```properties
# Ruta 2: Ejemplo - Servicio de Usuarios
spring.cloud.gateway.routes[1].id=usuarios
spring.cloud.gateway.routes[1].uri=lb://usuarios
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/usuarios/**
spring.cloud.gateway.routes[1].filters[0]=AddRequestHeader=X-Gateway-Passed,true
```

---

## Filtros y Middleware

### Filtros Globales

El Gateway aplica filtros a todas las peticiones:

#### 1. **CORS Filter** (Ver sección [CORS](#cors))

Configurado en [`CorsConfig.java`](src/main/java/com/metradingplat/gateway/config/CorsConfig.java:1):
- Permite orígenes específicos
- Habilita métodos HTTP comunes
- Permite headers personalizados

#### 2. **AddRequestHeader Filter**

Agrega headers personalizados a las peticiones:

```properties
spring.cloud.gateway.routes[0].filters[0]=AddRequestHeader=X-Gateway-Passed,true
```

**Ejemplo de uso en microservicio:**
```java
@GetMapping("/test")
public String test(@RequestHeader("X-Gateway-Passed") String gatewayPassed) {
    // Verificar que la petición pasó por el gateway
    return "Gateway passed: " + gatewayPassed;
}
```

### Filtros Personalizados Disponibles

Spring Cloud Gateway proporciona múltiples filtros built-in:

| Filtro | Descripción | Ejemplo |
|--------|-------------|---------|
| `AddRequestHeader` | Agrega header a request | `AddRequestHeader=X-Custom,Value` |
| `AddResponseHeader` | Agrega header a response | `AddResponseHeader=X-Response,OK` |
| `RewritePath` | Reescribe el path | `RewritePath=/old/(?<segment>.*), /new/${segment}` |
| `StripPrefix` | Elimina prefijos del path | `StripPrefix=1` |
| `Retry` | Reintentar peticiones fallidas | `Retry=3` |
| `CircuitBreaker` | Implementar circuit breaker | `CircuitBreaker=myCircuitBreaker` |

---

## CORS

### Configuración Global

El Gateway tiene configurado CORS globalmente en [`CorsConfig.java`](src/main/java/com/metradingplat/gateway/config/CorsConfig.java:1):

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Orígenes permitidos
        corsConfig.setAllowedOrigins(Arrays.asList(
            "http://localhost",
            "https://metradingplat.com",
            "https://www.metradingplat.com",
            "http://metradingplat.com",
            "http://www.metradingplat.com"
        ));

        // Métodos HTTP permitidos
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // Headers permitidos
        corsConfig.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With",
            "Accept", "Origin", "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));

        // Habilitar credenciales (cookies)
        corsConfig.setAllowCredentials(true);

        // Tiempo de caché de preflight (1 hora)
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
```

### Agregar Nuevos Orígenes

Para permitir nuevos orígenes, edita el array `allowedOrigins`:

```java
corsConfig.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",           // Frontend local
    "https://app.metradingplat.com",   // Producción
    "https://admin.metradingplat.com"  // Admin panel
));
```

---

## Balanceo de Carga

El Gateway implementa **Client-Side Load Balancing** usando Eureka:

### Cómo Funciona

1. **Gateway** consulta Eureka por el servicio `gestion-escaneres`
2. **Eureka** devuelve **todas las instancias disponibles**:
   ```
   - gestion-escaneres:8081
   - gestion-escaneres:8082
   - gestion-escaneres:8083
   ```
3. **Gateway** balancea las peticiones usando **Round Robin** por defecto

### Configuración de Load Balancer

Para personalizar el algoritmo de balanceo:

```properties
# Algoritmo de balanceo (Round Robin es el default)
spring.cloud.loadbalancer.ribbon.enabled=false
```

### Algoritmos Disponibles

- **Round Robin** (default): Distribuye equitativamente
- **Random**: Selección aleatoria
- **Weighted**: Basado en pesos configurados

---

## Desarrollo

### Estructura del Proyecto

```
gateway/
│
├── src/main/java/com/metradingplat/gateway/
│   ├── GatewayApplication.java           # Clase principal
│   └── config/
│       └── CorsConfig.java               # Configuración CORS
│
├── src/main/resources/
│   └── application.properties            # Configuración principal
│
└── pom.xml                               # Dependencias Maven
```

### Código Principal

```java
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### Agregar Nuevas Rutas

1. **Editar `application.properties`:**

```properties
spring.cloud.gateway.routes[1].id=nuevo-servicio
spring.cloud.gateway.routes[1].uri=lb://nuevo-servicio
spring.cloud.gateway.routes[1].predicates[0]=Path=/api/nuevo/**
```

2. **O crear una clase de configuración:**

```java
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("nuevo-servicio", r -> r
                .path("/api/nuevo/**")
                .uri("lb://nuevo-servicio"))
            .build();
    }
}
```

### Agregar Filtros Personalizados

```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("Request: {} {}",
            exchange.getRequest().getMethod(),
            exchange.getRequest().getURI());

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Ejecutar primero
    }
}
```

---

## Monitoreo

### Actuator Endpoints

| Endpoint | Descripción |
|----------|-------------|
| `/actuator/health` | Estado de salud del gateway |
| `/actuator/gateway/routes` | Rutas configuradas |
| `/actuator/gateway/refresh` | Refrescar rutas dinámicas |
| `/actuator/metrics` | Métricas del gateway |

### Ver Rutas Configuradas

```bash
curl http://localhost:8080/actuator/gateway/routes | jq
```

**Respuesta:**
```json
[
  {
    "route_id": "gestion-escaneres",
    "route_definition": {
      "id": "gestion-escaneres",
      "predicates": [
        {
          "name": "Path",
          "args": {
            "pattern": "/api/escaner/**"
          }
        }
      ],
      "filters": [
        {
          "name": "AddRequestHeader",
          "args": {
            "name": "X-Gateway-Passed",
            "value": "true"
          }
        }
      ],
      "uri": "lb://gestion-escaneres",
      "order": 0
    }
  }
]
```

### Refrescar Rutas

Para recargar rutas sin reiniciar el gateway:

```bash
curl -X POST http://localhost:8080/actuator/gateway/refresh
```

---

## Seguridad

### Recomendaciones para Producción

1. **HTTPS Obligatorio:**
```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_PASSWORD}
```

2. **Autenticación/Autorización:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

3. **Rate Limiting:**
```properties
spring.cloud.gateway.routes[0].filters[1]=RequestRateLimiter=10,1s
```

4. **Ocultar Información Sensible:**
```properties
management.endpoints.web.exposure.include=health,info
```

5. **Headers de Seguridad:**
```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http
        .headers(headers -> headers
            .frameOptions().disable()
            .contentSecurityPolicy("default-src 'self'"))
        .build();
}
```

---

## Solución de Problemas

### Problema: 404 al acceder a rutas

**Causa:** Servicio no registrado en Eureka o ruta incorrecta

**Solución:**
1. Verificar que el servicio esté UP en Eureka: `http://localhost:8761`
2. Revisar rutas configuradas: `http://localhost:8080/actuator/gateway/routes`

### Problema: CORS errors en el browser

**Causa:** Origen no permitido en CorsConfig

**Solución:**
Agregar el origen en [`CorsConfig.java`](src/main/java/com/metradingplat/gateway/config/CorsConfig.java:1):
```java
corsConfig.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",  // Agregar origen
    // ...
));
```

### Problema: Timeout en peticiones

**Causa:** Timeout muy corto

**Solución:**
```properties
spring.cloud.gateway.httpclient.connect-timeout=5000
spring.cloud.gateway.httpclient.response-timeout=30s
```

---

## Configuración en Producción

### Variables de Entorno

```bash
export SERVER_PORT=8080
export EUREKA_URL=http://eureka-prod:8761/eureka/
export SPRING_PROFILES_ACTIVE=prod
```

### Docker

```dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
version: '3.8'
services:
  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - EUREKA_URL=http://eureka:8761/eureka/
    depends_on:
      - eureka
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

---

## Mejores Prácticas

1. **Centralizar CORS**: Configurar CORS solo en el Gateway
2. **Usar lb://**: Siempre usar balanceo de carga con `lb://service-name`
3. **Monitoring**: Integrar con Prometheus/Grafana
4. **Circuit Breaker**: Implementar para resiliencia
5. **Rate Limiting**: Proteger contra DDoS
6. **Logging**: Centralizar logs con ELK Stack

---

## Recursos Adicionales

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Gateway Patterns](https://microservices.io/patterns/apigateway.html)

---

## Licencia

Copyright (c) 2025 MeTradingPlat. Todos los derechos reservados.

---

## Contacto

Para soporte o consultas: [contacto@metradingplat.com](mailto:contacto@metradingplat.com)

---

## Changelog

### v0.0.1-SNAPSHOT (Actual)
- Implementación inicial de API Gateway
- Integración con Eureka para Service Discovery
- CORS configurado globalmente
- Virtual Threads (Java 21)
- Descubrimiento automático de rutas
- Actuator endpoints habilitados
