package dk.jamesbabz.madkursus.service.models;

import java.util.UUID;

public record CookingProcessParameter(UUID id, String key, String label, CookingProcessParameterType type,
        boolean required, CookingProcessValue defaultValue, RecipeUnit unit, int sortOrder,
        CookingProcessParameterSource source, CookingProcessDerivedRule derivedRule, String derivedFrom) {
    public CookingProcessParameter(UUID id,String key,String label,CookingProcessParameterType type,boolean required,
            CookingProcessValue defaultValue,RecipeUnit unit,int sortOrder) {
        this(id,key,label,type,required,defaultValue,unit,sortOrder,
                required && defaultValue == null ? CookingProcessParameterSource.INPUT : CookingProcessParameterSource.OVERRIDEABLE_DEFAULT,null,null);
    }
    public boolean normalInput(){ return source == CookingProcessParameterSource.INPUT; }
    public boolean overrideable(){ return source == CookingProcessParameterSource.OVERRIDEABLE_DEFAULT || source == CookingProcessParameterSource.DERIVED; }
}
