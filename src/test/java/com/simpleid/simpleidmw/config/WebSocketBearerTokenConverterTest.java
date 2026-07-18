package com.simpleid.simpleidmw.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class WebSocketBearerTokenConverterTest {

    private final WebSocketBearerTokenConverter converter =
            new WebSocketBearerTokenConverter(new String[] {"/tic-tac-toe/ws/**"});

    @Test
    void readsTokenFromQueryParamOnWebSocketPath() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/tic-tac-toe/ws/info?access_token=ws-token").build();

        Authentication auth = convert(request);

        assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
        assertThat(((BearerTokenAuthenticationToken) auth).getToken()).isEqualTo("ws-token");
    }

    @Test
    void ignoresQueryParamTokenOnNonWebSocketPath() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/tic-tac-toe/games?access_token=ws-token").build();

        assertThat(convert(request)).isNull();
    }

    @Test
    void readsBearerHeaderOnNonWebSocketPath() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/tic-tac-toe/games")
                .header(HttpHeaders.AUTHORIZATION, "Bearer header-token")
                .build();

        Authentication auth = convert(request);

        assertThat(auth).isInstanceOf(BearerTokenAuthenticationToken.class);
        assertThat(((BearerTokenAuthenticationToken) auth).getToken()).isEqualTo("header-token");
    }

    private Authentication convert(MockServerHttpRequest request) {
        return converter.convert(MockServerWebExchange.from(request)).block();
    }
}
