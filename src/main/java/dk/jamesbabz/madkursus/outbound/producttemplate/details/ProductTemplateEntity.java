package dk.jamesbabz.madkursus.outbound.producttemplate.details;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import dk.jamesbabz.madkursus.service.models.ProductCategory;
import dk.jamesbabz.madkursus.service.models.Unit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="product_templates") @Getter @NoArgsConstructor(access=lombok.AccessLevel.PROTECTED)
public class ProductTemplateEntity {
    @Id private UUID id;
    private String name;
    private String normalizedName;
    @Enumerated(EnumType.STRING) private ProductCategory category;
    @Enumerated(EnumType.STRING) private Unit defaultUnit;
    private boolean common;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="product_template_aliases", joinColumns=@JoinColumn(name="template_id"))
    @Column(name="alias")
    private Set<String> aliases = new LinkedHashSet<>();
}
