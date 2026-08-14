package dk.jamesbabz.madkursus.inbound.rest;

import java.util.UUID;
import dk.jamesbabz.madkursus.inbound.rest.kitchenequipment.*;
import dk.jamesbabz.madkursus.service.applications.KitchenEquipmentService;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class KitchenEquipmentApiTest {
 @Test void crossUserGetUpdateAndDeleteAreReportedAsNotFound() throws Exception {
  UUID id=UUID.randomUUID();KitchenEquipmentService service=mock(KitchenEquipmentService.class);
  when(service.get(id)).thenThrow(new ResourceNotFoundException("Kitchen equipment",id));
  when(service.update(eq(id),any())).thenThrow(new ResourceNotFoundException("Kitchen equipment",id));
  doThrow(new ResourceNotFoundException("Kitchen equipment",id)).when(service).delete(id);
  var mvc=standaloneSetup(new KitchenEquipmentApiController(new KitchenEquipmentApiDelegateImpl(service,new KitchenEquipmentRestMapper()))).setControllerAdvice(new RestErrorHandler()).build();
  mvc.perform(get("/v1/kitchen-equipment/{id}",id)).andExpect(status().isNotFound());
  String body="{\"equipmentType\":\"POT\",\"name\":\"Gryde\",\"capacityMl\":5000}";
  mvc.perform(patch("/v1/kitchen-equipment/{id}",id).contentType("application/json").content(body)).andExpect(status().isNotFound());
  mvc.perform(delete("/v1/kitchen-equipment/{id}",id)).andExpect(status().isNotFound());
 }
}
