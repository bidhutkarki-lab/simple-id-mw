package com.simpleid.simpleidmw.config;

import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Establishes the gateway as the sole source of caller identity for downstream
 * services:
 * <ul>
 *   <li>Removes any {@code X-User-Id} sent by the client so it cannot be spoofed.</li>
 *   <li>For an authenticated request, injects a trusted {@code X-User-Id} taken
 *       from the validated JWT and strips the {@code Authorization} header so the
 *       token is not forwarded.</li>
 *   <li>Strips the {@code access_token} query parameter (used to carry the JWT on
 *       WebSocket handshakes, where the browser cannot set a header) so the token
 *       never leaks into downstream URLs or logs.</li>
 * </ul>
 * Downstream services can therefore trust {@code X-User-Id} unconditionally.
 */
@Slf4j
@Component
public class IdentityPropagationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    private static final String ACCESS_TOKEN_PARAM = "access_token";

    private final String userIdClaim;

    public IdentityPropagationFilter(
            @Value("${gateway.identity.user-id-claim:sub}") String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest incoming = exchange.getRequest();
        log.info("Incoming request: {} {}", incoming.getMethod(), incoming.getURI().getRawPath());
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(auth -> sanitize(exchange, ((JwtAuthenticationToken) auth).getToken()))
                .switchIfEmpty(Mono.fromSupplier(() -> sanitize(exchange, null)))
                .flatMap(chain::filter);
    }

    /**
     * Returns an exchange whose request carries a rewritten, writable header set
     * and a URI with the {@code access_token} query parameter removed. The
     * incoming request's headers are read-only at runtime, so we copy them into a
     * fresh {@link HttpHeaders} and expose it via a request decorator.
     */
    private ServerWebExchange sanitize(ServerWebExchange exchange, Jwt jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(exchange.getRequest().getHeaders());

        // Cookies are noise for a bearer-token API gateway; strip them at the edge.
        headers.remove(HttpHeaders.COOKIE);

        // Never trust a client-supplied identity header.
        headers.remove(USER_ID_HEADER);

        if (jwt != null) {
            // Token is validated here and not forwarded downstream.
            headers.remove(HttpHeaders.AUTHORIZATION);
            String userId = jwt.getClaimAsString(userIdClaim);
            if (userId != null) {
                headers.set(USER_ID_HEADER, userId);
            }
        }

        URI strippedUri = stripAccessToken(exchange.getRequest().getURI());

        ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Override
            public URI getURI() {
                return strippedUri;
            }
        };
        return exchange.mutate().request(request).build();
    }

    /**
     * Removes the {@code access_token} query parameter (if present) so the JWT
     * carried on a WebSocket handshake is not forwarded downstream. Other query
     * parameters are preserved.
     */
    private URI stripAccessToken(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || !rawQuery.contains(ACCESS_TOKEN_PARAM)) {
            return uri;
        }
        return UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam(ACCESS_TOKEN_PARAM)
                .build(true)
                .toUri();
    }

    @Override
    public int getOrder() {
        // Run before RouteToRequestUrlFilter so the stripped URI (access_token
        // removed) is used to build the downstream URL. Header edits are read
        // live by the routing filter, so they still take effect at call time.
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1;
    }
}
