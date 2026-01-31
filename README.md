# Gateway

API Gateway de la plataforma **MeTradingPlat**. Punto de entrada unico para todos los microservicios, basado en Spring Cloud Gateway con descubrimiento de servicios via Eureka.

## Tabla de Contenido

- [Arquitectura](#arquitectura)
- [Tecnologias](#tecnologias)
- [Enrutamiento](#enrutamiento)
- [CORS](#cors)
- [Configuracion](#configuracion)
- [Ejecucion](#ejecucion)
- [CI/CD](#cicd)

## Arquitectura

```mermaid
graph LR
    subgraph "Clientes"
        WEB["Frontend Angular<br/>metradingplat.com"]
    end

    subgraph "Gateway :8080"
        CORS["CORS Filter"]
        ROUTER["Router +<br/>Load Balancer"]
        HDR["AddRequestHeader<br/>X-Gateway-Passed"]
    end

    subgraph "Service Discovery"
        EUREKA["Eureka Server<br/>:8761"]
    end

    subgraph "Microservicios"
        SMS["scanner-management-service<br/>:8081"]
        MDS["marketdata-service<br/>:8082"]
    end

    WEB -->|HTTPS| CORS --> ROUTER
    ROUTER -->|/api/escaner/**| HDR --> SMS
    ROUTER -->|/api/marketdata/**| HDR --> MDS
    ROUTER -.->|descubre servicios| EUREKA
    SMS & MDS -.->|se registran| EUREKA
```

### Flujo de una peticion

```mermaid
sequenceDiagram
    participant C as Cliente
    participant GW as Gateway :8080
    participant E as Eureka
    participant S as Microservicio

    C->>GW: GET /api/marketdata/historical/AAPL?timeframe=M5
    GW->>GW: CORS Filter (valida origen)
    GW->>GW: Path predicate match → marketdata-service
    GW->>E: Resolver instancias de marketdata-service
    E-->>GW: [172.18.0.10:8082]
    GW->>GW: Add header X-Gateway-Passed=true
    GW->>S: GET /api/marketdata/historical/AAPL?timeframe=M5
    S-->>GW: 200 OK [candles]
    GW-->>C: 200 OK [candles]
```

## Tecnologias

| Tecnologia | Version | Proposito |
|---|---|---|
| Java | 21 | Runtime (Virtual Threads) |
| Spring Boot | 3.5.9 | Framework |
| Spring Cloud Gateway | 2025.0.0 | Enrutamiento reactivo (WebFlux/Netty) |
| Spring Cloud LoadBalancer | - | Balanceo de carga client-side |
| Eureka Client | - | Descubrimiento de servicios |
| Docker | Multi-stage | Contenedorizacion |

## Enrutamiento

### Rutas Estaticas

| ID | Path | Servicio destino | Filtros |
|---|---|---|---|
| `scanner-management-service` | `/api/escaner/**` | `lb://scanner-management-service` | `AddRequestHeader=X-Gateway-Passed,true` |
| `marketdata-service` | `/api/marketdata/**`, `/api/test/**` | `lb://marketdata-service` | `AddRequestHeader=X-Gateway-Passed,true` |

- `lb://` indica balanceo de carga via Eureka
- El header `X-Gateway-Passed: true` permite a los microservicios verificar que la peticion paso por el gateway

### Auto-Discovery

```yaml
discovery:
  locator:
    enabled: true
    lower-case-service-id: true
```

Cualquier servicio registrado en Eureka es accesible automaticamente via `/{service-name}/**`.

## CORS

Configuracion centralizada en `CorsConfig.java`:

| Propiedad | Valor |
|---|---|
| **Origenes permitidos** | `https://metradingplat.com`, `https://www.metradingplat.com` |
| **Metodos HTTP** | GET, POST, PUT, DELETE, OPTIONS, PATCH |
| **Headers permitidos** | Todos (`*`) |
| **Headers expuestos** | Authorization, Content-Type |
| **Credenciales** | Habilitadas |
| **Cache preflight** | 1 hora (3600s) |

## Configuracion

### Variables de Entorno

| Variable | Descripcion | Default |
|---|---|---|
| `EUREKA_HOST` | Host del servidor Eureka | `directory` |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `prod` |

### Perfiles

- **dev**: Eureka en `localhost:8761`
- **prod**: Eureka via `${EUREKA_HOST}:8761`, forward-headers-strategy: native

### Actuator

Endpoints expuestos: `/actuator/health`, `/actuator/info`

## Ejecucion

### Con Docker Compose

```bash
# Desde la raiz de metradingplat/
docker compose up -d gateway
```

Disponible en `http://localhost:8080`.

### Desarrollo Local

```bash
cd gateway

# Requiere Java 21, Maven, Eureka corriendo en localhost:8761
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## CI/CD

Pipeline automatizado con GitHub Actions:

### CI (push a master)

1. Checkout + JDK 21
2. Build con Maven (`mvn clean package -DskipTests`)
3. Build imagen Docker: `virtud142/metradingplat_gateway:latest`
4. Push a DockerHub

### CD (tras CI exitoso)

1. Pull imagen en servidor self-hosted
2. Stop/remove contenedor anterior
3. Run nuevo contenedor en red `metradingplat-network`
4. Verificacion de salud

```
Docker Registry: virtud142/metradingplat_gateway:latest
```
