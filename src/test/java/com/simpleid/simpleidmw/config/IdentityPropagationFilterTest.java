package com.simpleid.simpleidmw.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class IdentityPropagationFilterTest {

    private final IdentityPropagationFilter filter = new IdentityPropagationFilter("sub");

    @Test
    void injectsTrustedUserIdAndStripsSensitiveHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .build();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header(IdentityPropagationFilter.USER_ID_HEADER, "spoofed")
                .build();

        HttpHeaders forwarded = runFilter(request, new JwtAuthenticationToken(jwt));

        assertThat(forwarded.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isEqualTo("user-123");
        assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void stripsClientSuppliedUserIdWhenUnauthenticated() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
                .header(IdentityPropagationFilter.USER_ID_HEADER, "spoofed")
                .build();

        HttpHeaders forwarded = runFilter(request, null);

        assertThat(forwarded.getFirst(IdentityPropagationFilter.USER_ID_HEADER)).isNull();
    }

    private HttpHeaders runFilter(MockServerHttpRequest request, JwtAuthenticationToken auth) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        Mono<Void> result = filter.filter(exchange, chain);
        if (auth != null) {
            result = result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        }
        result.block();

        return forwarded.get().getHeaders();
    }
}
