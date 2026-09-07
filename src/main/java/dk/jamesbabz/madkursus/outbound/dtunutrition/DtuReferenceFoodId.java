package dk.jamesbabz.madkursus.outbound.dtunutrition;

import java.io.Serializable;
import java.util.Objects;

public class DtuReferenceFoodId implements Serializable {
    private String datasetVersion;
    private String foodId;

    public DtuReferenceFoodId() { }
    public DtuReferenceFoodId(String datasetVersion, String foodId) {
        this.datasetVersion = datasetVersion;
        this.foodId = foodId;
    }
    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof DtuReferenceFoodId other)) return false;
        return Objects.equals(datasetVersion, other.datasetVersion) && Objects.equals(foodId, other.foodId);
    }
    @Override public int hashCode() { return Objects.hash(datasetVersion, foodId); }
}
