package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShoppingListTextServiceTest {
    ProductPort productPort = mock(ProductPort.class);
    ProductTemplatePort templatePort = mock(ProductTemplatePort.class);
    ShoppingListPort shoppingPort = mock(ShoppingListPort.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    Map<UUID, Product> owned = new LinkedHashMap<>();
    Map<UUID, ShoppingListItem> items = new LinkedHashMap<>();
    List<ProductTemplate> catalog = new ArrayList<>();
    UUID user = UUID.randomUUID();
    ShoppingListTextService service;
    ProductService products;
    ShoppingListService shopping;

    @BeforeEach void setup() {
        when(currentUser.currentUserId()).thenReturn(user);
        when(productPort.findAllByUserId(user)).thenAnswer(c -> List.copyOf(owned.values()));
        when(productPort.findByIdAndUserId(any(), eq(user))).thenAnswer(c -> Optional.ofNullable(owned.get(c.getArgument(0))));
        when(productPort.findByUserIdAndSourceTemplateId(eq(user), any())).thenAnswer(c -> owned.values().stream().filter(p -> Objects.equals(p.sourceTemplateId(), c.getArgument(1))).findFirst());
        when(productPort.findByUserIdAndNormalizedName(eq(user), anyString())).thenAnswer(c -> owned.values().stream().filter(p -> p.name().equalsIgnoreCase(c.getArgument(1))).findFirst());
        when(productPort.save(any())).thenAnswer(c -> {
            Product p = c.getArgument(0);
            Product saved = new Product(p.id() == null ? UUID.randomUUID() : p.id(), p.userId(), p.sourceTemplateId(), p.name(), p.category(), p.defaultUnit(), p.inventoryTrackingMode());
            owned.put(saved.id(), saved); return saved;
        });
        when(templatePort.search("", null)).thenAnswer(c -> List.copyOf(catalog));
        when(templatePort.findByNameOrAlias(anyString())).thenAnswer(c -> catalog.stream().filter(t -> t.name().equalsIgnoreCase(c.getArgument(0)) || t.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(c.getArgument(0)))).toList());
        when(shoppingPort.findActiveByProductIdAndUserId(any(), eq(user))).thenAnswer(c -> Optional.ofNullable(items.get(c.getArgument(0))));
        when(shoppingPort.save(any())).thenAnswer(c -> {ShoppingListItem item = c.getArgument(0); items.put(item.product().id(), item); return item;});
        products = new ProductService(productPort, currentUser, mock(InventoryPort.class));
        var templates = new ProductTemplateService(templatePort, products);
        shopping = new ShoppingListService(shoppingPort, products, templates, mock(InventoryService.class), currentUser);
        service = new ShoppingListTextService(products, templates, shopping);
    }
    Product product(String name, Unit unit, InventoryTrackingMode mode) {
        Product p = new Product(UUID.randomUUID(), user, null, name, ProductCategory.OTHER, unit, mode); owned.put(p.id(),p); return p;
    }
    ProductTemplate template(String name, Unit unit, InventoryTrackingMode mode, String... aliases) {
        ProductTemplate t = new ProductTemplate(UUID.randomUUID(), name, ProductCategory.OTHER, unit, mode, List.of(aliases), false);catalog.add(t);return t;
    }
    @Test void bulletsColonUnitsAndSafeDefaultsAreParsedWithoutMutation() {
        product("Æg", Unit.PIECE, InventoryTrackingMode.QUANTITY);
        template("Kyllingebryst", Unit.GRAM, InventoryTrackingMode.QUANTITY);
        var result = service.preview("- Kyllingebryst: 400 g\n• Æg 10 stk.\nÆg 3\nKyllingebryst 200\n\n");
        assertThat(result.valid()).isTrue(); assertThat(result.imported()).isFalse();
        assertThat(result.items()).extracting(ShoppingListTextService.Line::quantity).containsExactly(new BigDecimal("400"),new BigDecimal("10"),new BigDecimal("3"),new BigDecimal("200"));
        assertThat(result.items()).extracting(ShoppingListTextService.Line::unit).containsExactly(Unit.GRAM,Unit.PIECE,Unit.PIECE,Unit.GRAM);
        verify(productPort, never()).save(any());verify(shoppingPort, never()).save(any());
    }
    @Test void importsAddToExistingQuantities() {
        Product chicken = product("Kyllingebryst",Unit.GRAM,InventoryTrackingMode.QUANTITY), eggs = product("Æg",Unit.PIECE,InventoryTrackingMode.QUANTITY);
        shopping.add(chicken.id(),new BigDecimal("200"));shopping.add(eggs.id(),new BigDecimal("2"));
        assertThat(service.importText("Kyllingebryst 400 gram\nÆg: 3 styk").imported()).isTrue();
        assertThat(items.get(chicken.id()).quantity()).isEqualByComparingTo("600");
        assertThat(items.get(eggs.id()).quantity()).isEqualByComparingTo("5");
    }
    @Test void createsUnknownUserProductsAndReusesThemOnLaterImports() {
        String text = "Cola\nAfrensningsmiddel\nKølervæske\nHeinz baked beans 2 stk\nSaft 250 ml";
        assertThat(service.importText(text).imported()).isTrue();
        assertThat(owned).hasSize(5); assertThat(items).hasSize(5);
        assertThat(owned.values()).allMatch(p -> p.sourceTemplateId() == null);
        Product cola = owned.values().stream().filter(p -> p.name().equals("Cola")).findFirst().orElseThrow();
        assertThat(cola.inventoryTrackingMode()).isEqualTo(InventoryTrackingMode.PRESENCE);
        assertThat(items.get(cola.id()).quantity()).isNull();
        assertThat(service.importText(" cola \n HEINZ   baked   BEANS 3 stk.").imported()).isTrue();
        assertThat(owned).hasSize(5); assertThat(items).hasSize(5);
        assertThat(items.values().stream().filter(i -> i.product().name().equals("Heinz baked beans")).findFirst().orElseThrow().quantity()).isEqualByComparingTo("5");
    }
    @Test void duplicatePresenceAndWhitespaceReuseOneProductWithinImport() {
        assertThat(service.importText("  Nyt   middel \n• NYT middel\nNyt middel").imported()).isTrue();
        assertThat(owned).hasSize(1); assertThat(items).hasSize(1);
    }
    @Test void knownPresenceAndAliasesMaterializeTemplatesWithoutDuplicates() {
        ProductTemplate curry = template("Karry",Unit.GRAM,InventoryTrackingMode.PRESENCE,"currypulver");
        assertThat(service.importText("currypulver\nKarry").imported()).isTrue();
        assertThat(owned).hasSize(1);assertThat(items).hasSize(1);
        assertThat(owned.values().iterator().next().sourceTemplateId()).isEqualTo(curry.id());
    }
    @Test void existingUserProductWinsOverCatalogAndTrivialWhitespace() {
        Product p = product("Heinz   baked beans",Unit.PIECE,InventoryTrackingMode.QUANTITY);
        template("Heinz baked beans",Unit.GRAM,InventoryTrackingMode.QUANTITY);
        assertThat(service.importText("heinz baked beans 2").imported()).isTrue();
        assertThat(items.get(p.id()).quantity()).isEqualByComparingTo("2"); assertThat(owned).hasSize(1);
    }
    @Test void ownedTemplateMaterializationIsReusedThroughAlias() {
        ProductTemplate t = template("Æg",Unit.PIECE,InventoryTrackingMode.QUANTITY,"æg fra høns");
        Product p = new Product(UUID.randomUUID(),user,t.id(),"Mine æg",ProductCategory.EGG,Unit.PIECE,InventoryTrackingMode.QUANTITY);owned.put(p.id(),p);
        assertThat(service.importText("æg fra høns 2").imported()).isTrue();
        assertThat(owned).hasSize(1);assertThat(items.get(p.id()).quantity()).isEqualByComparingTo("2");
    }
    @Test void validationErrorsIncludeOriginalLineAndPreventEveryMutation() {
        product("Æg",Unit.PIECE,InventoryTrackingMode.QUANTITY);
        product("Salt",Unit.GRAM,InventoryTrackingMode.PRESENCE);
        product("Vandhanevand",Unit.MILLILITER,InventoryTrackingMode.UNTRACKED);
        var result = service.importText("Cola\nUkendt 2\nÆg\nÆg 2 g\nÆg 0 stk\nSalt 5 g\nVandhanevand\nMel 2 kg\n: 10 g");
        assertThat(result.valid()).isFalse();assertThat(result.imported()).isFalse();
        assertThat(result.items().get(1).line()).isEqualTo(2);assertThat(result.items().get(1).text()).isEqualTo("Ukendt 2");
        assertThat(result.items().subList(1,9)).allMatch(line -> line.error() != null);
        verify(productPort,never()).save(any());verify(shoppingPort,never()).save(any());
    }
    @Test void conflictingUnknownRowsAreRejectedBeforeCreatingProducts() {
        assertThat(service.importText("Cola\nCola 2 stk").valid()).isFalse();
        verify(productPort,never()).save(any());verify(shoppingPort,never()).save(any());
    }
    @Test void supportedDecimalsUseExistingShoppingValidation() {
        var result=service.importText("Æg 1,5 stk\nSaft 150 ml");
        assertThat(result.valid()).isTrue();
        assertThat(service.preview("Æg 0,3 stk").valid()).isFalse();
        assertThat(service.preview("Saft 1,5 ml").valid()).isFalse();
    }
    @Test void ambiguousAliasAndEmptyInputAreErrors() {
        template("Røde løg",Unit.PIECE,InventoryTrackingMode.QUANTITY,"løg");
        template("Gule løg",Unit.PIECE,InventoryTrackingMode.QUANTITY,"løg");
        assertThat(service.preview("løg 3").valid()).isFalse();
        assertThat(service.preview(" \n").valid()).isFalse();
    }
    @Test void exactCatalogNameWinsOverSharedAlias() {
        ProductTemplate onions = template("Løg",Unit.PIECE,InventoryTrackingMode.QUANTITY);
        template("Røde løg",Unit.PIECE,InventoryTrackingMode.QUANTITY,"løg");
        assertThat(service.importText("Løg 3").imported()).isTrue();
        assertThat(owned.values().iterator().next().sourceTemplateId()).isEqualTo(onions.id());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "1.000|1000|GRAM|g", "1.250|1250|GRAM|g", "1000|1000|GRAM|g",
            "1,5|1.5|PIECE|stk", "1.000,5|1000.5|PIECE|stk",
            "1.000.000|1000000|GRAM|g", "1.5|1.5|PIECE|stk"
    }, delimiter = '|')
    void danishNumbersPreserveQuantitiesWithExplicitAndDefaultUnits(String input, String expected, Unit unit, String suffix) {
        product("Testvare", unit, InventoryTrackingMode.QUANTITY);
        for (String line : List.of("Testvare " + input + " " + suffix, "Testvare " + input)) {
            var result = service.preview(line);
            assertThat(result.valid()).as(line).isTrue();
            assertThat(result.items().getFirst().quantity()).isEqualByComparingTo(expected);
            assertThat(result.items().getFirst().unit()).isEqualTo(unit);
        }
    }

    @Test void exportedDanishFarfalleLineRoundTripsThroughPreviewAndImport() {
        Product farfalle = product("Farfalle", Unit.GRAM, InventoryTrackingMode.QUANTITY);
        // Exact text emitted by inventory export's Danish number formatting.
        String exported = "- Farfalle: 1.000 g";
        var preview = service.preview(exported);
        assertThat(preview.valid()).isTrue();
        assertThat(preview.items().getFirst().name()).isEqualTo("Farfalle");
        assertThat(preview.items().getFirst().unit()).isEqualTo(Unit.GRAM);
        assertThat(preview.items().getFirst().quantity()).isEqualByComparingTo("1000");
        assertThat(service.importText(exported).imported()).isTrue();
        assertThat(items.get(farfalle.id()).quantity()).isEqualByComparingTo("1000");
    }

    @Test void malformedGroupingAndUnsupportedFractionalGramsRemainInvalid() {
        product("Farfalle", Unit.GRAM, InventoryTrackingMode.QUANTITY);
        for (String quantity : List.of("1.00,5", "1.000.00", "1.000,5")) {
            assertThat(service.preview("Farfalle " + quantity + " g").valid()).as(quantity).isFalse();
        }
        verify(shoppingPort, never()).save(any());
    }
}
