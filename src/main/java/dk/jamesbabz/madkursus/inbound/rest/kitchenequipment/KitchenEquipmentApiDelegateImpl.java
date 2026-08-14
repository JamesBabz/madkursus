package dk.jamesbabz.madkursus.inbound.rest.kitchenequipment;

import java.net.URI; import java.util.*;
import dk.jamesbabz.madkursus.inbound.rest.KitchenEquipmentApiDelegate;
import dk.jamesbabz.madkursus.inbound.rest.dto.*;
import dk.jamesbabz.madkursus.service.applications.KitchenEquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class KitchenEquipmentApiDelegateImpl implements KitchenEquipmentApiDelegate {
    private final KitchenEquipmentService service; private final KitchenEquipmentRestMapper mapper;
    public ResponseEntity<List<KitchenEquipmentDTO>> getKitchenEquipmentList(){return ResponseEntity.ok(service.getAll().stream().map(mapper::dto).toList());}
    public ResponseEntity<KitchenEquipmentDTO> getKitchenEquipment(UUID id){return ResponseEntity.ok(mapper.dto(service.get(id)));}
    public ResponseEntity<KitchenEquipmentDTO> createKitchenEquipment(KitchenEquipmentInputDTO input){var result=mapper.dto(service.create(mapper.input(input)));return ResponseEntity.created(URI.create("/v1/kitchen-equipment/"+result.getId())).body(result);}
    public ResponseEntity<KitchenEquipmentDTO> updateKitchenEquipment(UUID id,KitchenEquipmentInputDTO input){return ResponseEntity.ok(mapper.dto(service.update(id,mapper.input(input))));}
    public ResponseEntity<Void> deleteKitchenEquipment(UUID id){service.delete(id);return ResponseEntity.noContent().build();}
}
