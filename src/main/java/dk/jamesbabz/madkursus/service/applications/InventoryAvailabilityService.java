package dk.jamesbabz.madkursus.service.applications;

import java.math.BigDecimal;
import java.util.*;
import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.ports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class InventoryAvailabilityService {
    private final InventoryPort inventoryPort;
    private final MealPlanPort mealPlanPort;
    private final CurrentUserProvider currentUser;
    private final RecipeQuantityNormalizer normalizer;

    public record TemplateAvailability(ProductTemplate template,Product product,InventoryTrackingMode trackingMode,
            BigDecimal physicalQuantity,BigDecimal reservedQuantity,BigDecimal availableQuantity,
            BigDecimal plannedShortfall,int plannedUsageCount,List<InventoryReservationDetail> reservations) {}
    public record Snapshot(Map<UUID,InventoryItem> inventoryByProductId,Map<UUID,Reservation> reservationsByTemplateId) {
        public Reservation reservation(UUID templateId){return reservationsByTemplateId.getOrDefault(templateId,Reservation.empty());}
        public Reservation reservation(Product product){
            if(product.sourceTemplateId()!=null) return reservation(product.sourceTemplateId());
            return reservationsByTemplateId.values().stream().filter(r->r.template()!=null&&r.template().name().equalsIgnoreCase(product.name())).findFirst().orElse(Reservation.empty());
        }
    }
    public record Reservation(ProductTemplate template,BigDecimal quantity,int usageCount,List<InventoryReservationDetail> details,String warning) {
        static Reservation empty(){return new Reservation(null,BigDecimal.ZERO,0,List.of(),null);}
    }
    private static final class MutableReservation {
        ProductTemplate template; BigDecimal quantity=BigDecimal.ZERO; int usageCount; String warning; final List<InventoryReservationDetail> details=new ArrayList<>();
    }

    @Transactional(readOnly=true)
    public Snapshot snapshot(UUID excludedMealPlanId) {
        UUID userId=currentUser.currentUserId();
        Map<UUID,InventoryItem> inventory=new HashMap<>();
        inventoryPort.findAllByUserId(userId).forEach(item->inventory.put(item.product().id(),item));
        Map<UUID,MutableReservation> mutable=new LinkedHashMap<>();
        for(MealPlan plan:mealPlanPort.findAllByUserId(userId)) {
            if(Objects.equals(plan.id(),excludedMealPlanId)) continue;
            for(PlannedRecipe planned:plan.recipes()) {
                if(planned.status()!=PlannedRecipeStatus.PLANNED) continue;
                Map<UUID,MutableReservation> occurrence=new LinkedHashMap<>();
                for(RecipeIngredient ingredient:planned.recipe().ingredients()) {
                    ProductTemplate template=ingredient.productTemplate();
                    if(template.defaultTrackingMode()==InventoryTrackingMode.UNTRACKED)continue;
                    MutableReservation value=occurrence.computeIfAbsent(template.id(),ignored->new MutableReservation()); value.template=template;
                    if(template.defaultTrackingMode()==InventoryTrackingMode.PRESENCE) continue;
                    BigDecimal scaled=ingredient.quantity().multiply(BigDecimal.valueOf(planned.portions()));
                    NormalizedRecipeQuantity normalized=normalizer.normalize(scaled,ingredient.unit(),template.defaultUnit());
                    if(!normalized.resolved()) { value.warning=normalized.warning(); continue; }
                    value.quantity=value.quantity.add(normalized.quantity());
                }
                occurrence.forEach((templateId,value)->{MutableReservation total=mutable.computeIfAbsent(templateId,ignored->new MutableReservation());total.template=value.template;total.usageCount++;if(value.warning!=null)total.warning=value.warning;total.quantity=total.quantity.add(value.quantity);total.details.add(new InventoryReservationDetail(plan.id(),plan.name(),planned.id(),planned.recipe().id(),planned.recipe().name(),planned.portions(),value.warning==null?value.quantity:null,value.template.defaultUnit()));});
            }
        }
        Map<UUID,Reservation> reservations=new LinkedHashMap<>();
        mutable.forEach((id,value)->reservations.put(id,new Reservation(value.template,value.quantity,value.usageCount,List.copyOf(value.details),value.warning)));
        return new Snapshot(Map.copyOf(inventory),Map.copyOf(reservations));
    }

    public TemplateAvailability forTemplate(Snapshot snapshot,ProductTemplate template,Product product,InventoryTrackingMode mode) {
        InventoryItem item=product==null?null:snapshot.inventoryByProductId().get(product.id());
        Reservation reservation=snapshot.reservation(template.id());
        if(mode==InventoryTrackingMode.UNTRACKED)return new TemplateAvailability(template,product,mode,null,null,null,null,0,List.of());
        if(mode==InventoryTrackingMode.PRESENCE) return new TemplateAvailability(template,product,mode,null,null,null,null,
                reservation.usageCount(),reservation.details());
        BigDecimal physical=item==null||item.quantity()==null?BigDecimal.ZERO:item.quantity();
        BigDecimal reserved=reservation.quantity(); BigDecimal available=physical.subtract(reserved).max(BigDecimal.ZERO);
        BigDecimal shortfall=reserved.subtract(physical).max(BigDecimal.ZERO);
        return new TemplateAvailability(template,product,mode,physical,reserved,available,shortfall,reservation.usageCount(),reservation.details());
    }

    @Transactional(readOnly=true)
    public List<InventoryAvailability> inventoryAvailability() {
        Snapshot snapshot=snapshot(null); List<InventoryAvailability> result=new ArrayList<>();
        for(InventoryItem item:snapshot.inventoryByProductId().values()) {
            Product product=item.product(); UUID templateId=product.sourceTemplateId();
            if(product.inventoryTrackingMode()==InventoryTrackingMode.UNTRACKED)continue;
            Reservation reservation=templateId==null?snapshot.reservation(product):snapshot.reservation(templateId);
            if(product.inventoryTrackingMode()==InventoryTrackingMode.PRESENCE) {
                result.add(new InventoryAvailability(item,null,null,null,null,reservation.usageCount(),reservation.details()));
            } else {
                BigDecimal physical=item.quantity(),reserved=reservation.quantity(),available=physical.subtract(reserved).max(BigDecimal.ZERO);
                result.add(new InventoryAvailability(item,physical,reserved,available,reserved.subtract(physical).max(BigDecimal.ZERO),reservation.usageCount(),reservation.details()));
            }
        }
        return result.stream().sorted(Comparator.comparing(a->a.inventoryItem().product().name(),String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
