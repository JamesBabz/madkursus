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
        assertThat(html).contains("login-form", "register-form", "auth-tab-login", "auth-tab-register", "logout");
        assertThat(html).contains("auth-success").doesNotContain("show-register", "show-login");
        assertThat(javascript).contains("${AUTH_API}/me", "/registration-status", "X-XSRF-TOKEN", "showAuthMode");
        assertThat(worker).contains("url.pathname.startsWith('/v1/')");
        assertThat(html).contains("template-search", "show-custom-product")
                .doesNotContain("onboarding-templates", "Kom hurtigt i gang");
        assertThat(javascript).contains("/v1/product-templates", "/v1/products/from-template/", "250")
                .doesNotContain("showOnboarding", "common=true", "onboardingSkipped");
        assertThat(html).contains("edit-product-dialog", "edit-product-form", "id=\"toast\"");
        assertThat(javascript).contains("showToast", "setTimeout", "2600", "searchRequestId",
                "templateSearch.value = ''", "method: 'PATCH'", "openProductEditor");
        assertThat(worker).contains("madkursus-shell-v26");
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
        assertThat(html).contains("shopping-view", "shopping-add-dialog", "edit-shopping-dialog",
                "shopping-active-list", "shopping-purchased-list", "toast-action");
        assertThat(javascript).contains("/v1/shopping-list", "purchaseShoppingItem", "undo-purchase",
                "attachShoppingGestures", "pointerdown", "pointermove", "600", "clear-purchased", "undoShoppingItem",
                "searchShoppingCandidates", "shoppingSearchRequestId");
        assertThat(worker).contains("request.method !== 'GET'", "url.pathname.startsWith('/v1/')");
        assertThat(html).contains("edit-product-tracking-mode", "Præcis mængde", "Kun om jeg har varen",
                "edit-inventory-presence", "edit-shopping-presence");
        assertThat(javascript).contains("inventoryTrackingMode", "PRESENCE", "På lager", "Køb",
                "input.step = unit === 'PIECE' ? '0.5' : '1'", "formatQuantity", "da-DK");
        assertThat(javascript).contains("input.min = allowZero ? '0' : (unit === 'PIECE' ? '0.5' : '1')",
                "candidate.defaultTrackingMode", "candidate.inventoryTrackingMode",
                "inventory-add-quantity-controls", "shopping-add-quantity-controls",
                "quantity == null ? {} : { quantity }");
        assertThat(html).contains("id=\"inventory-add-quantity\"", "id=\"shopping-add-quantity\"")
                .doesNotContain("id=\"inventory-add-quantity\" name=\"quantity\" type=\"number\" inputmode=\"numeric\" min=\"0.5\"",
                        "id=\"shopping-add-quantity\" type=\"number\" inputmode=\"numeric\" min=\"0.5\"");
        assertThat(html).contains("show-recipes", "recipes-view", "recipe-editor-dialog", "recipe-detail-dialog",
                "recipe-template-search", "recipe-portions");
        assertThat(html.indexOf("id=\"show-recipes\"")).isLessThan(html.indexOf("id=\"show-products\""));
        assertThat(javascript).contains("/v1/recipes", "searchRecipeTemplates", "scaledDecimal", "recipePortions = 2",
                "productTemplateId", "method:editingRecipeId?'PATCH':'POST'");
        assertThat(javascript).contains("danishDecimal(scaledDecimal(ingredient.quantity, recipePortions))");
        assertThat(html).contains("add-process-step", "cooking-process-select", "cooking-process-parameters");
        assertThat(javascript).contains("/v1/cooking-processes", "openProcessPicker", "type:'PROCESS'", "renderedProcess");
        assertThat(javascript).contains("durationMinutes", "durationSeconds", "minutter", "sekunder",
                "recipeIngredientId", "allocatedForIngredient", "Ingen ingredienser er tilføjet til opskriften endnu.");
        assertThat(html).contains("plan-recipes", "recipe-plan-dialog", "calculate-recipe-plan", "add-recipe-missing", "cook-recipe");
        assertThat(javascript).contains("calculate-requirements", "add-missing-to-shopping-list", "/cook", "recipePlanSelections",
                "button.disabled=true", "trackingMode==='PRESENCE'", "r.warning");
        assertThat(html).contains("recipe-plan-requirements", "Samlet behov").doesNotContain("id=\"recipe-plan-available\"", "id=\"recipe-plan-missing\"");
        assertThat(javascript).contains("renderRecipePlanPreview", "scaledDecimal(ingredient.quantity,portions)",
                "Behov", "På lager", "Mangler:", "Du har nok", "✓ På lager");
        assertThat(html).contains("show-meal-plans", "meal-plans-panel", "request-save-meal-plan", "meal-plan-detail-dialog",
                "meal-plan-requirements", "meal-plan-add-missing");
        assertThat(javascript).contains("/v1/meal-plans", "saveCurrentMealPlan", "loadMealPlans", "openMealPlan",
                "changePlannedPortions", "cookPlanned", "togglePlannedSkip", "Færdig ✓");
        assertThat(html).contains("show-recipe-templates", "recipe-templates-panel", "recipe-template-detail-dialog",
                "Føj til mine opskrifter");
        assertThat(javascript).contains("/v1/recipe-templates", "loadRecipeTemplates", "recipeTemplatePortions=2",
                "add-to-my-recipes", "userRecipeId");
        assertThat(html).contains("show-kitchen", "kitchen-view", "kitchen-equipment-dialog", "Mit køkken",
                "data-equipment-fields=\"STOVE\"", "data-equipment-fields=\"OVEN\"");
        assertThat(javascript).contains("/v1/kitchen-equipment", "loadKitchenEquipment", "openKitchenEquipment",
                "saveKitchenEquipment", "deleteKitchenEquipment", "liters", "centimeters", "userRecipeId");
        assertThat(html).contains("data-heat=\"LOW\"", "data-heat=\"MEDIUM_LOW\"", "data-heat=\"MEDIUM_HIGH\"");
        assertThat(javascript).contains("suggestedHeatMappings");
        assertThat(html).contains("inventory-reservation-dialog", "Planlagt til");
        assertThat(javascript).contains("reservedQuantity", "physicalQuantity", "availableQuantity",
                "plannedShortfall", "openInventoryReservations", "Planlagt til andre retter");
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
