package dk.jamesbabz.madkursus.inbound.rest;

import java.util.Arrays;

import dk.jamesbabz.madkursus.inbound.rest.dto.CreateProductDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductContractTest {
    @Test
    void productRequestDoesNotExposeAnOwnerField() {
        assertThat(Arrays.stream(CreateProductDTO.class.getMethods()).map(method -> method.getName()))
                .doesNotContain("getUserId", "setUserId", "userId");
    }
}
