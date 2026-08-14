package dk.jamesbabz.madkursus.inbound.rest;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.*;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductTemplateSeedTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void canonicalCatalogIsCompleteUniqueTypedAndUsesStableMigrationIds() throws Exception {
        JsonNode root=read("seed/product-templates.json"),products=root.get("products");
        assertThat(root.path("metadata").path("catalogCount").asInt()).isEqualTo(384);assertThat(products).hasSize(384);
        Set<String> keys=new HashSet<>(),ids=new HashSet<>(),names=new HashSet<>();int aliases=0,common=0,presence=0;
        for(JsonNode product:products){String key=product.path("key").asText(),name=product.path("name").asText(),id=product.path("id").asText();
            assertThat(key).matches("[A-Z][A-Z0-9_]*");assertThat(keys.add(key)).as("unique key %s",key).isTrue();
            assertThat(ids.add(id)).as("unique id %s",id).isTrue();assertThat(names.add(normalize(name))).as("unique name %s",name).isTrue();
            assertThat(UUID.fromString(id)).isEqualTo(stableId(name));assertThat(ProductCategory.valueOf(product.path("category").asText())).isNotNull();
            assertThat(Unit.valueOf(product.path("defaultUnit").asText())).isIn(Unit.GRAM,Unit.MILLILITER,Unit.PIECE);
            InventoryTrackingMode mode=InventoryTrackingMode.valueOf(product.path("defaultTrackingMode").asText());assertThat(mode).isIn(InventoryTrackingMode.QUANTITY,InventoryTrackingMode.PRESENCE);if(mode==InventoryTrackingMode.PRESENCE)presence++;
            if(product.path("common").asBoolean())common++;Set<String> localAliases=new HashSet<>();
            for(JsonNode alias:product.path("aliases")){String value=alias.asText();assertThat(value).isEqualTo(value.trim()).isNotBlank();assertThat(localAliases.add(normalize(value))).as("unique alias on %s",name).isTrue();aliases++;}}
        assertThat(aliases).isEqualTo(211);assertThat(common).isEqualTo(94);assertThat(presence).isEqualTo(81);
    }

    @Test void canonicalCatalogExactlyRepresentsV6V10AndV14PointOneEndState() throws Exception {
        JsonNode canonical=read("seed/product-templates.json").get("products"),legacy=read("db/seed/madkursus-product-templates-seed.json").get("products");Map<String,JsonNode> byName=index(canonical);
        assertThat(legacy).hasSize(383);assertThat(byName).hasSize(384);Set<String> explicitPresence=v10PresenceNames();
        for(JsonNode old:legacy){JsonNode current=byName.get(old.path("name").asText());assertThat(current).isNotNull();assertThat(current.path("category")).isEqualTo(old.path("category"));assertThat(current.path("defaultUnit")).isEqualTo(old.path("defaultUnit"));assertThat(current.path("aliases")).isEqualTo(old.path("aliases"));assertThat(current.path("common")).isEqualTo(old.path("common"));String expected="SPICE".equals(old.path("category").asText())||explicitPresence.contains(old.path("name").asText())?"PRESENCE":"QUANTITY";assertThat(current.path("defaultTrackingMode").asText()).isEqualTo(expected);}
        JsonNode sausages=byName.get("Pølser");assertThat(sausages.path("id").asText()).isEqualTo(stableId("Pølser").toString());assertThat(sausages.path("category").asText()).isEqualTo("MEAT");assertThat(sausages.path("defaultUnit").asText()).isEqualTo("GRAM");assertThat(sausages.path("defaultTrackingMode").asText()).isEqualTo("QUANTITY");
    }

    @Test void aliasesAndTrackingSemanticsRemainSearchFriendlyAndSeparateFromUnits() throws Exception {
        Map<String,JsonNode> products=index(read("seed/product-templates.json").get("products"));assertThat(aliasValues(products.get("Hakket oksekød"))).contains("oksefars");assertThat(aliasValues(products.get("Sojasauce"))).contains("soya");assertThat(aliasValues(products.get("Majsstivelse"))).contains("maizena");assertThat(products.get("Salt").path("defaultUnit").asText()).isEqualTo("GRAM");assertThat(products.get("Salt").path("defaultTrackingMode").asText()).isEqualTo("PRESENCE");
        Map<String,List<String>> owners=new HashMap<>();products.values().forEach(product->product.path("aliases").forEach(alias->owners.computeIfAbsent(normalize(alias.asText()),ignored->new ArrayList<>()).add(product.path("name").asText())));Map<String,List<String>> ambiguous=new HashMap<>();owners.forEach((alias,values)->{if(values.size()>1)ambiguous.put(alias,values);});assertThat(ambiguous).containsOnlyKeys("oliven");assertThat(ambiguous.get("oliven")).containsExactlyInAnyOrder("Grønne oliven","Sorte oliven");
    }

    @Test void allGlobalSeedCrossReferencesResolveAgainstCanonicalProducts() throws Exception {
        Set<String> productNames=index(read("seed/product-templates.json").get("products")).keySet();read("seed/recipe-templates.json").get("recipes").forEach(recipe->recipe.path("ingredients").forEach(ingredient->assertThat(productNames).contains(ingredient.path("productTemplate").asText())));List<String> futureReferences=new ArrayList<>();collectNamedFields(read("seed/cooking-processes.json"),"productTemplate",futureReferences);assertThat(productNames).containsAll(futureReferences);
    }

    @Test void canonicalCookingProcessSourceMatchesAlreadyAppliedV19Input() throws Exception {assertThat(read("seed/cooking-processes.json")).isEqualTo(read("db/seed/madkursus-cooking-processes-seed.json"));}

    private Set<String> v10PresenceNames() throws Exception {String sql=resourceText("db/migration/V10__product_template_default_tracking_mode.sql"),names=sql.substring(sql.indexOf("WHERE name IN"));Matcher matcher=Pattern.compile("'([^']+)'").matcher(names);Set<String> result=new HashSet<>();while(matcher.find())result.add(matcher.group(1));return result;}
    private Map<String,JsonNode> index(JsonNode products){Map<String,JsonNode> result=new HashMap<>();products.forEach(product->result.put(product.path("name").asText(),product));return result;}
    private List<String> aliasValues(JsonNode product){List<String> result=new ArrayList<>();product.path("aliases").forEach(alias->result.add(alias.asText()));return result;}
    private void collectNamedFields(JsonNode node,String name,List<String> values){if(node.isObject())node.fields().forEachRemaining(field->{if(field.getKey().equals(name)&&field.getValue().isTextual())values.add(field.getValue().asText());collectNamedFields(field.getValue(),name,values);});else if(node.isArray())node.forEach(child->collectNamedFields(child,name,values));}
    private UUID stableId(String name){return UUID.nameUUIDFromBytes(("madkursus-template:"+normalize(name)).getBytes(StandardCharsets.UTF_8));}
    private JsonNode read(String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).as(path).isNotNull();return mapper.readTree(stream);}}
    private String resourceText(String path)throws Exception{try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).as(path).isNotNull();return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
    private String normalize(String value){return value.trim().toLowerCase(Locale.ROOT);}
}
