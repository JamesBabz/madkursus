package dk.jamesbabz.madkursus.outbound.producttemplate;

import dk.jamesbabz.madkursus.outbound.producttemplate.details.ProductTemplateJpaRepository;
import dk.jamesbabz.madkursus.outbound.producttemplate.mappers.ProductTemplateEntityMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ProductTemplateResolutionTest {
    @Test void termResolutionUsesExactCatalogQueryNotSubstringSearch() {
        var repository=mock(ProductTemplateJpaRepository.class);
        var adapter=new ProductTemplateAdapterImpl(repository,new ProductTemplateEntityMapper());
        assertThat(adapter.findByNameOrAlias("  HAKKET OKSEKØD  ")).isEmpty();
        verify(repository).findByNameOrAlias("hakket oksekød");verifyNoMoreInteractions(repository);
    }
}
