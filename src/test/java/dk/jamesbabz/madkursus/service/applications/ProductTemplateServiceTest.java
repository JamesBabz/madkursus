package dk.jamesbabz.madkursus.service.applications;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductTemplateServiceTest {
    @Test void copiesTemplateValuesIntoNormalProduct() {
        UUID id=UUID.randomUUID(); ProductTemplate template=new ProductTemplate(id,"Æg",ProductCategory.EGG,Unit.PIECE,List.of("hønseæg"),true);
        ProductTemplatePort port=mock(ProductTemplatePort.class); ProductService products=mock(ProductService.class);
        when(port.findById(id)).thenReturn(Optional.of(template));
        Product created=new Product(UUID.randomUUID(),UUID.randomUUID(),"Æg",ProductCategory.EGG,Unit.PIECE);
        when(products.create("Æg",ProductCategory.EGG,Unit.PIECE)).thenReturn(created);
        assertThat(new ProductTemplateService(port,products).addToProducts(id)).isEqualTo(created);
        assertThat(template.aliases()).containsExactly("hønseæg");
    }
    @Test void unknownTemplateReturnsNotFound() {
        ProductTemplatePort port=mock(ProductTemplatePort.class); UUID id=UUID.randomUUID();
        when(port.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new ProductTemplateService(port,mock(ProductService.class)).addToProducts(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
