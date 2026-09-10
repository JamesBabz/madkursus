package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendAssetsTest {
    @Test
    void frontendContainsAuthAndCsrfIntegrationAndNeverCachesApi() throws Exception {
        String html = resource("static/index.html");
        String javascript = resource("static/js/app.js");
        assertThat(resource("static/js/locales/da.js")).contains("g pr. portion", "ukendt bidrag", "Vis fordeling", "Vis ukendte");
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
        assertThat(html).contains("recipe-carbohydrates", "recipe-template-carbohydrates");
        assertThat(javascript).contains("renderCarbohydrates", "nutrition.carbohydrates.perPortion", "nutrition.carbohydrates.unknownContribution", "nutrition.carbohydrates.showBreakdown");
        assertThat(html).contains("show-nutrition", "more-nutrition", "nutrition-admin-view", "nutrition-edit-form", "nutrition.title");
        assertThat(javascript).contains("renderAdminNavigation", "currentUser?.admin === true", "currentUser = await jsonRequest(`${AUTH_API}/login`", "renderUnknownCarbohydrates", "nutrition.showUnknown", "nutrition.missingCarbohydrates", "NUTRITION_ADMIN_API");
        assertThat(html).contains("nutrition-list-header", "nutrition-admin-list", "nutrition-dtu-status", "nutrition-match-dialog");
        assertThat(html).contains("nutrition.dtu.readyForApproval", "nutrition.dtu.noMatch", "nutrition.dtu.safeMatches", "nutrition.dtu.approveSafe", "select-all-nutrition", "nutrition-bulk-confirm-dialog");
        assertThat(html).contains("nutrition.dtu.suggestions", "nutrition.dtu.searchCatalog", "nutrition.dtu.suggestionsHint");
        assertThat(javascript).contains("nutrition-product-name", "nutrition-cell", "nutrition-row-actions", "approveSelectedDtu", "approveSafeDtu", "AUTO_EQUIVALENT_CARBOHYDRATE", "requestApproveSelectedDtu", "nutrition.dtu.search", "nutrition.openData");
        assertThat(worker).contains("madkursus-shell-v52", "/css/app.css?v=52", "/js/dialog-viewport.js?v=52", "/js/app.js?v=52");
        assertThat(html).contains("/css/app.css?v=52", "/js/dialog-viewport.js?v=52", "/js/app.js?v=52");
        assertThat(html).contains("inventory-view", "inventory-add-dialog", "edit-inventory-dialog");
        assertThat(javascript).contains("/v1/inventory", "searchInventoryCandidates", "from-template",
                "loadInventory", "showToast", "inventorySearchRequestId");
        assertThat(html).contains("inputmode=\"numeric\"", "step=\"1\"", "inventory-add-conversion",
                "edit-inventory-conversion");
        assertThat(html.indexOf("id=\"show-inventory\"")).isLessThan(html.indexOf("id=\"show-products\""));
        assertThat(javascript).contains("inventoryConversion", "Intl.NumberFormat('da-DK'", "value / 1000");
        assertThat(javascript).contains("showView('inventory')", "requestProductDeletion", "method: 'DELETE'",
                "products.stillInInventory");
        assertThat(html).contains("request-delete-product", "delete-product-confirmation", "products.addShortcut")
                .doesNotContain("id=\"refresh-products\"");
        assertThat(html.indexOf("id=\"open-form\"")).isGreaterThan(html.indexOf("id=\"products-title\""));
        assertThat(html).contains("shopping-view", "shopping-add-dialog", "edit-shopping-dialog",
                "shopping-active-list", "shopping-purchased-list", "toast-action");
        assertThat(javascript).contains("/v1/shopping-list", "purchaseShoppingItem", "undo-purchase",
                "attachShoppingGestures", "pointerdown", "pointermove", "600", "clear-purchased", "undoShoppingItem",
                "searchShoppingCandidates", "shoppingSearchRequestId");
        assertThat(worker).contains("request.method !== 'GET'", "url.pathname.startsWith('/v1/')");
        assertThat(html).contains("edit-product-tracking-mode", "inventory.tracking.quantity", "inventory.tracking.presence",
                "edit-inventory-presence", "edit-shopping-presence");
        assertThat(javascript).contains("inventoryTrackingMode", "PRESENCE", "inventory.onHand", "shoppingList.purchase",
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
                "recipes.process.advancedSettings", "INGREDIENT_LIST", "p.source||'INPUT'", "renderProcessDetails", "process-details", "durationSummary", "recipePreparedComponents", "preparedComponentId", "inputSummary",
                "productTemplateId", "method:editingRecipeId?'PATCH':'POST'");
        assertThat(javascript).contains("const quantity=ingredient.quantity", "recipeUnitLabel(ingredient.unit,quantity)");
        assertThat(html).contains("add-process-step", "cooking-process-select", "cooking-process-parameters");
        assertThat(javascript).contains("/v1/cooking-processes", "openProcessPicker", "type:'PROCESS'", "renderedProcess");
        assertThat(javascript).contains("durationMinutes", "durationSeconds", "recipes.process.minutes", "recipes.process.seconds",
                "recipeIngredientId", "allocatedForIngredient", "recipes.process.validation.ingredientsRequired");
        assertThat(html).contains("plan-recipes", "recipe-plan-dialog", "calculate-recipe-plan", "add-recipe-missing", "cook-recipe");
        assertThat(javascript).contains("calculate-requirements", "add-missing-to-shopping-list", "/cook", "recipePlanSelections",
                "button.disabled=true", "trackingMode==='PRESENCE'", "r.warning");
        assertThat(html).contains("recipe-plan-requirements", "mealPlan.totalRequirements").doesNotContain("id=\"recipe-plan-available\"", "id=\"recipe-plan-missing\"");
        assertThat(javascript).contains("renderRecipePlanPreview", "scaledDecimal(ingredient.quantity,portions)",
                "inventory.required", "inventory.owned", "inventory.onHand", "inventory.reserved", "inventory.available", "inventory.missingAmount", "inventory.sufficient");
        assertThat(html).contains("show-meal-plans", "meal-plans-panel", "request-save-meal-plan", "meal-plan-detail-dialog",
                "meal-plan-requirements", "meal-plan-add-missing");
        assertThat(javascript).contains("/v1/meal-plans", "saveCurrentMealPlan", "loadMealPlans", "openMealPlan",
                "changePlannedPortions", "cookPlanned", "togglePlannedSkip", "mealPlan.completedSummary");
        assertThat(html).contains("show-recipe-templates", "recipe-templates-panel", "recipe-template-detail-dialog",
                "recipes.catalog.addToLibrary");
        assertThat(javascript).contains("/v1/recipe-templates", "loadRecipeTemplates", "initialPortions=2",
                "add-to-my-recipes", "userRecipeId");
        assertThat(html).contains("show-kitchen", "kitchen-view", "kitchen-equipment-dialog", "equipment.title",
                "data-equipment-fields=\"STOVE\"", "data-equipment-fields=\"OVEN\"");
        assertThat(javascript).contains("/v1/kitchen-equipment", "loadKitchenEquipment", "openKitchenEquipment",
                "saveKitchenEquipment", "deleteKitchenEquipment", "liters", "centimeters", "userRecipeId");
        assertThat(html).contains("data-heat=\"LOW\"", "data-heat=\"MEDIUM_LOW\"", "data-heat=\"MEDIUM_HIGH\"");
        assertThat(javascript).contains("suggestedHeatMappings");
        assertThat(html).contains("inventory-reservation-dialog", "inventory.reservationsTitle");
        assertThat(javascript).contains("reservedQuantity", "physicalQuantity", "availableQuantity",
                "plannedShortfall", "openInventoryReservations");
    }

    @Test
    void sharedDialogsUseTheVisibleViewportAndTheirOwnScrollContainer() throws Exception {
        String html = resource("static/index.html");
        String css = resource("static/css/app.css");
        String javascript = resource("static/js/dialog-viewport.js");

        assertThat(html).contains("interactive-widget=resizes-content");
        assertThat(css).contains(
                "--dialog-viewport-height: 100dvh",
                "overflow-y: auto",
                "overscroll-behavior: contain",
                "scroll-padding-block",
                "html:has(dialog[open]), body:has(dialog[open])",
                ".edit-dialog > form > .dialog-actions:last-child",
                "var(--dialog-viewport-bottom)");
        assertThat(javascript).contains(
                "function updateDialogViewport()",
                "window.visualViewport",
                "--dialog-viewport-height",
                "--dialog-viewport-bottom",
                "scrollIntoView({ block: 'nearest'",
                "controlBounds.bottom > visibleBottom");
    }

    @Test
    void chatIsWiredIntoNavigationAndPwaWithSafeTextRendering() throws Exception {
        String html = resource("static/index.html");
        String app = resource("static/js/app.js");
        String chat = resource("static/js/ai-chat.js");
        assertThat(html).contains("chat-launcher", "chat-drawer", "chat-minimize", "data-i18n-aria-label=\"chat.open\"", "aria-haspopup=\"dialog\"",
                "show-ai", "more-ai", "ai-view", "chat.assistantName", "chat-input", "chat-send",
                "data-chat-prompt", "aria-live=\"polite\"", "maxlength=\"4000\"");
        assertThat(html.indexOf("/js/ai-chat.js?v=52")).isLessThan(html.indexOf("/js/app.js?v=52"));
        assertThat(app).contains("createAiChat(document.querySelector('#chat-component'), jsonRequest,",
                "aiChat.reset()", "showView('ai')");
        assertThat(chat).contains("'/v1/ai/chat'", "JSON.stringify({ message,", "maxAdditionalIngredients", "content.textContent = text")
                .doesNotContain("innerHTML", "localStorage", "sessionStorage", "11434", "llama3.1");
        assertThat(html).contains("aria-modal=\"false\"");
        assertThat(chat).doesNotContain("showModal");
        assertThat(resource("static/service-worker.js")).contains("/js/ai-chat.js?v=52");
        assertThat(resource("static/css/app.css")).contains("white-space: pre-wrap", ".chat-message-user", ".chat-message-assistant");
    }

    private String resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
