package com.simpleid.simpleidmw.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/auth/.well-known/jwks.json",
                "gateway.security.public-paths=/auth/register,/auth/login",
                "gateway.security.websocket-paths=/tic-tac-toe/ws/**"
        })
class SecurityConfigIntegrationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void protectedPathWithoutTokenIsUnauthorized() {
        client.get().uri("/tic-tac-toe/games")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void publicPathBypassesJwtValidation() {
        // No auth-service is running, so the request passes security and then fails
        // at routing (5xx) rather than being rejected with 401 — proving it is public.
        client.post().uri("/auth/login")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void webSocketHandshakeWithoutTokenIsUnauthorized() {
        client.get().uri("/tic-tac-toe/ws/info")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
