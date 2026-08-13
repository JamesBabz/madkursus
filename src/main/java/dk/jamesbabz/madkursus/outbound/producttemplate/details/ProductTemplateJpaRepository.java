package dk.jamesbabz.madkursus.outbound.producttemplate.details;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductTemplateJpaRepository extends JpaRepository<ProductTemplateEntity, UUID> {
    @Query("""
        select distinct t from ProductTemplateEntity t left join t.aliases a
        where (:common is null or t.common = :common)
          and (:search = '' or lower(t.name) like concat('%', :search, '%') or lower(a) like concat('%', :search, '%'))
        order by t.name
        """)
    List<ProductTemplateEntity> search(@Param("search") String search, @Param("common") Boolean common);
}
