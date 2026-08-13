package dk.jamesbabz.madkursus.inbound.rest;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductTemplateSeedTest {
    @Test void seedIsCompleteUniqueAndUsesValidUnits() throws Exception {
        try(var in=getClass().getResourceAsStream("/db/seed/madkursus-product-templates-seed.json")) {
            JsonNode root=new ObjectMapper().readTree(in); JsonNode products=root.get("products");
            assertThat(products.size()).isEqualTo(383);
            assertThat(root.get("metadata").get("commonCount").asInt()).isEqualTo(94);
            Set<String> names=new HashSet<>(); int aliases=0; int common=0;
            for(JsonNode p:products) {
                assertThat(names.add(p.get("name").asText().toLowerCase())).isTrue();
                assertThat(p.get("defaultUnit").asText()).isIn("GRAM","MILLILITER","PIECE");
                aliases += p.get("aliases").size(); if(p.get("common").asBoolean()) common++;
            }
            assertThat(aliases).isEqualTo(211); assertThat(common).isEqualTo(94);
        }
    }
    @Test void representativeAliasesExist() throws Exception {
        try(var in=getClass().getResourceAsStream("/db/seed/madkursus-product-templates-seed.json")) {
            String json=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).toLowerCase();
            assertThat(json).contains("oksefars","soya","maizena","løg");
        }
    }
    @Test void recipeRelevantTemplateUnitsRemainUnchanged() throws Exception {
        try(var in=getClass().getResourceAsStream("/db/seed/madkursus-product-templates-seed.json")) {
            JsonNode products=new ObjectMapper().readTree(in).get("products");
            assertThat(find(products,"Salt").get("defaultUnit").asText()).isEqualTo("GRAM");
            assertThat(find(products,"Hakket oksekød").get("defaultUnit").asText()).isEqualTo("GRAM");
            assertThat(find(products,"Æg").get("defaultUnit").asText()).isEqualTo("PIECE");
        }
    }
    private JsonNode find(JsonNode products,String name) {
        for(JsonNode product:products) if(product.get("name").asText().equals(name)) return product;
        throw new AssertionError("Missing template " + name);
    }
}
