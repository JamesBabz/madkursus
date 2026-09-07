package dk.jamesbabz.madkursus.service.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.jamesbabz.madkursus.outbound.dtunutrition.DtuReferenceFoodEntity;
import dk.jamesbabz.madkursus.outbound.dtunutrition.DtuReferenceFoodRepository;
import dk.jamesbabz.madkursus.outbound.dtunutrition.ProductTemplateDtuMappingEntity;
import dk.jamesbabz.madkursus.outbound.dtunutrition.ProductTemplateDtuMappingRepository;
import dk.jamesbabz.madkursus.service.exceptions.InvalidInputException;
import dk.jamesbabz.madkursus.service.exceptions.ResourceNotFoundException;
import dk.jamesbabz.madkursus.service.models.NutritionData;
import dk.jamesbabz.madkursus.service.models.ProductTemplate;
import dk.jamesbabz.madkursus.service.models.RecipeUnit;
import dk.jamesbabz.madkursus.service.ports.ProductTemplatePort;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DtuNutritionAdminService {
    public enum MatchClassification { EXACT, HIGH_CONFIDENCE, REVIEW_REQUIRED, NO_MATCH }
    public enum Resolution { NONE, AUTO_EQUIVALENT_CARBOHYDRATE }
    public enum MappingStatus { UNMAPPED, APPROVED, REQUIRES_REVIEW }
    public record Food(String datasetVersion, String foodId, String danishName,
                       BigDecimal carbohydrateGrams, String stateDescription,
                       LocalDate sourceDate, String sourceUrl) { }
    public record Candidate(Food food, MatchClassification classification, int score, String reason) { }
    public record Suggestion(MatchClassification classification, List<Candidate> candidates, String reason,
                             Resolution resolution, Food representative, int equivalentCandidateCount,
                             BigDecimal carbohydrateSpread) { }
    public record CompatibleCandidateSummary(int count,BigDecimal minimum,BigDecimal maximum,
                                             BigDecimal mean,BigDecimal spread) { }
    public record ReviewCandidates(Suggestion suggestion,List<Candidate> automaticCandidates,
                                   List<Candidate> broadSearchCandidates,
                                   CompatibleCandidateSummary compatibleCandidateSummary) { }
    public record Mapping(MappingStatus status, Food food, BigDecimal approvedCarbohydrateGrams) { }
    public record Approval(UUID productTemplateId, String datasetVersion, String foodId) { }
    public record ImportResult(String datasetVersion, int importedFoods, boolean alreadyImported) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dataset(String datasetVersion, LocalDate sourceDate, String sourceUrl, List<DatasetFood> foods) { }
    public record DatasetFood(String foodId, String danishName, BigDecimal carbohydrateGrams,
                              String stateDescription) { }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9æøå]+", Pattern.CASE_INSENSITIVE);
    /** Deliberately small, identity-preserving vocabulary. Product-specific relations belong in aliases. */
    private static final Map<String,String> SYNONYMS=Map.ofEntries(
            Map.entry("barbeque","barbecue"),Map.entry("bbq","barbecue"),
            Map.entry("brystkoed","bryst"),Map.entry("soja","soya"),
            Map.entry("daase","canned"),Map.entry("konserves","canned"),
            Map.entry("ande","and"),Map.entry("hakkede","ground"),Map.entry("hakket","ground"),
            Map.entry("fars","ground"),Map.entry("flaaet","peeled"),Map.entry("stoedt","ground"));
    private static final Set<String> COMPOUND_PARTS=Set.of("barbecue","sauce","bryst","koed","fars","olie","mel","broed","boenne","noed","ekstrakt","pure","juice","bouillon","floede","aeg");
    private static final Set<String> IGNORED=Set.of("og","med","uden","af","til","i","paa","uspecificeret","uspec");
    private static final Set<String> FORM_WORDS=Set.of("ground","peeled","hel","whole","pulver","filet","bryst","blade","knold","revet","skiver","tern","stang","piske");
    private static final Set<String> DERIVED_TYPES=Set.of("bouillon","ekstrakt","sauce","juice","pure","suppe","pizza","wokret","faerdigret","takeaway","spegepoelse","poelse","pesto","ketchup","olie","mel");
    private static final Set<String> QUALIFIERS=Set.of("sort","hvid","groen","gul","roed","dansk","importeret","oekologisk","konventionel");
    private final DtuReferenceFoodRepository foods;
    private final ProductTemplateDtuMappingRepository mappings;
    private final ProductTemplatePort templates;
    private final ObjectMapper mapper;
    private volatile String cachedVersion;
    private volatile List<DtuReferenceFoodEntity> cachedCatalog;
    private volatile Map<UUID,ProductTemplateDtuMappingEntity> cachedMappings;

    public DtuNutritionAdminService(DtuReferenceFoodRepository foods,
                                    ProductTemplateDtuMappingRepository mappings,
                                    ProductTemplatePort templates, ObjectMapper mapper) {
        this.foods = foods;
        this.mappings = mappings;
        this.templates = templates;
        this.mapper = mapper;
    }

    @Transactional
    public ImportResult importBundled() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("nutrition/dtu-frida-5.5.json")) {
            if (input == null) throw new IllegalStateException("Bundled DTU dataset is missing");
            return importDataset(mapper.readValue(input, Dataset.class));
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException runtime) throw runtime;
            throw new IllegalStateException("Cannot import bundled DTU reference dataset", exception);
        }
    }

    @Transactional
    public ImportResult importDataset(Dataset dataset) {
        validate(dataset);
        boolean existed = foods.versions().contains(dataset.datasetVersion());
        if (existed) return new ImportResult(dataset.datasetVersion(), 0, true);
        List<DtuReferenceFoodEntity> entities = dataset.foods().stream()
                .map(food -> new DtuReferenceFoodEntity(dataset.datasetVersion(), food.foodId(),
                        food.danishName().trim(), normalize(food.danishName()), food.carbohydrateGrams(),
                        food.stateDescription(), dataset.sourceDate(), dataset.sourceUrl()))
                .toList();
        foods.saveAll(entities);
        cachedVersion=dataset.datasetVersion();
        cachedCatalog=entities;
        return new ImportResult(dataset.datasetVersion(), entities.size(), false);
    }

    public List<Food> search(String search) {
        String version = latestVersion();
        if (version == null) return List.of();
        String query=normalize(search);
        Set<String> queryTokens=searchTokens(search);
        return catalog().stream().filter(food->query.isEmpty()||normalize(food.getDanishName()).contains(query)
                        ||food.getFoodId().equals(query)||!queryTokens.isEmpty()&&searchTokens(food.getDanishName()).containsAll(queryTokens))
                .sorted(Comparator.comparing(DtuReferenceFoodEntity::getDanishName)).limit(50).map(this::food).toList();
    }

    public Suggestion suggest(ProductTemplate template) {
        String version = latestVersion();
        if (version == null) return noSuggestion("DTU-kataloget er ikke importeret");
        List<Signature> sources=new ArrayList<>();
        sources.add(signature(template.name(),template.category().name(),true));
        template.aliases().forEach(alias->sources.add(signature(alias,template.category().name(),false)));
        List<Scored> ranked=catalog().stream()
                .map(food->eligibleScore(sources,food))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingDouble(Scored::value).reversed()
                        .thenComparing(value->value.food().getDanishName())).toList();
        if(ranked.isEmpty())return noSuggestion("Intet godt DTU-forslag");
        Scored best=ranked.getFirst();
        double margin=ranked.size()==1?1:best.value()-ranked.get(1).value();
        boolean literal=normalize(template.name()).equals(normalize(best.food().getDanishName()));
        MatchClassification classification;
        if(literal&&!best.conflict())classification=MatchClassification.EXACT;
        else if(best.value()>=.84&&margin>=.08&&!best.conflict())classification=MatchClassification.HIGH_CONFIDENCE;
        else classification=MatchClassification.REVIEW_REQUIRED;
        EquivalentResolution equivalent=equivalentResolution(ranked);
        int candidateLimit=equivalent.resolution()==Resolution.AUTO_EQUIVALENT_CARBOHYDRATE?Math.min(5,equivalent.candidateCount()):
                classification==MatchClassification.EXACT||classification==MatchClassification.HIGH_CONFIDENCE?1:5;
        List<Candidate> candidates=ranked.stream().limit(candidateLimit)
                .map(value->new Candidate(food(value.food()),value==best?classification:MatchClassification.REVIEW_REQUIRED,
                        score100(value.value()),value.reason())).toList();
        String reason=switch(classification){case EXACT->"Navnet matcher DTU-navnet præcist";case HIGH_CONFIDENCE->"Samme kernefødevare og kompatibel produktform";case REVIEW_REQUIRED->"Semantisk kompatible varianter kræver gennemgang";case NO_MATCH->"Intet godt DTU-forslag";};
        if(equivalent.resolution()==Resolution.AUTO_EQUIVALENT_CARBOHYDRATE)
            reason="Kulhydratværdien kan auto-vælges fra "+equivalent.candidateCount()+" semantisk tilsvarende DTU-poster";
        return new Suggestion(classification,candidates,reason,equivalent.resolution(),
                equivalent.representative()==null?null:food(equivalent.representative()),
                equivalent.candidateCount(),equivalent.spread());
    }

    /** Broader, read-only retrieval for an explicit external review; production eligibility is not changed. */
    public ReviewCandidates reviewCandidates(ProductTemplate template){
        Suggestion suggestion=suggest(template);
        List<Signature> sources=new ArrayList<>();sources.add(signature(template.name(),template.category().name(),true));
        template.aliases().forEach(alias->sources.add(signature(alias,template.category().name(),false)));
        List<Candidate> automatic=catalog().stream().map(food->eligibleScore(sources,food)).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingDouble(Scored::value).reversed().thenComparing(value->foodIdOrder(value.food())))
                .limit(10).map(value->new Candidate(food(value.food()),classification(value.value()),score100(value.value()),value.reason())).toList();
        Set<String> automaticIds=automatic.stream().map(candidate->candidate.food().foodId()).collect(java.util.stream.Collectors.toSet());
        Set<String> query=new LinkedHashSet<>();sources.forEach(source->{query.addAll(source.identity());query.addAll(source.forms());});
        List<Candidate> broad=catalog().stream().filter(food->!automaticIds.contains(food.getFoodId()))
                .map(food->broadCandidate(query,food)).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparing(candidate->foodIdOrder(findEntity(candidate.food()))))
                .limit(Math.max(0,10-automatic.size())).toList();
        List<Scored> ranked=catalog().stream().map(food->eligibleScore(sources,food)).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingDouble(Scored::value).reversed().thenComparing(value->foodIdOrder(value.food()))).toList();
        return new ReviewCandidates(suggestion,automatic,broad,compatibleSummary(ranked));
    }

    private static CompatibleCandidateSummary compatibleSummary(List<Scored> ranked){
        if(ranked.isEmpty())return null;Signature first=signature(ranked.getFirst().food().getDanishName(),null,true);
        List<BigDecimal> values=ranked.stream().filter(value->equivalentIdentity(first,signature(value.food().getDanishName(),null,true)))
                .map(value->value.food().getCarbohydrateGrams()).toList();if(values.isEmpty())return null;
        BigDecimal min=values.stream().min(BigDecimal::compareTo).orElseThrow(),max=values.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal mean=values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),4,RoundingMode.HALF_UP).stripTrailingZeros();
        return new CompatibleCandidateSummary(values.size(),min,max,mean,max.subtract(min));
    }

    private java.util.Optional<Candidate> broadCandidate(Set<String> query,DtuReferenceFoodEntity entity){
        Set<String> target=searchTokens(entity.getDanishName());long shared=query.stream().filter(target::contains).count();
        if(shared==0)return java.util.Optional.empty();double coverage=(double)shared/Math.max(1,query.size());
        if(coverage<.5)return java.util.Optional.empty();int score=score100(.55*coverage+.45*((double)shared/Math.max(1,target.size())));
        return java.util.Optional.of(new Candidate(food(entity),MatchClassification.REVIEW_REQUIRED,score,
                "Bred tokenoverlap; kandidaten bestod ikke den automatiske semantiske matcher"));
    }

    private DtuReferenceFoodEntity findEntity(Food value){return catalog().stream().filter(food->food.getFoodId().equals(value.foodId())).findFirst().orElseThrow();}
    private static MatchClassification classification(double score){return score>=.92?MatchClassification.EXACT:score>=.84?MatchClassification.HIGH_CONFIDENCE:MatchClassification.REVIEW_REQUIRED;}

    private Suggestion noSuggestion(String reason){return new Suggestion(MatchClassification.NO_MATCH,List.of(),reason,Resolution.NONE,null,0,null);}

    public Mapping mapping(UUID productTemplateId) {
        return mappingEntities().entrySet().stream().filter(entry->entry.getKey().equals(productTemplateId)).map(Map.Entry::getValue).findFirst().map(mapping -> {
            DtuReferenceFoodEntity approved = foods.findByDatasetVersionAndFoodId(
                    mapping.getDatasetVersion(), mapping.getFoodId()).orElseThrow();
            DtuReferenceFoodEntity current = latestForFoodId(mapping.getFoodId());
            boolean changed = current != null && current.getCarbohydrateGrams()
                    .compareTo(mapping.getApprovedCarbohydrateGrams()) != 0;
            return new Mapping(changed ? MappingStatus.REQUIRES_REVIEW : MappingStatus.APPROVED,
                    food(changed ? current : approved), mapping.getApprovedCarbohydrateGrams());
        }).orElse(new Mapping(MappingStatus.UNMAPPED, null, null));
    }

    @Transactional
    public NutritionAdminService.Entry approve(Approval approval, NutritionAdminService nutrition) {
        return approve(approval,nutrition,null,null);
    }

    public NutritionAdminService.Entry approveReviewed(Approval approval,NutritionAdminService nutrition,String reason){
        return approve(approval,nutrition,null,"AI_REVIEW_MATCH_HIGH"+(reason==null||reason.isBlank()?"":"; "+reason));
    }

    private NutritionAdminService.Entry approve(Approval approval, NutritionAdminService nutrition, Suggestion automatic,String reviewedNote) {
        ProductTemplate template = templates.findById(approval.productTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Product template", approval.productTemplateId()));
        DtuReferenceFoodEntity reference = foods.findByDatasetVersionAndFoodId(
                        approval.datasetVersion(), approval.foodId())
                .orElseThrow(() -> new InvalidInputException("DTU reference food does not exist"));
        String note=reference.getStateDescription();
        if(automatic!=null)note="AUTO_EQUIVALENT_CARBOHYDRATE; candidateCount="+automatic.equivalentCandidateCount()
                +"; spread="+automatic.carbohydrateSpread()+" g/100 g"
                +(note==null||note.isBlank()?"":"; "+note);
        if(reviewedNote!=null)note=reviewedNote+(note==null||note.isBlank()?"":"; "+note);
        NutritionData data = new NutritionData(reference.getCarbohydrateGrams(), new BigDecimal("100"),
                RecipeUnit.GRAM, "DTU Frida: " + reference.getDanishName(), "DTU",
                reference.getFoodId(), reference.getDatasetVersion(), reference.getSourceUrl(),
                note);
        NutritionAdminService.Entry updated = nutrition.update(template.id(), data);
        var saved=mappings.save(new ProductTemplateDtuMappingEntity(template.id(), reference.getDatasetVersion(),
                reference.getFoodId(), reference.getCarbohydrateGrams()));
        Map<UUID,ProductTemplateDtuMappingEntity> mappingCacheUpdated=new LinkedHashMap<>(mappingEntities());mappingCacheUpdated.put(template.id(),saved);cachedMappings=Map.copyOf(mappingCacheUpdated);
        return updated;
    }

    @Transactional
    public List<NutritionAdminService.Entry> approveAll(Collection<Approval> approvals,
                                                         NutritionAdminService nutrition) {
        if (approvals == null || approvals.isEmpty()) throw new InvalidInputException("Select at least one reviewed match");
        for(Approval approval:approvals){ProductTemplate template=templates.findById(approval.productTemplateId()).orElseThrow(()->new ResourceNotFoundException("Product template",approval.productTemplateId()));Suggestion suggestion=suggest(template);boolean ready=(suggestion.classification()==MatchClassification.EXACT||suggestion.classification()==MatchClassification.HIGH_CONFIDENCE)&&suggestion.candidates().size()==1&&suggestion.candidates().getFirst().food().datasetVersion().equals(approval.datasetVersion())&&suggestion.candidates().getFirst().food().foodId().equals(approval.foodId());if(!ready)throw new InvalidInputException("Bulk approval only accepts the reviewed EXACT or HIGH_CONFIDENCE proposal");}
        return approvals.stream().map(approval -> approve(approval, nutrition)).toList();
    }

    @Transactional
    public List<NutritionAdminService.Entry> approveSafe(NutritionAdminService nutrition) {
        List<SafeApproval> safe=templates.search("",null).stream().filter(template->template.nutritionData()==null)
                .map(template->new SafeApproval(template,suggest(template)))
                .filter(value->value.suggestion().resolution()==Resolution.AUTO_EQUIVALENT_CARBOHYDRATE).toList();
        return safe.stream().map(value->{Food representative=value.suggestion().representative();return approve(
                new Approval(value.template().id(),representative.datasetVersion(),representative.foodId()),nutrition,value.suggestion(),null);}).toList();
    }

    public long catalogCount() { return foods.count(); }
    public String latestVersion() {String value=cachedVersion;if(value==null){value=foods.versions().stream().findFirst().orElse(null);cachedVersion=value;}return value;}

    private List<DtuReferenceFoodEntity> catalog(){List<DtuReferenceFoodEntity> value=cachedCatalog;if(value==null){String version=latestVersion();value=version==null?List.of():List.copyOf(foods.search(version,""));cachedCatalog=value;}return value;}
    private Map<UUID,ProductTemplateDtuMappingEntity> mappingEntities(){Map<UUID,ProductTemplateDtuMappingEntity> value=cachedMappings;if(value==null){Map<UUID,ProductTemplateDtuMappingEntity> loaded=new LinkedHashMap<>();mappings.findAll().forEach(item->loaded.put(item.getProductTemplateId(),item));value=Map.copyOf(loaded);cachedMappings=value;}return value;}

    private record Signature(String core,Set<String> identity,Set<String> forms,Set<String> states,
                             Set<String> preservation,String derivedType,boolean primary,boolean preservedProduct){}
    private record Scored(DtuReferenceFoodEntity food,double value,boolean conflict,
                          boolean primaryIdentity,String reason){}
    private record EquivalentResolution(Resolution resolution,DtuReferenceFoodEntity representative,
                                        int candidateCount,BigDecimal spread){}
    private record SafeApproval(ProductTemplate template,Suggestion suggestion){}

    private static EquivalentResolution equivalentResolution(List<Scored> ranked){
        if(ranked.size()<2)return new EquivalentResolution(Resolution.NONE,null,0,null);
        Signature first=signature(ranked.getFirst().food().getDanishName(),null,true);
        List<Scored> group=ranked.stream().filter(value->equivalentIdentity(first,
                signature(value.food().getDanishName(),null,true))).toList();
        if(group.size()<2)return new EquivalentResolution(Resolution.NONE,null,0,null);
        BigDecimal min=group.stream().map(value->value.food().getCarbohydrateGrams()).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max=group.stream().map(value->value.food().getCarbohydrateGrams()).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal spread=max.subtract(min);
        if(spread.compareTo(new BigDecimal("2.0"))>0)return new EquivalentResolution(Resolution.NONE,null,group.size(),spread);
        BigDecimal mean=group.stream().map(value->value.food().getCarbohydrateGrams()).reduce(BigDecimal.ZERO,BigDecimal::add)
                .divide(BigDecimal.valueOf(group.size()),8,RoundingMode.HALF_UP);
        DtuReferenceFoodEntity representative=group.stream().map(Scored::food).min(Comparator
                .comparing((DtuReferenceFoodEntity food)->food.getCarbohydrateGrams().subtract(mean).abs())
                .thenComparing(DtuNutritionAdminService::foodIdOrder)).orElseThrow();
        return new EquivalentResolution(Resolution.AUTO_EQUIVALENT_CARBOHYDRATE,representative,group.size(),spread);
    }

    private static boolean equivalentIdentity(Signature left,Signature right){
        return java.util.Objects.equals(left.core(),right.core())
                &&java.util.Objects.equals(left.derivedType(),right.derivedType())
                &&equivalenceIdentity(left).equals(equivalenceIdentity(right))
                &&left.forms().equals(right.forms())
                &&!stateConflict(left.states(),right.states())
                &&left.preservation().equals(right.preservation());
    }

    private static Set<String> equivalenceIdentity(Signature signature){
        Set<String> result=new LinkedHashSet<>(signature.identity());
        result.removeAll(QUALIFIERS);
        return result;
    }

    private static String foodIdOrder(DtuReferenceFoodEntity food){
        try{return String.format("%020d",Long.parseLong(food.getFoodId()));}catch(NumberFormatException ignored){return food.getFoodId();}
    }

    private static java.util.Optional<Scored> eligibleScore(List<Signature> sources,DtuReferenceFoodEntity food){
        Signature target=signature(food.getDanishName(),null,true);
        Set<String> declaredForms=new LinkedHashSet<>(),declaredStates=new LinkedHashSet<>();
        sources.forEach(source->{declaredForms.addAll(source.forms());declaredStates.addAll(source.states());});
        if(formConflict(declaredForms,target)||stateConflict(declaredStates,target.states()))return java.util.Optional.empty();
        Scored best=null;
        for(Signature source:sources){
            if(!eligible(source,target))continue;
            boolean formConflict=formConflict(source,target);
            boolean stateConflict=stateConflict(source.states(),target.states());
            if(formConflict||stateConflict)continue;
            long shared=source.identity().stream().filter(target.identity()::contains).count();
            double identityCoverage=source.identity().isEmpty()?0:(double)shared/source.identity().size();
            double identityPrecision=target.identity().isEmpty()?0:(double)shared/target.identity().size();
            double value=.68*identityCoverage+.22*identityPrecision;
            if(source.forms().isEmpty()||target.forms().containsAll(source.forms())||tomatoFormCompatible(source,target))value+=.06;
            if(preservationCompatible(source,target))value+=.08;
            else if(!source.preservedProduct()&&target.preservation().contains("canned"))value-=.15;
            if(!source.primary())value-=.08;
            if(target.states().equals(Set.of("raw"))&&source.states().isEmpty())value+=.04;
            else if(source.states().isEmpty()&&target.states().stream().anyMatch(Set.of("boiled","fried","dried","frozen")::contains))value-=.18;
            value=Math.max(0,Math.min(1,value));
            String why="Samme kernefødevare: "+source.core();
            if(target.derivedType()!=null)why+="; produkttype "+target.derivedType();
            if(!source.forms().isEmpty())why+="; kompatibel form "+String.join(", ",source.forms());
            if(!source.preservation().isEmpty())why+="; kompatibel bevaring";
            Scored scored=new Scored(food,value,false,source.primary(),why);
            if(best==null||scored.value()>best.value())best=scored;
        }
        return java.util.Optional.ofNullable(best);
    }

    private static boolean eligible(Signature source,Signature target){
        if(source.core()==null||target.core()==null||!sameToken(source.core(),target.core()))return false;
        if(source.derivedType()!=null||target.derivedType()!=null){
            if(!java.util.Objects.equals(source.derivedType(),target.derivedType()))return false;
            Set<String> sourceQualifiers=new LinkedHashSet<>(source.identity());sourceQualifiers.remove(source.derivedType());
            Set<String> targetQualifiers=new LinkedHashSet<>(target.identity());targetQualifiers.remove(target.derivedType());
            if(!sourceQualifiers.isEmpty()&&java.util.Collections.disjoint(sourceQualifiers,targetQualifiers))return false;
        }
        if(source.identity().stream().noneMatch(target.identity()::contains))return false;
        if(target.derivedType()!=null&&!target.derivedType().equals(source.derivedType()))return false;
        return true;
    }

    private static boolean formConflict(Signature source,Signature target){
        return formConflict(source.forms(),target)
                ||source.forms().contains("ground")&&!target.forms().contains("ground")&&!tomatoFormCompatible(source,target);
    }

    private static boolean formConflict(Set<String> sourceForms,Signature target){
        if(sourceForms.contains("ground")&&target.forms().stream().anyMatch(Set.of("whole","stang","filet","bryst")::contains))return true;
        if(sourceForms.contains("bryst")&&!target.forms().contains("bryst"))return true;
        if(sourceForms.contains("filet")&&!target.forms().contains("filet"))return true;
        if(sourceForms.contains("piske")&&!target.forms().contains("piske"))return true;
        return false;
    }

    private static boolean tomatoFormCompatible(Signature source,Signature target){
        return sameToken(source.core(),"tomat")&&source.forms().contains("ground")
                &&target.forms().contains("peeled")&&target.preservation().contains("canned");
    }

    private static boolean preservationCompatible(Signature source,Signature target){
        if(source.preservedProduct())return target.preservation().contains("canned");
        return !source.preservation().isEmpty()&&target.preservation().containsAll(source.preservation());
    }

    private static Signature signature(String value,String category,boolean primary){
        List<String> tokens=tokens(value);
        Set<String> forms=new LinkedHashSet<>(),states=stateWords(value),preservation=new LinkedHashSet<>(),identity=new LinkedHashSet<>();
        String derived=null;
        for(String token:tokens){
            String canonical=SYNONYMS.getOrDefault(token,token);
            if(canonical.equals("canned")){preservation.add("canned");continue;}
            if(FORM_WORDS.contains(canonical)){forms.add(canonical.equals("hel")?"whole":canonical);continue;}
            if(!stateWords(canonical).isEmpty()||IGNORED.contains(canonical))continue;
            if(DERIVED_TYPES.contains(canonical)&&derived==null)derived=canonical;
            identity.add(canonicalToken(canonical));
        }
        String core=derived!=null?derived:identity.stream().filter(token->!DERIVED_TYPES.contains(token)&&!QUALIFIERS.contains(token)).findFirst().orElse(null);
        if(identity.contains("aeg"))core="aeg";
        if("salt".equals(core)&&identity.size()>1)core=identity.stream().filter(token->!token.equals("salt")).findFirst().orElse(core);
        if(derived!=null&&derived.equals("sauce")&&identity.contains("barbecue"))core="barbecue-sauce";
        if(core!=null&&core.equals("barbecue-sauce")){identity.add("barbecue-sauce");derived="barbecue-sauce";}
        boolean preserved="PRESERVED".equals(category)||preservation.contains("canned");
        return new Signature(core,identity,forms,states,preservation,derived,primary,preserved);
    }

    private static List<String> tokens(String value){
        List<String> result=new ArrayList<>();
        for(String word:normalize(value).split(" ")){
            if(word.length()<2||word.chars().allMatch(Character::isDigit))continue;
            for(String part:splitCompound(word))result.add(SYNONYMS.getOrDefault(part,part));
        }
        return result;
    }

    private static Set<String> searchTokens(String value){
        Set<String> result=new LinkedHashSet<>();
        for(String token:tokens(value)){
            if(IGNORED.contains(token)||!stateWords(token).isEmpty())continue;
            result.add(canonicalToken(token));
        }
        return result;
    }

    private static String canonicalToken(String word){
        if(word.length()>5&&word.endsWith("dder"))return word.substring(0,word.length()-3);
        if(word.length()>5&&word.endsWith("er"))return word.substring(0,word.length()-2);
        if(word.length()>5&&word.endsWith("e"))return word.substring(0,word.length()-1);
        return word;
    }

    private static boolean sameToken(String left,String right){return canonicalToken(left).equals(canonicalToken(right));}
    private static int score100(double value){return (int)Math.round(value*100);}
    private static boolean stateConflict(Set<String>a,Set<String>b){if(a.isEmpty())return false;Set<String> hard=Set.of("raw","fresh","boiled","fried","dried","frozen","canned");for(String x:a)if(hard.contains(x)&&b.stream().anyMatch(y->hard.contains(y)&&!y.equals(x)))return true;if(a.contains("water")&&b.contains("oil")||a.contains("oil")&&b.contains("water"))return true;return false;}
    private static Set<String> stateWords(String value){Set<String> result=new LinkedHashSet<>();for(String word:normalize(value).split(" ")){if(Set.of("frossen","frosne","dybfrost").contains(word))result.add("frozen");if(Set.of("toerret","toerrede").contains(word))result.add("dried");if(Set.of("frisk","friske").contains(word))result.add("fresh");if(Set.of("raa","raat").contains(word))result.add("raw");if(Set.of("kogt","kogte").contains(word))result.add("boiled");if(Set.of("stegt","stegte","friturestegt").contains(word))result.add("fried");if(Set.of("konserves","daase").contains(word))result.add("canned");if(word.equals("vand"))result.add("water");}return result;}

    private static List<String> splitCompound(String word){for(String part:COMPOUND_PARTS){if(word.length()>part.length()+2&&word.endsWith(part))return List.of(word.substring(0,word.length()-part.length()),part);if(word.length()>part.length()+2&&word.startsWith(part))return List.of(part,word.substring(part.length()));}return List.of(word);}

    private DtuReferenceFoodEntity latestForFoodId(String foodId) {
        for (String version : foods.versions()) {
            var result = foods.findByDatasetVersionAndFoodId(version, foodId);
            if (result.isPresent()) return result.get();
        }
        return null;
    }

    private Food food(DtuReferenceFoodEntity entity) {
        return new Food(entity.getDatasetVersion(), entity.getFoodId(), entity.getDanishName(),
                entity.getCarbohydrateGrams(), entity.getStateDescription(), entity.getSourceDate(),
                entity.getSourceUrl());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String canonical=value.toLowerCase(Locale.forLanguageTag("da")).replace("æ","ae").replace("ø","oe").replace("å","aa");
        String decomposed = Normalizer.normalize(canonical, Normalizer.Form.NFKD);
        return NON_ALNUM.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ").trim()
                .replaceAll("\\s+", " ");
    }

    private static void validate(Dataset dataset) {
        if (dataset == null || dataset.datasetVersion() == null || dataset.datasetVersion().isBlank()
                || dataset.sourceDate() == null || dataset.sourceUrl() == null || dataset.sourceUrl().isBlank()
                || dataset.foods() == null || dataset.foods().isEmpty())
            throw new InvalidInputException("DTU dataset metadata and foods are required");
        Set<String> ids = new LinkedHashSet<>();
        for (DatasetFood food : dataset.foods()) {
            if (food.foodId() == null || food.foodId().isBlank() || !ids.add(food.foodId())
                    || food.danishName() == null || food.danishName().isBlank()
                    || food.carbohydrateGrams() == null || food.carbohydrateGrams().signum() < 0)
                throw new InvalidInputException("DTU dataset contains an invalid or duplicate food");
        }
    }
}
