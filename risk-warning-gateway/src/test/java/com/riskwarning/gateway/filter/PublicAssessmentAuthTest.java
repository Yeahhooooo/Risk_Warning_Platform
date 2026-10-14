package com.riskwarning.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PublicAssessmentAuthTest {
    @Test
    void anonymousAssessmentReadPasses() {
        assertAccess(HttpMethod.GET, "/api/org/enterprise/测试企业/assessment", true);
    }

    @Test
    void otherOperationsStillRequireAuthentication() {
        assertAccess(HttpMethod.POST, "/api/org/enterprise/example/assessment", false);
        assertAccess(HttpMethod.GET, "/api/org/enterprise/1/members", false);
        assertAccess(HttpMethod.GET, "/api/report/enterprise/example/assessment", false);
        assertAccess(HttpMethod.GET, "/api/org/enterprise/example/assessment/extra", false);
    }

    private void assertAccess(HttpMethod method, String path, boolean allowed) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        AtomicBoolean forwarded = new AtomicBoolean();
        new AuthFilter().filter(exchange, request -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();
        assertEquals(allowed, forwarded.get());
        if (!allowed) assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
