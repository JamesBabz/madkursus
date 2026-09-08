package dk.jamesbabz.madkursus.service.applications;

import dk.jamesbabz.madkursus.service.models.*;
import dk.jamesbabz.madkursus.service.exceptions.AiUnavailableException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AiSuggestionValidatorTest {
    private final AiSuggestionValidator validator = new AiSuggestionValidator();
    private final UUID user = UUID.randomUUID();
    private ProductTemplate template(String name, ProductCategory category, Unit unit) {
        return new ProductTemplate(UUID.randomUUID(),name,category,unit,List.of(),true);
    }
    private InventoryItem stock(ProductTemplate template,String amount) {
        return new InventoryItem(UUID.randomUUID(),new Product(UUID.randomUUID(),user,template.id(),template.name(),template.category(),template.defaultUnit(),template.defaultTrackingMode()),
                amount==null?null:new BigDecimal(amount));
    }
    private AiMealProposal.Ingredient product(InventoryItem stock,String amount,Unit unit) {
        return new AiMealProposal.Ingredient("product:"+stock.product().id(),amount==null?null:new BigDecimal(amount),unit);
    }
    private AiMealProposal.Ingredient requirement(ProductTemplate template,String amount) {
        return new AiMealProposal.Ingredient("template:"+template.id(),new BigDecimal(amount),template.defaultUnit());
    }
    private AiMealProposal proposal(AiMealProposal.Ingredient... ingredients) {
        return new AiMealProposal(AiMealProposal.Reply.SUGGESTIONS,List.of(new AiMealProposal.Suggestion("Pasta med tilbehør",List.of(ingredients))));
    }
    @Test void ownedIngredientIsNotMissingAndUsageIsNotStock() {
        var pasta=template("Farfalle",ProductCategory.GRAIN_PASTA,Unit.GRAM);var stock=stock(pasta,"1000");
        var proposal=proposal(product(stock,"200",Unit.GRAM));
        var result=validator.validate(proposal,validator.candidates(List.of(stock),List.of(pasta)),List.of(stock),0);
        var meal=result.suggestions().getFirst();var ingredient=meal.ingredients().getFirst();
        assertThat(meal.additionalIngredients()).isZero();assertThat(ingredient.usage()).isEqualByComparingTo("200");
        assertThat(ingredient.stock()).isEqualByComparingTo("1000");assertThat(ingredient.status()).isEqualTo(AiSuggestionValidator.Status.OWNED);
        assertThat(validator.present(proposal,result,0).answer()).contains("Du har allerede ingredienserne.").doesNotContain("GRAM","PIECE","MILLILITER");
    }
    @Test void canonicalTemplateMatchesOwnedProductEvenAfterRename() {
        var onion=template("Løg",ProductCategory.VEGETABLE,Unit.PIECE);var original=stock(onion,"1");
        var renamed=new Product(original.product().id(),user,onion.id(),"Mine løg",onion.category(),Unit.PIECE,InventoryTrackingMode.QUANTITY);
        var inventory=List.of(new InventoryItem(original.id(),renamed,BigDecimal.ONE));
        var result=validator.validate(proposal(requirement(onion,"1")),validator.candidates(inventory,List.of(onion)),inventory,0);
        assertThat(result.suggestions().getFirst().ingredients().getFirst().name()).isEqualTo("Mine løg");
        assertThat(result.suggestions().getFirst().additionalIngredients()).isZero();
    }
    @Test void exactReportedFlourGarlicAndOnionAmountsNeverBecomePurchases() {
        var flour=stock(template("Hvedemel",ProductCategory.BAKING,Unit.GRAM),"18");
        var garlic=stock(template("Hvidløg",ProductCategory.VEGETABLE,Unit.PIECE),"2");
        var onion=stock(template("Løg",ProductCategory.VEGETABLE,Unit.PIECE),"1");
        var inventory=List.of(flour,garlic,onion);
        var proposal=proposal(product(flour,"18",Unit.GRAM),product(garlic,"2",Unit.PIECE),product(onion,"1",Unit.PIECE));
        var result=validator.validate(proposal,validator.candidates(inventory,List.of()),inventory,0);
        assertThat(result.suggestions().getFirst().additionalIngredients()).isZero();
        assertThat(result.suggestions().getFirst().ingredients()).allMatch(i->i.status()==AiSuggestionValidator.Status.OWNED);
        assertThat(validator.present(proposal,result,0).answer()).contains("Du har allerede ingredienserne.").doesNotContain("GRAM", "PIECE", "ikke registreret");
    }
    @Test void absentIngredientIsMissingAndExplicitLimitIsEnforced() {
        var cheese=template("Ost",ProductCategory.DAIRY,Unit.GRAM);
        var options=validator.candidates(List.of(),List.of(cheese));var proposal=proposal(requirement(cheese,"50"));
        var accepted=validator.validate(proposal,options,List.of(),1);
        assertThat(accepted.suggestions().getFirst().additionalIngredients()).isEqualTo(1);
        assertThat(accepted.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.MISSING);
        assertThat(validator.validate(proposal,options,List.of(),0).suggestions()).isEmpty();
    }
    @Test void duplicateProductAndTemplateReferencesAreAggregatedBeforeShortageCalculation() {
        var onion=template("Løg",ProductCategory.VEGETABLE,Unit.PIECE);var stock=stock(onion,"1");
        var proposal=proposal(product(stock,"1",Unit.PIECE),requirement(onion,"1"));
        var result=validator.validate(proposal,validator.candidates(List.of(stock),List.of(onion)),List.of(stock),1);
        var meal=result.suggestions().getFirst();assertThat(meal.ingredients()).hasSize(1);assertThat(meal.additionalIngredients()).isEqualTo(1);
        assertThat(meal.ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.SHORTAGE);
        assertThat(meal.ingredients().getFirst().shortage()).isEqualByComparingTo("1");
    }
    @Test void presenceHasUnknownSufficiencyButDoesNotRequireBuying() {
        var salt=new ProductTemplate(UUID.randomUUID(),"Salt",ProductCategory.SPICE,Unit.GRAM,InventoryTrackingMode.PRESENCE,List.of(),true);
        var stock=stock(salt,null);var proposal=proposal(product(stock,null,null));
        var result=validator.validate(proposal,validator.candidates(List.of(stock),List.of(salt)),List.of(stock),0);
        assertThat(result.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.UNKNOWN_AMOUNT);
        assertThat(validator.present(proposal,result,0).answer()).contains("Du har allerede ingredienserne.").doesNotContain("mængden er ukendt");
    }
    @Test void unsupportedUnitOrUnknownReferenceCannotBecomeAPurchaseClaim() {
        var flour=template("Hvedemel",ProductCategory.BAKING,Unit.GRAM);var stock=stock(flour,"18");var options=validator.candidates(List.of(stock),List.of(flour));
        for(var ingredient:List.of(product(stock,"18",Unit.MILLILITER),new AiMealProposal.Ingredient("product:"+UUID.randomUUID(),BigDecimal.ONE,Unit.GRAM))) {
            assertThat(validator.validate(proposal(ingredient),options,List.of(stock),null).suggestions()).isEmpty();
        }
    }
    @Test void genericPastaIsNotMatchedToFarfalleOrDeclaredMissing() {
        var farfalle=template("Farfalle",ProductCategory.GRAIN_PASTA,Unit.GRAM);var pasta=template("Pasta",ProductCategory.GRAIN_PASTA,Unit.GRAM);
        var stock=stock(farfalle,"1000");var result=validator.validate(proposal(requirement(pasta,"200")),
                validator.candidates(List.of(stock),List.of(pasta)),List.of(stock),null);
        assertThat(result.suggestions()).isEmpty();assertThat(result.withheld()).isEqualTo(1);
    }
    @Test void negativeQuantityIsRejectedButOmittedQuantityDoesNotClaimSufficiency() {
        var flour=template("Hvedemel",ProductCategory.BAKING,Unit.GRAM);var stock=stock(flour,"18");var options=validator.candidates(List.of(stock),List.of());
        assertThat(validator.validate(proposal(product(stock,"-1",Unit.GRAM)),options,List.of(stock),null).suggestions()).isEmpty();
        var unquantified = proposal(product(stock,null,null));
        var result = validator.validate(unquantified,options,List.of(stock),0);
        assertThat(result.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.OWNED_UNQUANTIFIED);
        assertThat(validator.present(unquantified,result,0).answer()).contains("Du har allerede ingredienserne.").doesNotContain("brugsmængden er ikke fastlagt", "nok til forslaget", "Ekstra ingredienser med registreret mangel");
    }
    @Test void corruptEnvelopeFailsClosedAndRoleRepliesAreApplicationText() {
        assertThatThrownBy(()->validator.validate(null,List.of(),List.of(),null)).isInstanceOf(AiUnavailableException.class);
        var proposal=new AiMealProposal(AiMealProposal.Reply.OUT_OF_SCOPE,List.of());
        assertThat(validator.present(proposal,validator.validate(proposal,List.of(),List.of(),null),null).answer()).contains("køkkenhjælper");
    }
    @Test void removedStockIsRecheckedAsAbsent() {
        var flour=template("Hvedemel",ProductCategory.BAKING,Unit.GRAM);var stock=stock(flour,"18");
        var result=validator.validate(proposal(product(stock,"18",Unit.GRAM)),validator.candidates(List.of(stock),List.of()),List.of(),null);
        assertThat(result.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.MISSING);
    }
    @Test void badSiblingDoesNotDiscardValidMealsAndReasonsAreRetained() {
        var item=stock(template("Løg",ProductCategory.VEGETABLE,Unit.PIECE),"1");
        var valid=proposal(product(item,null,null)).suggestions().getFirst();
        var invalid=proposal(new AiMealProposal.Ingredient("unknown",null,null)).suggestions().getFirst();
        var proposal=new AiMealProposal(AiMealProposal.Reply.SUGGESTIONS,List.of(valid,invalid,valid));
        var result=validator.validate(proposal,validator.candidates(List.of(item),List.of()),List.of(item),0);
        assertThat(result.suggestions()).hasSize(2);
        assertThat(result.rejections()).containsExactly(new AiSuggestionValidator.Rejection(1,AiSuggestionValidator.Reason.UNKNOWN_REFERENCE,"unknown"));
        var allInvalid=new AiMealProposal(AiMealProposal.Reply.SUGGESTIONS,Arrays.asList(invalid,null));
        var rejected=validator.validate(allInvalid,List.of(),List.of(),null);
        assertThat(rejected.rejections()).extracting(AiSuggestionValidator.Rejection::reason)
                .containsExactly(AiSuggestionValidator.Reason.UNKNOWN_REFERENCE,AiSuggestionValidator.Reason.INVALID_STRUCTURED_RESPONSE);
        assertThat(validator.present(allInvalid,rejected,null).answer()).contains("Jeg fandt ikke et forslag").doesNotContain("UNKNOWN_REFERENCE");
    }
    @Test void reasonMappingAndMixedDuplicateAmountsRemainStrict() {
        var item=stock(template("Løg",ProductCategory.VEGETABLE,Unit.PIECE),"1");
        var candidates=validator.candidates(List.of(item),List.of());
        assertThat(validator.validate(proposal(product(item,"2",Unit.GRAM)),candidates,List.of(item),null).rejections().getFirst().reason()).isEqualTo(AiSuggestionValidator.Reason.UNIT_MISMATCH);
        assertThat(validator.validate(proposal(product(item,"-1",Unit.PIECE)),candidates,List.of(item),null).rejections().getFirst().reason()).isEqualTo(AiSuggestionValidator.Reason.INVALID_QUANTITY);
        assertThat(validator.validate(proposal(product(item,"2",Unit.PIECE)),candidates,List.of(item),0).rejections().getFirst().reason()).isEqualTo(AiSuggestionValidator.Reason.MAX_ADDITIONAL_INGREDIENTS_EXCEEDED);
        assertThat(validator.validate(proposal(product(item,"2",Unit.PIECE),product(item,null,null)),candidates,List.of(item),null).rejections().getFirst().reason()).isEqualTo(AiSuggestionValidator.Reason.INVALID_QUANTITY);
    }
    @Test void productionQuantityWithoutUnitIsRejectedWhileReferenceOnlyAndExplicitUnitWork() {
        var item=stock(template("Farfalle",ProductCategory.GRAIN_PASTA,Unit.GRAM),"1000");
        var candidates=validator.candidates(List.of(item),List.of());
        var invalid=validator.validate(proposal(product(item,"400",null)),candidates,List.of(item),0);
        assertThat(invalid.suggestions()).isEmpty();
        assertThat(invalid.rejections().getFirst().reason()).isEqualTo(AiSuggestionValidator.Reason.UNIT_MISMATCH);
        var quantified=validator.validate(proposal(product(item,"400",Unit.GRAM)),candidates,List.of(item),0);
        assertThat(quantified.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.OWNED);
        var referenceOnly=validator.validate(proposal(product(item,null,null)),candidates,List.of(item),0);
        assertThat(referenceOnly.suggestions().getFirst().ingredients().getFirst().status()).isEqualTo(AiSuggestionValidator.Status.OWNED_UNQUANTIFIED);
    }
    @Test void presentationListsOnlyActionableShortagesAndKeepsValidationFacts() {
        var onion=stock(template("Løg",ProductCategory.VEGETABLE,Unit.PIECE),"1");
        var pasta=stock(template("Penne",ProductCategory.GRAIN_PASTA,Unit.GRAM),"200");
        var proposal=proposal(product(onion,"2",Unit.PIECE),product(pasta,null,null));
        var stock=List.of(onion,pasta);var result=validator.validate(proposal,validator.candidates(stock,List.of()),stock,1);
        assertThat(result.suggestions().getFirst().additionalIngredients()).isEqualTo(1);
        assertThat(validator.present(proposal,result,1).answer()).contains("Du mangler:","Løg: 1 stk")
                .doesNotContain("Penne", "brugsmængden", "Ekstra ingredienser", "mængden er ukendt", "på lager");
    }
}
