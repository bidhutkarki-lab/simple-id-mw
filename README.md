# simple-id-mw

Identity middleware and API gateway. It sits in front of the microservices,
validates the JWT on every incoming request, and routes valid requests to the
right downstream service.

- **Framework:** Spring Cloud Gateway (reactive / Netty)
- **AuthN:** Spring Security OAuth2 Resource Server, JWTs verified against a JWKS endpoint
- **Routing:** static path-prefix → service mappings in `application.yml`
- **Java:** 21 · **Build:** Maven

## How it works

1. A request hits the gateway (default port `8080`).
2. The security filter chain (`config/SecurityConfig.java`) requires a valid
   `Authorization: Bearer <jwt>` on every request except health probes and the
   configured **public paths** (e.g. login/registration). Signatures are checked
   against keys fetched from the configured JWKS endpoint. Requests without a
   valid token get `401`.
3. Authenticated requests are matched against the routes in `application.yml`
   and forwarded to the corresponding service. Before forwarding,
   `IdentityPropagationFilter` strips the `Authorization` header and any
   client-supplied `X-User-Id`, then injects a **trusted** `X-User-Id` taken
   from the JWT. Cookies are stripped at the edge.
4. `GET /actuator/health` is open (no token) for liveness/readiness probes.

## WebSocket / SockJS

The gateway proxies WebSocket (and SockJS fallback) traffic through the same
path-prefix routes — Spring Cloud Gateway upgrades an `http://` route target to
`ws://` automatically on a handshake request. The tic-tac-toe STOMP-over-SockJS
endpoint at `/tic-tac-toe/ws` is reached through the gateway with no extra route.

A browser **cannot** set an `Authorization` header on a WebSocket/SockJS
handshake, so on the configured **WebSocket paths** (`WEBSOCKET_PATHS`, default
`/tic-tac-toe/ws/**`) the JWT is also accepted from the `access_token` query
parameter. These paths are still fully authenticated — an invalid or missing
token gets `401`. Every other route keeps the stricter header-only behaviour, so
tokens are never accepted in URLs except where the browser leaves no
alternative. The `access_token` query parameter is stripped before the handshake
is forwarded, so the token never reaches downstream URLs or logs; downstream
services see only the trusted `X-User-Id`.

The frontend points SockJS at the gateway and appends the token:

```ts
const WS_URL = import.meta.env.VITE_WS_URL ?? "http://localhost:8080/tic-tac-toe/ws";
// ...
webSocketFactory: () => new SockJS(`${WS_URL}?access_token=${encodeURIComponent(token)}`),
```

## Configuration

All values have placeholder defaults — override via environment variables.

| Variable                | Purpose                                | Default                                              |
| ----------------------- | -------------------------------------- | ---------------------------------------------------- |
| `JWKS_URI`              | JWKS endpoint used to verify JWTs      | `http://localhost:8081/auth/.well-known/jwks.json`   |
| `AUTH_SERVICE_URI`      | Target for `/auth/**`                  | `http://localhost:8081`                              |
| `TIC_TAC_TOE_SERVICE_URI` | Target for `/tic-tac-toe/**`         | `http://localhost:8082`                              |
| `PUBLIC_PATHS`          | Paths that bypass JWT validation       | `/auth/register,/auth/login`                         |
| `WEBSOCKET_PATHS`       | Handshake paths that also accept the JWT via `access_token` query param | `/tic-tac-toe/ws/**`            |
| `USER_ID_CLAIM`         | JWT claim forwarded as `X-User-Id`     | `sub`                                                |

### Adding a route

Add a block under `spring.cloud.gateway.routes` in
`src/main/resources/application.yml`:

```yaml
- id: payment-service
  uri: ${PAYMENT_SERVICE_URI:http://localhost:8083}
  predicates:
    - Path=/api/payments/**
```

## Run

```bash
JWKS_URI=https://your-auth-server/.well-known/jwks.json ./mvnw spring-boot:run
```

## Test

```bash
./mvnw test
```

## Notes

- Downstream services receive a trusted `X-User-Id` header and no longer see the
  JWT — the gateway is the single point of token validation. Change which claim
  populates the header via `USER_ID_CLAIM` (default `sub`).
- Because downstream trust `X-User-Id`, those services must **only** be reachable
  through the gateway (network isolation), otherwise a caller could set the header
  directly.
