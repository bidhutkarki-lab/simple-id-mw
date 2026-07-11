package com.example.simpleidmw.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Establishes the gateway as the sole source of caller identity for downstream
 * services:
 * <ul>
 *   <li>Removes any {@code X-User-Id} sent by the client so it cannot be spoofed.</li>
 *   <li>For an authenticated request, injects a trusted {@code X-User-Id} taken
 *       from the validated JWT and strips the {@code Authorization} header so the
 *       token is not forwarded.</li>
 * </ul>
 * Downstream services can therefore trust {@code X-User-Id} unconditionally.
 */
@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final String userIdClaim;

    public IdentityPropagationFilter(
            @Value("${gateway.identity.user-id-claim:sub}") String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Defense in depth: always drop a client-supplied identity header.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken())
                .map(jwt -> withTrustedIdentity(exchange, sanitized, jwt))
                .defaultIfEmpty(exchange.mutate().request(sanitized).build())
                .flatMap(chain::filter);
    }

    private ServerWebExchange withTrustedIdentity(
            ServerWebExchange exchange, ServerHttpRequest sanitized, Jwt jwt) {
        ServerHttpRequest request = sanitized.mutate()
                .headers(headers -> {
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    String userId = jwt.getClaimAsString(userIdClaim);
                    if (userId != null) {
                        headers.set(USER_ID_HEADER, userId);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        // Run before the routing filter so header changes reach the downstream call.
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
