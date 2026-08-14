package dk.jamesbabz.madkursus.service.applications;

import java.util.*;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductTemplateServiceTest {
    @Test void copiesTemplateValuesIntoIndependentProductWithoutAliases() {
        UUID id=UUID.randomUUID();ProductTemplate template=new ProductTemplate(id,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of("fint salt"),true);ProductTemplatePort port=mock(ProductTemplatePort.class);ProductService products=mock(ProductService.class);when(port.findById(id)).thenReturn(Optional.of(template));Product created=new Product(UUID.randomUUID(),UUID.randomUUID(),id,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE);when(products.createFromTemplate(id,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE)).thenReturn(created);
        assertThat(new ProductTemplateService(port,products).addToProducts(id)).isEqualTo(created);assertThat(created.sourceTemplateId()).isEqualTo(id);assertThat(created.defaultUnit()).isEqualTo(Unit.GRAM);assertThat(created.inventoryTrackingMode()).isEqualTo(InventoryTrackingMode.PRESENCE);assertThat(template.aliases()).containsExactly("fint salt");verify(products).createFromTemplate(id,"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE);
    }
    @Test void unknownTemplateReturnsNotFound(){ProductTemplatePort port=mock(ProductTemplatePort.class);UUID id=UUID.randomUUID();when(port.findById(id)).thenReturn(Optional.empty());assertThatThrownBy(()->new ProductTemplateService(port,mock(ProductService.class)).addToProducts(id)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void searchDelegatesAliasQueryWithoutChangingCatalogData(){ProductTemplatePort port=mock(ProductTemplatePort.class);ProductService products=mock(ProductService.class);ProductTemplate beef=new ProductTemplate(UUID.randomUUID(),"Hakket oksekød",ProductCategory.MEAT,Unit.GRAM,InventoryTrackingMode.QUANTITY,List.of("oksefars"),true);when(port.search("oksefars",null)).thenReturn(List.of(beef));assertThat(new ProductTemplateService(port,products).search("oksefars",null)).containsExactly(beef);verifyNoInteractions(products);}
}
