package dk.jamesbabz.madkursus.inbound.security;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginLoggingConfigurationTest {
    @Test void requestPayloadLoggingIsExplicitlyDisabled() throws Exception {
        try (var stream=getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            assertThat(stream).isNotNull();
            String yaml=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertThat(yaml).contains("org.springframework.web.HttpLogging: OFF",
                    "org.springframework.web.filter.CommonsRequestLoggingFilter: OFF",
                    "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor: INFO");
        }
    }
}
