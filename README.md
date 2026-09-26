# 🌐 GMS Gateway Service

Single entry point for all client traffic in the **Global Medical System**. Routes requests
to the three backend microservices and centralizes JWT validation at the edge.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-4.x-green)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

## 🚀 Quick Start

```bash
mvn spring-boot:run
# Gateway listens on http://localhost:9090
```

Open http://localhost:9090 in a browser — the gateway proxies to the right backend automatically.

## 🔧 Tech Stack
- **Java 21**, **Spring Boot 3.3.0**
- **Spring Cloud Gateway** (reactive, Netty-based)
- **JWT validation** at the edge (uses the same `JWT_SECRET` as the other services)
- **Maven**

## 🔀 Routing (current)

| Path prefix        | Routed to             | Internal port |
|--------------------|-----------------------|---------------|
| `/api/files/**`    | file-system-service   | 8081          |
| `/api/biometric/**`| biometric-service     | 8082          |
| `/`                | backend-service       | 8080          |

> Clients only ever talk to port **9090**. The internal services' ports are not exposed
> outside the Docker network.

## 🧭 Why a gateway?

- **One URL for the frontend** — clients only need to remember `9090`, not `8080/8081/8082`.
- **Centralized auth** — JWT is verified once at the edge; downstream services trust the gateway.
- **Easier HTTPS / TLS termination** — terminate HTTPS once at the gateway, plain HTTP inside.
- **Rate limiting & CORS** — apply cross-cutting concerns in one place.
- **Service discovery ready** — when you later add Eureka/Consul, only the gateway needs to know.

## ⚙️ Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | — | HMAC secret for JWT validation |
| `FILE_SERVICE_URL` | `http://localhost:8081` | Where file-system-service runs |
| `BIOMETRIC_SERVICE_URL` | `http://localhost:8082` | Where biometric-service runs |
| `BACKEND_SERVICE_URL` | `http://localhost:8080` | Where backend-service runs |

## 🐳 Docker

```bash
docker build -t gms-gateway-service .
docker run -p 9090:9090 gms-gateway-service
```

When using `docker compose` (recommended), all four services come up together.

## 🧪 Build & Test

```bash
mvn clean package
mvn test
mvn spring-boot:run
```

## 📡 Quick smoke test

```bash
# Without auth (should be blocked on protected routes)
curl http://localhost:9090/api/files/ping

# With a JWT (replace TOKEN below)
curl -H "Authorization: Bearer TOKEN" http://localhost:9090/api/files/ping
```

---

Part of the GMS ecosystem. See the main project: [golamrabbanyshikder/global-medical-system](https://github.com/golamrabbanyshikder/global-medical-system)
