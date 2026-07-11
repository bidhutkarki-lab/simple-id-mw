package com.example.simpleidmw;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json")
class SimpleIdMwApplicationTests {

    @Test
    void contextLoads() {
    }
}
