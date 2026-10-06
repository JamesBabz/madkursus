package dk.jamesbabz.madkursus.inbound.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeTemplateImportConfigurationTest {
    private final ApplicationContextRunner configuration = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test void baseAndOrdinaryTestsStayDisabledWithoutLocalProfile() {
        configuration.run(context -> {
            assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("local");
            assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isFalse();
        });
    }

    @Test void productionProfileInheritsTheSafeBaseDefault() {
        configuration.withPropertyValues("spring.profiles.active=production").run(context ->
                assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isFalse());
    }

    @Test void localAuthoringProfileExplicitlyEnablesImport() {
        configuration.withPropertyValues("spring.profiles.active=local").run(context ->
                assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isTrue());
    }

    @Test void bootRunLocalDefaultIsOverriddenByAnExplicitProductionProfile() {
        configuration.withPropertyValues("spring.profiles.default=local").run(context ->
                assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isTrue());
        configuration.withPropertyValues("spring.profiles.default=local", "spring.profiles.active=production").run(context ->
                assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isFalse());
    }

    @Test void importTestsCanOptInWithoutActivatingLocalProfile() {
        configuration.withPropertyValues("madkursus.recipe-template-import.enabled=true").run(context -> {
            assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("local");
            assertThat(context.getEnvironment().getProperty("madkursus.recipe-template-import.enabled", Boolean.class)).isTrue();
        });
    }
}
