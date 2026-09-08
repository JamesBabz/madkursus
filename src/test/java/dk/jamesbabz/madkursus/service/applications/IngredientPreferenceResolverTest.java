package dk.jamesbabz.madkursus.service.applications;
import dk.jamesbabz.madkursus.service.models.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class IngredientPreferenceResolverTest {
    @Test void onlyUnambiguousCatalogIdentityIsAcceptedAndDuplicateTermsAreDeduplicated() {
        var port=mock(dk.jamesbabz.madkursus.service.ports.ProductTemplatePort.class);var templates=new ProductTemplateService(port,mock(ProductService.class));var resolver=new IngredientPreferenceResolver(templates);
        var first=new ProductTemplate(UUID.randomUUID(),"First",ProductCategory.OTHER,Unit.GRAM,List.of(),true);
        var second=new ProductTemplate(UUID.randomUUID(),"Second",ProductCategory.OTHER,Unit.GRAM,List.of(),true);
        when(port.findByNameOrAlias("exact")).thenReturn(List.of(first));
        when(port.findByNameOrAlias("ambiguous")).thenReturn(List.of(first,second));
        assertThat(resolver.resolve(List.of("exact","exact","ambiguous","unknown"))).containsExactly(first.id());
        verify(port,times(1)).findByNameOrAlias("exact");
    }
}
