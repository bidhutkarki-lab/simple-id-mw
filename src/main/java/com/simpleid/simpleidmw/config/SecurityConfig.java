package com.simpleid.simpleidmw.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;

/**
 * Every request must carry a valid JWT (verified against the configured JWKS
 * endpoint) before the gateway routes it downstream, except for health probes
 * and the configured public paths (e.g. login/registration).
 *
 * <p>The bearer token is normally read from the {@code Authorization} header. A
 * browser cannot set that header on a WebSocket/SockJS handshake, so on the
 * configured WebSocket path(s) the token is also accepted from the
 * {@code access_token} query parameter (see {@link WebSocketBearerTokenConverter}).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${gateway.security.public-paths:/tic-tac-toe/users/register,/auth/login}") String[] publicPaths,
            @Value("${gateway.security.websocket-paths:/tic-tac-toe/ws/**}") String[] webSocketPaths) {
        ServerAuthenticationConverter bearerConverter = new WebSocketBearerTokenConverter(webSocketPaths);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers(publicPaths).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(bearerConverter)
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
