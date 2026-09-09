package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShoppingListTextIntegrationTest {
    @Test void realApiPreviewsAddsAndRollsBackEntireImportOnDatabaseFailure() throws Exception {
        try (var postgres = EmbeddedPostgres.start(); var context = new SpringApplicationBuilder(MadkursusApplication.class)
                .web(WebApplicationType.SERVLET).run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres", "--spring.datasource.password=")) {
            var jdbc = context.getBean(JdbcTemplate.class);
            UUID id = UUID.randomUUID();
            jdbc.update("insert into users(id,username,password_hash,created_at,enabled) values (?,?,?,now(),true)", id, "text-import-test", "unused");
            var principal = new AuthenticatedUser(id, "text-import-test", "unused", true, false);
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext)context).apply(springSecurity()).build();
            String text = "Testmiddel\nTestbønner 2 stk";
            mvc.perform(post("/v1/shopping-list/import/preview").with(user(principal)).with(csrf()).contentType("text/plain").content(text))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.imported").value(false));
            assertThat(jdbc.queryForObject("select count(*) from products where user_id=?", Integer.class, id)).isZero();
            mvc.perform(post("/v1/shopping-list/import").with(user(principal)).with(csrf()).contentType("text/plain").content(text + "\nUkendt 5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
            assertThat(jdbc.queryForObject("select count(*) from products where user_id=?", Integer.class, id)).isZero();
            for (int repeat=0; repeat<2; repeat++) {
                mvc.perform(post("/v1/shopping-list/import").with(user(principal)).with(csrf()).contentType("text/plain").content(text))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(true));
            }
            assertThat(jdbc.queryForObject("select count(*) from products where user_id=?", Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from shopping_list_items where user_id=?", Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select sum(quantity) from shopping_list_items where user_id=?", Integer.class, id)).isEqualTo(4);
            // Fail the second item at the database boundary after the first item has been processed.
            jdbc.execute("create function fail_text_import() returns trigger language plpgsql as $$ begin if exists (select 1 from products where id=new.product_id and name='Rollback anden') then raise exception 'test import failure'; end if; return new; end $$");
            jdbc.execute("create trigger fail_text_import before insert on shopping_list_items for each row execute function fail_text_import()");
            mvc.perform(post("/v1/shopping-list/import").with(user(principal)).with(csrf()).contentType("text/plain")
                    .content("Testbønner 3\nRollback første\nRollback anden")).andExpect(status().is5xxServerError());
            assertThat(jdbc.queryForObject("select count(*) from products where user_id=?", Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from shopping_list_items where user_id=?", Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select sum(quantity) from shopping_list_items where user_id=?", Integer.class, id)).isEqualTo(4);
        }
    }
}
