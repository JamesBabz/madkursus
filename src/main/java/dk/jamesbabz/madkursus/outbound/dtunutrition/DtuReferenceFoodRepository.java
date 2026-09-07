package dk.jamesbabz.madkursus.outbound.dtunutrition;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DtuReferenceFoodRepository extends JpaRepository<DtuReferenceFoodEntity, DtuReferenceFoodId> {
    Optional<DtuReferenceFoodEntity> findByDatasetVersionAndFoodId(String datasetVersion, String foodId);

    @Query("""
        select f from DtuReferenceFoodEntity f
        where f.datasetVersion = :version
          and (:search = '' or lower(f.danishName) like concat('%', :search, '%') or f.foodId = :search)
        order by f.danishName
        """)
    List<DtuReferenceFoodEntity> search(@Param("version") String version, @Param("search") String search);

    @Query("select distinct f.datasetVersion from DtuReferenceFoodEntity f order by f.datasetVersion desc")
    List<String> versions();
}
