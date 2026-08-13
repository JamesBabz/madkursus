package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendAssetsTest {
    @Test
    void frontendContainsAuthAndCsrfIntegrationAndNeverCachesApi() throws Exception {
        String html = resource("static/index.html");
        String javascript = resource("static/js/app.js");
        String worker = resource("static/service-worker.js");
        assertThat(html).contains("login-form", "register-form", "logout");
        assertThat(html).contains("auth-success").doesNotContain("show-register", "show-login");
        assertThat(javascript).contains("${AUTH_API}/me", "/registration-status", "X-XSRF-TOKEN");
        assertThat(worker).contains("url.pathname.startsWith('/v1/')");
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
