package dk.jamesbabz.madkursus.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShoppingProductCreationIntegrationTest {
    @Test void shoppingFlowCreatesAnOwnedProductAndUsesNormalAdditiveShoppingSemantics() throws Exception {
        try (var postgres = EmbeddedPostgres.start(); var context = new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres", "--spring.datasource.password=")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var mapper = context.getBean(ObjectMapper.class);
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
            UUID userId = UUID.randomUUID(), otherId = UUID.randomUUID();
            for (UUID id : new UUID[]{userId, otherId})
                jdbc.update("insert into users(id,username,password_hash,created_at,enabled) values (?,?,?,now(),true)", id, id.toString(), "unused");
            var principal = new AuthenticatedUser(userId, userId.toString(), "unused", true);
            var other = new AuthenticatedUser(otherId, otherId.toString(), "unused", true);
            Integer templateCount = jdbc.queryForObject("select count(*) from product_templates", Integer.class);

            // The shopping dialog uses the same Product endpoint as the Products page.
            String response = mvc.perform(post("/v1/products").with(user(principal)).with(csrf()).contentType("application/json")
                    .content("{\"name\":\" New shopping product \",\"category\":\"OTHER\",\"defaultUnit\":\"PIECE\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("New shopping product"))
                    .andExpect(jsonPath("$.inventoryTrackingMode").value("QUANTITY"))
                    .andReturn().getResponse().getContentAsString();
            UUID productId = UUID.fromString(mapper.readTree(response).get("id").asText());
            var product = jdbc.queryForMap("select user_id,source_template_id from products where id=?", productId);
            assertThat(product.get("user_id")).isEqualTo(userId);
            assertThat(product.get("source_template_id")).isNull();
            assertThat(jdbc.queryForObject("select count(*) from product_templates", Integer.class)).isEqualTo(templateCount);

            String add = "{\"productId\":\"" + productId + "\",\"quantity\":1.5}";
            mvc.perform(post("/v1/shopping-list/items").with(user(principal)).with(csrf()).contentType("application/json").content(add))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.product.id").value(productId.toString()))
                    .andExpect(jsonPath("$.quantity").value(1.5));
            // Adding the now-existing Product again still merges into the active item.
            mvc.perform(post("/v1/shopping-list/items").with(user(principal)).with(csrf()).contentType("application/json").content(add))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(3));
            assertThat(jdbc.queryForObject("select count(*) from shopping_list_items where user_id=? and product_id=?", Integer.class, userId, productId)).isEqualTo(1);
            mvc.perform(get("/v1/shopping-list").with(user(principal))).andExpect(jsonPath("$[0].product.id").value(productId.toString()));
            mvc.perform(get("/v1/products/{id}", productId).with(user(other))).andExpect(status().isNotFound());
            mvc.perform(post("/v1/shopping-list/items").with(user(other)).with(csrf()).contentType("application/json").content(add)).andExpect(status().isNotFound());
        }
    }
}
