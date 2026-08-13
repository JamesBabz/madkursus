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
        assertThat(html).contains("template-search", "show-custom-product")
                .doesNotContain("onboarding-templates", "Kom hurtigt i gang");
        assertThat(javascript).contains("/v1/product-templates", "/v1/products/from-template/", "250")
                .doesNotContain("showOnboarding", "common=true", "onboardingSkipped");
        assertThat(html).contains("edit-product-dialog", "edit-product-form", "id=\"toast\"");
        assertThat(javascript).contains("showToast", "setTimeout", "2600", "searchRequestId",
                "templateSearch.value = ''", "method: 'PATCH'", "openProductEditor");
        assertThat(worker).contains("madkursus-shell-v8");
        assertThat(html).contains("inventory-view", "inventory-add-dialog", "edit-inventory-dialog");
        assertThat(javascript).contains("/v1/inventory", "searchInventoryCandidates", "from-template",
                "loadInventory", "showToast", "inventorySearchRequestId");
        assertThat(html).contains("inputmode=\"numeric\"", "step=\"1\"", "inventory-add-conversion",
                "edit-inventory-conversion");
        assertThat(html.indexOf("id=\"show-inventory\"")).isLessThan(html.indexOf("id=\"show-products\""));
        assertThat(javascript).contains("inventoryConversion", "Intl.NumberFormat('da-DK'", "value / 1000");
        assertThat(javascript).contains("showView('inventory')", "requestProductDeletion", "method: 'DELETE'",
                "Produktet findes stadig på lager");
        assertThat(html).contains("request-delete-product", "delete-product-confirmation", "+ Tilføj produkt")
                .doesNotContain("id=\"refresh-products\"");
        assertThat(html.indexOf("id=\"open-form\"")).isGreaterThan(html.indexOf("id=\"products-title\""));
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
