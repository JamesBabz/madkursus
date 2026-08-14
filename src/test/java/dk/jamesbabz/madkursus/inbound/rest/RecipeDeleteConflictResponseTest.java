package dk.jamesbabz.madkursus.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import dk.jamesbabz.madkursus.service.exceptions.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RecipeDeleteConflictResponseTest {
    @Test void activePlanConflictIsReturnedAs409WithActionableDanishMessage() {
        String message="Opskriften er stadig med i en aktiv madplan. Fjern den fra madplanen først.";
        var response=new RestErrorHandler().conflict(new ConflictException(message));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull(); assertThat(response.getBody().getMessage()).isEqualTo(message);
    }
}
