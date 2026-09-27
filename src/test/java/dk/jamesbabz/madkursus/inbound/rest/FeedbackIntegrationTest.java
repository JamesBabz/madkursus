package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.MadkursusApplication;
import dk.jamesbabz.madkursus.inbound.security.AuthenticatedUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedbackIntegrationTest {
    private EmbeddedPostgres postgres;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID(), otherId = UUID.randomUUID();
    private final AuthenticatedUser member = new AuthenticatedUser(userId, "feedback-member", "unused", true, false);
    private final AuthenticatedUser admin = new AuthenticatedUser(otherId, "feedback-admin", "unused", true, true);

    @BeforeAll void start() throws Exception {
        postgres = EmbeddedPostgres.start();
        context = new SpringApplicationBuilder(MadkursusApplication.class).web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                        "--spring.datasource.username=postgres", "--spring.datasource.password=");
        jdbc = context.getBean(JdbcTemplate.class);
        for (var principal : new AuthenticatedUser[]{member, admin})
            jdbc.update("insert into users(id,username,password_hash,created_at,enabled) values (?,?,?,now(),true)",
                    principal.id(), principal.username(), "unused");
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
    }

    @AfterAll void stop() throws Exception {
        if (context != null) context.close();
        if (postgres != null) postgres.close();
    }

    @BeforeEach void clearFeedback() { jdbc.update("delete from feedback"); }

    @ParameterizedTest @ValueSource(strings = {"FEEDBACK", "BUG"})
    void authenticatedSubmissionUsesServerIdentityStatusAndTime(String type) throws Exception {
        mvc.perform(post("/v1/feedback").with(user(member)).with(csrf()).contentType("application/json")
                .content("""
                        {"type":"%s","title":" A title ","description":" Details ",
                         "createdBy":"%s","status":"DONE","createdAt":"2000-01-01T00:00:00Z"}
                        """.formatted(type, otherId)))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        var row = jdbc.queryForMap("select * from feedback");
        assertThat(row.get("id")).isInstanceOf(UUID.class);
        assertThat(row.get("type")).isEqualTo(type);
        assertThat(row.get("title")).isEqualTo("A title");
        assertThat(row.get("description")).isEqualTo("Details");
        assertThat(row.get("created_by")).isEqualTo(userId);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select created_at > now() - interval '1 minute' from feedback", Boolean.class)).isTrue();
    }

    @Test void adminListsItemsAcrossUsersAndChangesOnlyStatusThenDeletes() throws Exception {
        UUID first = submit(member, "FEEDBACK"), second = submit(admin, "BUG");
        mvc.perform(get("/v1/admin/feedback").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].createdBy").value(userId.toString()))
                .andExpect(jsonPath("$[1].title").value("Title"))
                .andExpect(jsonPath("$[1].description").value("Details"))
                .andExpect(jsonPath("$[1].type").value("FEEDBACK"))
                .andExpect(jsonPath("$[1].createdAt").isNotEmpty());
        var original = jdbc.queryForMap("select title,description,type,created_by,created_at from feedback where id=?", first);
        for (String value : new String[]{"IN_PROGRESS", "DONE", "OPEN"}) {
            mvc.perform(patch("/v1/admin/feedback/{id}", first).with(user(admin)).with(csrf())
                    .contentType("application/json").content("{\"status\":\"" + value + "\",\"title\":\"Changed\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value));
            assertThat(jdbc.queryForObject("select status from feedback where id=?", String.class, first)).isEqualTo(value);
            assertThat(jdbc.queryForMap("select title,description,type,created_by,created_at from feedback where id=?", first)).isEqualTo(original);
        }
        mvc.perform(delete("/v1/admin/feedback/{id}", first).with(user(admin)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from feedback where id=?", Integer.class, first)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from feedback where id=?", Integer.class, second)).isEqualTo(1);
    }

    @Test void memberCannotListUpdateOrDeleteEvenTheirOwnSubmission() throws Exception {
        UUID id = submit(member, "BUG");
        mvc.perform(get("/v1/admin/feedback").with(user(member))).andExpect(status().isForbidden());
        mvc.perform(patch("/v1/admin/feedback/{id}", id).with(user(member)).with(csrf())
                .contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/feedback/{id}", id).with(user(member)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/v1/feedback").with(user(member))).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/v1/feedback/{id}", id).with(user(member))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from feedback where id=?", String.class, id)).isEqualTo("OPEN");
    }

    @Test void endpointsRequireAuthenticationAndMutationsRequireCsrf() throws Exception {
        String body = "{\"type\":\"BUG\",\"title\":\"Title\",\"description\":\"Details\"}";
        mvc.perform(post("/v1/feedback").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/admin/feedback")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/v1/admin/feedback/{id}", UUID.randomUUID()).with(csrf())
                .contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/v1/admin/feedback/{id}", UUID.randomUUID()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/feedback").with(user(member)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/v1/admin/feedback/{id}", UUID.randomUUID()).with(user(admin))
                .contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/v1/admin/feedback/{id}", UUID.randomUUID()).with(user(admin))).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"type\":\"OTHER\",\"title\":\"Title\",\"description\":\"Details\"}",
            "{\"type\":\"BUG\",\"title\":\"   \",\"description\":\"Details\"}",
            "{\"type\":\"BUG\",\"title\":\"Title\",\"description\":\"   \"}"})
    void invalidSubmissionsAreRejected(String body) throws Exception {
        mvc.perform(post("/v1/feedback").with(user(member)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from feedback", Integer.class)).isZero();
    }

    @Test void invalidStatusAndMissingItemsAreRejected() throws Exception {
        UUID id = submit(member, "FEEDBACK");
        for (String body : new String[]{"{}", "{\"status\":\"INVALID\"}"})
            mvc.perform(patch("/v1/admin/feedback/{id}", id).with(user(admin)).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(patch("/v1/admin/feedback/{id}", UUID.randomUUID()).with(user(admin)).with(csrf())
                .contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/v1/admin/feedback/{id}", UUID.randomUUID()).with(user(admin)).with(csrf())).andExpect(status().isNotFound());
    }

    private UUID submit(AuthenticatedUser principal, String type) throws Exception {
        mvc.perform(post("/v1/feedback").with(user(principal)).with(csrf()).contentType("application/json")
                .content("{\"type\":\"" + type + "\",\"title\":\"Title\",\"description\":\"Details\"}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from feedback where created_by=? order by created_at desc limit 1", UUID.class, principal.id());
    }
}
