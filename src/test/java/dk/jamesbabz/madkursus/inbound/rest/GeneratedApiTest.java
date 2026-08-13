package dk.jamesbabz.madkursus.inbound.rest;

import java.util.UUID;

import dk.jamesbabz.madkursus.inbound.rest.inventory.InventoryApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.rest.inventory.InventoryRestMapper;
import dk.jamesbabz.madkursus.inbound.rest.product.ProductApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.rest.product.ProductRestMapper;
import dk.jamesbabz.madkursus.service.applications.InventoryService;
import dk.jamesbabz.madkursus.service.applications.ProductService;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GeneratedApiTest {
    @Test
    void decimalInventoryQuantityReturnsBadRequest() throws Exception {
        InventoryService service = new InventoryService(
                mock(dk.jamesbabz.madkursus.service.ports.InventoryPort.class),
                mock(ProductService.class),
                mock(dk.jamesbabz.madkursus.service.applications.ProductTemplateService.class),
                mock(dk.jamesbabz.madkursus.service.ports.CurrentUserProvider.class));
        InventoryRestMapper mapper = new InventoryRestMapper(new ProductRestMapper());
        MockMvc mvc = standaloneSetup(new InventoryApiController(new InventoryApiDelegateImpl(service, mapper)))
                .setControllerAdvice(new RestErrorHandler()).build();
        mvc.perform(post("/v1/inventory").contentType(MediaType.APPLICATION_JSON).content("""
                {"productId":"%s","quantity":1.5}
                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeQuantityReturnsBadRequest() throws Exception {
        InventoryService service = mock(InventoryService.class);
        InventoryRestMapper mapper = new InventoryRestMapper(new ProductRestMapper());
        MockMvc mvc = standaloneSetup(new InventoryApiController(new InventoryApiDelegateImpl(service, mapper)))
                .setControllerAdvice(new RestErrorHandler()).build();
        mvc.perform(post("/v1/inventory").contentType(MediaType.APPLICATION_JSON).content("""
                {"productId":"%s"}
                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void unknownProductReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        ProductService service = mock(ProductService.class);
        when(service.get(id)).thenThrow(new ResourceNotFoundException("Product", id));
        MockMvc mvc = standaloneSetup(new ProductApiController(
                        new ProductApiDelegateImpl(service, new ProductRestMapper(), mock(dk.jamesbabz.madkursus.service.applications.ProductTemplateService.class))))
                .setControllerAdvice(new RestErrorHandler()).build();
        mvc.perform(get("/v1/products/{id}", id)).andExpect(status().isNotFound());
    }
}
