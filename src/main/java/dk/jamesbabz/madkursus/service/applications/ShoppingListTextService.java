package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.models.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShoppingListTextService {
    private final ProductService products;
    private final ProductTemplateService templates;
    private final ShoppingListService shopping;
    private static final String DANISH_GROUPED_NUMBER = "[+-]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?";
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)(?:\\s*:\\s*|\\s+)(" + DANISH_GROUPED_NUMBER + "|[+-]?\\d+(?:[.,]\\d+)?)(?:\\s+([\\p{L}.]+))?$");
    public record Line(int line, String text, String name, BigDecimal quantity, Unit unit, boolean newProduct, String error) {}
    public record Result(boolean valid, boolean imported, List<Line> items) {}
    private record Entry(Line line, Product product, ProductTemplate template) {}

    @Transactional(readOnly = true)
    public Result preview(String text) { return result(plan(text), false); }

    @Transactional
    public Result importText(String text) {
        List<Entry> entries = plan(text);
        Result preview = result(entries, false);
        if (!preview.valid()) return preview;
        Map<String, Product> materialized = new HashMap<>();
        for (Entry entry : entries) {
            Product product = entry.product();
            if (product.id() == null) {
                product = materialized.computeIfAbsent(key(product.name()), ignored -> {
                    Product p = entry.product();
                    ProductTemplate t = entry.template();
                    return t == null ? products.create(p.name(), p.category(), p.defaultUnit(), p.inventoryTrackingMode())
                            : products.createFromTemplate(t.id(), t.name(), t.category(), t.defaultUnit(), t.defaultTrackingMode());
                });
            }
            // Reuse the normal additive merge and PRESENCE behavior, within this transaction.
            shopping.add(product.id(), entry.line().quantity());
        }
        return result(entries, true);
    }

    private Result result(List<Entry> entries, boolean imported) {
        return new Result(entries.stream().noneMatch(e -> e.line().error() != null), imported,
                entries.stream().map(Entry::line).toList());
    }

    private List<Entry> plan(String text) {
        if (text == null || text.isBlank() || text.length() > 50000)
            return List.of(error(1, "", "Indsæt en liste på højst 50.000 tegn."));
        String[] lines = text.split("\\R", -1);
        if (Arrays.stream(lines).filter(s -> !s.isBlank()).count() > 200)
            return List.of(error(1, "", "Indsæt højst 200 varer ad gangen."));
        List<Product> available = new ArrayList<>(products.getAll());
        List<ProductTemplate> catalog = null;
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String original = lines[index];
            if (original.isBlank()) continue;
            try {
                String value = spaces(original).replaceFirst("^[-•]\\s*", "");
                var match = AMOUNT.matcher(value);
                BigDecimal quantity = null;
                Unit explicitUnit = null;
                String name = value;
                if (match.matches()) {
                    name = spaces(match.group(1));
                    String number = match.group(2);
                    // Danish grouping takes precedence over the existing ungrouped decimal-dot syntax.
                    if (number.matches(DANISH_GROUPED_NUMBER)) number = number.replace(".", "");
                    quantity = new BigDecimal(number.replace(',', '.'));
                    if (match.group(3) != null) explicitUnit = unit(match.group(3));
                } else if (value.contains(":") || value.matches(".*\\d.*")) {
                    throw invalid("Brug varenavn efterfulgt af mængde og evt. g, ml eller stk.");
                }
                if (name.isBlank() || name.length() > 255 || name.contains(":")) throw invalid("Angiv et varenavn på højst 255 tegn.");
                String nameKey = key(name);
                List<Product> matches = available.stream().filter(p -> key(p.name()).equals(nameKey)).toList();
                if (matches.size() > 1) throw invalid("Flere egne produkter matcher navnet. Brug et entydigt navn.");
                Product product = matches.isEmpty() ? null : matches.getFirst();
                ProductTemplate template = null;
                if (product == null) {
                    List<ProductTemplate> candidates = templates.findByNameOrAlias(name);
                    if (candidates.isEmpty()) {
                        String identity = key(name);
                        if (catalog == null) catalog = templates.search("", null);
                        candidates = catalog.stream().filter(t -> key(t.name()).equals(identity)
                                || t.aliases().stream().anyMatch(a -> key(a).equals(identity))).toList();
                    }
                    List<ProductTemplate> exactNames = candidates.stream().filter(t -> key(t.name()).equals(nameKey)).toList();
                    if (!exactNames.isEmpty()) candidates = exactNames;
                    if (candidates.size() > 1) throw invalid("Flere katalogvarer matcher navnet. Brug det præcise varenavn.");
                    if (!candidates.isEmpty()) {
                        template = candidates.getFirst();
                        UUID templateId = template.id();
                        String templateName = key(template.name());
                        product = available.stream().filter(p -> templateId.equals(p.sourceTemplateId()) || key(p.name()).equals(templateName))
                                .findFirst().orElse(null);
                        if (product == null) product = new Product(null, null, template.id(), template.name(), template.category(),
                                template.defaultUnit(), template.defaultTrackingMode());
                    }
                }
                if (product == null) {
                    if (quantity != null && explicitUnit == null) throw invalid("Ukendt vare: angiv enhed (g, ml eller stk.) sammen med mængden.");
                    // PRESENCE products still require a storage unit; it is never displayed or counted.
                    product = new Product(null, null, null, name, ProductCategory.OTHER,
                            explicitUnit == null ? Unit.PIECE : explicitUnit,
                            quantity == null ? InventoryTrackingMode.PRESENCE : InventoryTrackingMode.QUANTITY);
                }
                if (product.inventoryTrackingMode() == InventoryTrackingMode.UNTRACKED) throw invalid("Varen føres ikke på indkøbslisten (UNTRACKED).");
                if (explicitUnit != null && explicitUnit != product.defaultUnit()) throw invalid("Enheden passer ikke til varen. Brug " + label(product.defaultUnit()) + ".");
                if (product.inventoryTrackingMode() == InventoryTrackingMode.QUANTITY && quantity == null) throw invalid("Angiv en mængde i " + label(product.defaultUnit()) + ".");
                if (product.inventoryTrackingMode() == InventoryTrackingMode.PRESENCE && quantity != null) throw invalid("Denne vare føres uden mængde. Skriv kun varenavnet.");
                shopping.requireValidQuantity(product, quantity);
                entries.add(new Entry(new Line(index + 1, original, product.name(), quantity,
                        quantity == null ? null : product.defaultUnit(), product.id() == null && product.sourceTemplateId() == null, null), product, template));
                if (!available.contains(product)) available.add(product);
            } catch (InvalidInputException | NumberFormatException failure) {
                entries.add(error(index + 1, original, failure.getMessage()));
            }
        }
        return entries;
    }

    private static Entry error(int line, String text, String message) { return new Entry(new Line(line, text, null, null, null, false, message), null, null); }
    private static InvalidInputException invalid(String message) { return new InvalidInputException(message); }
    private static String spaces(String value) { return value.replaceAll("[\\s\\p{Z}]+", " ").trim(); }
    private static String key(String value) { return spaces(value).toLowerCase(Locale.ROOT); }
    private static Unit unit(String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace(".", "")) {
            case "g", "gram" -> Unit.GRAM;
            case "ml", "milliliter" -> Unit.MILLILITER;
            case "stk", "styk", "stykker" -> Unit.PIECE;
            default -> throw invalid("Ukendt enhed. Brug g, gram, ml, stk. eller styk.");
        };
    }
    private static String label(Unit unit) { return switch (unit) { case GRAM -> "g"; case MILLILITER -> "ml"; case PIECE -> "stk."; }; }
}
