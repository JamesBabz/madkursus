package dk.jamesbabz.madkursus.service.models;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AiChatIntentTest {
    @Test void planLimitedWinsOnlyForSameNormalizedTermAndPreservesDistinctBan() {
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_PLAN_DISCOVERY, false, List.of("kylling"),
                List.of(" PASTA ", "æg", "RIS", "Cafe\u0301"), 5, List.of("pasta", "café"));
        assertThat(intent.excludedIngredientTerms()).containsExactly("æg", "RIS");
        assertThat(intent.limitedIngredientTerms()).containsExactly("pasta", "café");
        assertThat(intent.preferredIngredientTerms()).containsExactly("kylling");
        assertThat(intent.requestedMealCount()).isEqualTo(5);
    }
    @Test void ordinaryDiscoveryContractIsUnchanged() {
        var intent = new AiChatIntent(AiChatIntent.Intent.MEAL_DISCOVERY, false, List.of(), List.of("pasta"), null, List.of("pasta"));
        assertThat(intent.excludedIngredientTerms()).containsExactly("pasta");
    }
}
