package dk.jamesbabz.madkursus.inbound.rest;
import dk.jamesbabz.madkursus.inbound.rest.recipeimport.RecipeTemplateImportApiDelegateImpl;
import dk.jamesbabz.madkursus.inbound.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value=RecipeTemplateImportApiController.class,properties="madkursus.recipe-template-import.enabled=false")
@Import({SecurityConfig.class,RecipeTemplateImportApiDelegateImpl.class})
class RecipeTemplateImportDisabledTest {
    @Autowired MockMvc mvc;@MockitoBean UserDetailsService users;
    @Test void explicitlyDisabledEvenForAdmin()throws Exception {
        mvc.perform(get("/v1/admin/recipe-template-import").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        for(String action:new String[]{"validate","import"})mvc.perform(post("/v1/admin/recipe-template-import/"+action).with(user("admin").roles("ADMIN")).with(csrf()).contentType("text/plain").content("{}")).andExpect(status().isForbidden());
    }
}
