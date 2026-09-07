package dk.jamesbabz.madkursus.inbound.rest;

import dk.jamesbabz.madkursus.inbound.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProductApiController.class, AuthApiController.class, CookingProcessApiController.class,NutritionAdminApiController.class})
@Import(SecurityConfig.class)
class SecurityRulesTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductApiDelegate productDelegate;
    @MockitoBean AuthApiDelegate authDelegate;
    @MockitoBean CookingProcessApiDelegate cookingProcessDelegate;
    @MockitoBean NutritionAdminApiDelegate nutritionAdminDelegate;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void productApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/v1/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/cooking-processes")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationStatusAndCsrfEndpointArePublic() throws Exception {
        when(authDelegate.getRegistrationStatus()).thenReturn(org.springframework.http.ResponseEntity.ok(
                new dk.jamesbabz.madkursus.inbound.rest.dto.RegistrationStatusDTO(false)));
        mvc.perform(get("/v1/auth/registration-status")).andExpect(status().isOk());
        when(authDelegate.getCsrfToken()).thenReturn(org.springframework.http.ResponseEntity.ok(
                new dk.jamesbabz.madkursus.inbound.rest.dto.CsrfTokenDTO("token")));
        mvc.perform(get("/v1/auth/csrf")).andExpect(status().isOk());
    }

    @Test
    void registrationMutationRequiresCsrfToken() throws Exception {
        mvc.perform(post("/v1/auth/register").contentType("application/json")
                .content("{\"username\":\"user\",\"password\":\"long-password\"}"))
                .andExpect(status().isForbidden());
        when(authDelegate.register(any())).thenReturn(org.springframework.http.ResponseEntity.status(201).body(
                new dk.jamesbabz.madkursus.inbound.rest.dto.CurrentUserDTO(java.util.UUID.randomUUID(), "user")));
        mvc.perform(post("/v1/auth/register").with(csrf()).contentType("application/json")
                .content("{\"username\":\"user\",\"password\":\"long-password\"}"))
                .andExpect(status().is2xxSuccessful());
    }
    @Test void normalUserCannotAccessOrMutateGlobalNutrition()throws Exception{mvc.perform(get("/v1/admin/dtu-catalog").with(user("normal"))).andExpect(status().isForbidden());mvc.perform(post("/v1/admin/dtu-catalog/import-bundled").with(user("normal")).with(csrf())).andExpect(status().isForbidden());mvc.perform(put("/v1/admin/product-template-nutrition/"+java.util.UUID.randomUUID()).with(user("normal")).with(csrf()).contentType("application/json").content("{\"carbohydrateGrams\":0,\"basisQuantity\":100,\"basisUnit\":\"GRAM\",\"source\":\"test\"}")).andExpect(status().isForbidden());}
}
