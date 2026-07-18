package com.simpleid.simpleidmw.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts the bearer token from a request. A browser cannot set an
 * {@code Authorization} header on a WebSocket/SockJS handshake, so on the
 * configured WebSocket handshake path(s) the token is additionally accepted from
 * the {@code access_token} query parameter. Every other path keeps the stricter
 * header-only behaviour, so tokens are never accepted in URLs except where the
 * browser leaves no alternative.
 */
public class WebSocketBearerTokenConverter implements ServerAuthenticationConverter {

    private final ServerBearerTokenAuthenticationConverter headerConverter =
            new ServerBearerTokenAuthenticationConverter();
    private final ServerBearerTokenAuthenticationConverter queryConverter =
            new ServerBearerTokenAuthenticationConverter();
    private final ServerWebExchangeMatcher webSocketMatcher;

    public WebSocketBearerTokenConverter(String[] webSocketPaths) {
        this.queryConverter.setAllowUriQueryParameter(true);
        List<ServerWebExchangeMatcher> matchers = Arrays.stream(webSocketPaths)
                .map(path -> (ServerWebExchangeMatcher) new PathPatternParserServerWebExchangeMatcher(path))
                .toList();
        this.webSocketMatcher = new OrServerWebExchangeMatcher(matchers);
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return webSocketMatcher.matches(exchange)
                .flatMap(match -> match.isMatch()
                        ? queryConverter.convert(exchange)
                        : headerConverter.convert(exchange));
    }
}
