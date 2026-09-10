# Madhjælp: AI proposes, Madkursus verifies

## Flow and contracts

`POST /v1/ai/chat` accepts `{"message":"Hvad kan jeg lave?"}` plus optional
`maxAdditionalIngredients` (integer 0–20). The response remains `{"answer":"..."}`
with plain Danish text. Existing session authentication and CSRF handling remain.
The shared UI offers an explicit extra-ingredient selector. Its “højst 2” example
sets that field to 2. Arbitrary natural-language numeric constraints are NOT parsed;
users should select the limit if they want it enforced. Java always reports the
calculated number of distinct ingredient shortages, never a model-generated count.

1. `AiChatService` loads the authenticated user's inventory through `InventoryService`.
2. `AiSuggestionValidator` builds an allowlist of concrete Product references and
   common ProductTemplate catalog references. Domain objects never enter the adapter.
3. The model receives stock context, reference IDs and a cooking-only system role.
4. `AiChatPort` returns an untrusted `AiMealProposal`: a reply kind, meal names and
   required ingredient references, optional proposed quantities and storage units.
5. `OllamaChatAdapter` uses Ollama's JSON-schema `format` field and parses the
   returned JSON into the proposal. Provider details and schema stay in the adapter.
6. The service re-reads user inventory after inference, then validates proposals
   against the allowlist and current stock. No database transaction spans inference.
7. Java writes the answer. Raw JSON, model-authored purchase lists and model-authored
   availability claims never reach the UI. The existing UI renders text safely.

This is a meal-idea ingredient check, not recipe generation: no steps, persistence,
recipe objects, cooking execution, mutations or import are produced.
Ollama structured-output documentation: https://docs.ollama.com/capabilities/structured-outputs

## Deterministic checks

- References resolve only to Products from the request user's original inventory,
  or explicitly supplied common ProductTemplates. Model-generated names are not
  searched or compared to inventory names.
- `Product.id` and `Product.sourceTemplateId` establish identity. A template can
  resolve an owned product even if the product was renamed. Unknown references
  (including another user's product IDs) cause the whole suggestion to be withheld.
- Repeated references to the same concrete product (including a Product reference
  plus its ProductTemplate reference) are combined before quantity comparisons.
- Supplied usage quantities must be positive and have a matching storage
  unit. Unit mismatches, invalid numbers, ambiguous identity,
  untracked requirements and unsupported references withhold the suggestion.
- Usage is compared with physical stock. Sufficient owned stock is never classified
  as missing. A partially covered amount is a shortage, with owned stock and the
  additional quantity shown separately. Absent stock is missing.
- Presence tracking means owned with unknown quantity. Java reports that uncertainty
  and asks the user to check sufficiency; it does not invent a stock quantity.
- Distinct missing/short ingredients count toward `maxAdditionalIngredients`.
  Suggestions over the limit are withheld; model claims are never used to pass it.
- The shared `QuantityDisplay` is extracted from existing recipe instruction
  formatting. Chat and structured recipe instructions use the same Danish g/ml/stk
  and decimal/fraction conventions. Storage enum tokens remain internal schema data.

The stock snapshot is physical inventory, not net of meal-plan reservations. It is
fresh at validation time, but may of course change after the response is sent.

## Product identity and conservative generalization

ProductTemplates already have names, search aliases, categories and unit-conversion
metadata. Product has a canonical `sourceTemplateId`. Aliases support search, not
substitution; the category `GRAIN_PASTA` includes more than interchangeable pasta.
There is no curated “Farfalle satisfies Pasta” relation. No aliases or pasta mappings
were added for this feature, and no name-based matching is used.

A model may name a meal “Pasta bolognese” while referencing the actual owned Farfalle
Product. That can be validated. A generic Pasta template cannot be assumed equivalent
 to Farfalle. If a template is absent by identity but another stocked product shares
its category, this first implementation withholds the suggestion as ambiguous.
The category is a reason to withhold, NEVER proof of equivalence. This deliberately
also withholds some perfectly reasonable suggestions (e.g. a new dairy ingredient
when another dairy product is present). Unsupported/non-common catalog ingredients
cannot currently be proposed successfully. This bounded catalog keeps context modest.

Recommended follow-up: introduce a curated domain-level ingredient requirement /
substitution relation, with unit and recipe-use constraints, then reuse it across
recipe requirements and AI validation. Do not turn broad categories or aliases into
an implicit ontology. Unit conversions can subsequently reuse the existing quantity
normalizer; this version accepts only identical storage units.

## Role and quantity context

The system prompt establishes cooking-only scope and says user instructions cannot
override it, including “ignore previous instructions”. Out-of-scope replies are
rendered as an application-owned Danish redirect. This is defense in depth, not a
security boundary or deterministic classification of arbitrary user intent.

Inventory is marked `AVAILABLE INGREDIENTS — ALREADY OWNED. DO NOT SUGGEST BUYING THESE.`
Each entry distinguishes `available stock 1000 g` from suggested usage, and includes
its reference, storage-unit token and tracking mode for the structured response.
Quantities are optional for meal ideas. If supplied, usage must be independent of stock.
Java verifies quantities against stock; it does not assess portion size or culinary
plausibility. A model can still suggest using an unnecessarily large amount that
fits inventory. No large portion-sizing system is included.

The proposal contains only meal names and ingredients. The model's meal name,
choice of ingredients and completeness remain creative/unverified. Java only
claims to check the listed requirements. It cannot detect an omitted necessary
ingredient or guarantee a meal is sensible. No live-model quality claim is made.
For now, requests needing general cooking instructions use a fixed scope/help reply
instead of allowing unchecked prose to reintroduce inventory assertions. A richer,
carefully separated general cooking-answer path can be evaluated later.

## UI and model metadata

Dedicated **Madhjælp** navigation and the floating launcher share one `#chat-component`
and one `createAiChat` instance. Opening moves the existing element into a non-modal
`section` with `role="dialog"` and `aria-modal="false"`. It has no backdrop or focus
trap. Outside pointer/focus interaction minimizes without cancelling the underlying
interaction. Minus / **Minimér Madhjælp** and Escape also minimize. Messages, drafts,
limits, errors and pending requests remain intact. Real dialogs elsewhere still use
`showModal()` and retain their normal behavior.

Mobile keeps navigation exposed and sizes the panel with the existing visual-viewport
variables. A smaller keyboard viewport allows more panel space; the composer can
scroll. The desktop panel stays lower-right. No panel opens automatically.

`GET /v1/ai/chat/model` is authenticated, returns the configured model with no-store,
and makes no Ollama call. Both surfaces display it. `OLLAMA_MODEL` remains the only
model setting. `OLLAMA_BASE_URL`, `OLLAMA_CONNECT_TIMEOUT`, `OLLAMA_READ_TIMEOUT` and
all existing defaults/deployment settings are unchanged. There are no recipe-model
variables, model selection controls or automatic model switching.

## Tests

- `node --test src/test/js/ai-chat.test.cjs` uses Node's built-in runner and DOM doubles.
- `node --test src/test/js/ai-chat-browser.test.cjs` needs Playwright/Chromium;
  `CHAT_TEST_BROWSER_CHANNEL=msedge` can use installed Edge. All HTTP is mocked.
- `gradlew check` runs backend, contract, frontend-asset and packaging checks.

Tests cover authenticated context, scope/stock instructions, fresh stock, Product /
ProductTemplate identity, owned/missing/short/presence cases, duplicate aggregation,
unknown references, mismatched units, generic Pasta ambiguity, numeric limits,
Danish rendering, non-modal outside interactions, shared state and real modal behavior.
No test calls a real Ollama model. Reduced browser viewport checks do not replace
physical phone keyboard testing.

## Intentionally deferred

Backend conversation history (the browser never concatenates previous messages),
recipe generation/import, streaming, ontology/generalized ingredient matching,
portion sizing, reservation-aware availability, recipe completeness validation,
model switching, tool calling, RAG and all inventory/shopping mutations.


## Diagnosing meal suggestions

INFO logs record request start (model, inventory count, optional limit), context preparation
milliseconds/catalog size/context character count, provider milliseconds, parsing milliseconds
and proposal count, inventory refresh/validation milliseconds and accepted/rejected counts,
and total milliseconds/outcome, including failed requests. Timings use a monotonic clock.
HTTP timing includes response transfer/deserialization; parsing timing measures the inner
structured proposal. These are wall-clock observations, not GPU-only inference metrics.

DEBUG on `dk.jamesbabz.madkursus.service.applications.AiSuggestionValidator` logs each decision
and resolved product/template IDs. DEBUG on `dk.jamesbabz.madkursus.outbound.ollama` logs parsed
proposals and malformed sibling indexes. Enable narrowly while diagnosing: proposals can
contain user-related food data. INFO does not contain prompts, inventory contents, messages,
raw JSON, credentials or session data. WARN covers provider failures/timeouts, invalid
responses and a single aggregate of validation rejections; details stay at DEBUG.

Validation returns indexed rejection records with UNKNOWN_REFERENCE, AMBIGUOUS_REFERENCE,
INVALID_QUANTITY, UNIT_MISMATCH, UNTRACKED_INGREDIENT, INVALID_STOCK,
MAX_ADDITIONAL_INGREDIENTS_EXCEEDED or INVALID_STRUCTURED_RESPONSE. Insufficient stock is a
validated shortage, not itself a rejection. Valid siblings survive malformed individual
suggestions as well as semantic rejection. A malformed outer envelope still fails with
HTTP 503. All rejected suggestions produce the existing friendly fallback.

The schema now requires only an ingredient reference. Quantity/unit are optional; a supplied
quantity requires its matching storage unit. Reference-only owned items never claim enough
stock. Zero stock still counts as missing. Duplicate identities count once; specified amounts
sum, while mixed quantified/unquantified duplicates are rejected to avoid presenting a partial
sum as complete usage. Purchase limits apply to registered missing ingredients/shortages;
without usage amounts, actual sufficient quantities cannot be guaranteed, and the UI says so.
The full schema is sent through Ollama's format field without duplicating it in a system message.

The remaining common-template catalog may still dominate prompt size. UUIDs are now kept in the server-side allowlist rather than sent to the model. The
120-second read timeout is unchanged. Measure actual Ollama runs before further optimization;
no speedup or model-compliance guarantee follows from deterministic unit tests.

Manual probes: "Hvad kan jeg lave?", "Noget med kartofler?", the explicit maximum-2 example,
"Jeg har lyst til pasta", and a request specifying usage greater than stock. Compare logs for
context size, provider time and rejection reasons. Also stop Ollama temporarily to verify the
normal friendly provider error, and repeat with an empty inventory. Do not expect prior-message
recall in this stateless feature.


## Raw failure diagnostics and compact references

Enable only the adapter logger at DEBUG using Spring configuration:

```yaml
logging:
  level:
    dk.jamesbabz.madkursus.outbound.ollama.OllamaChatAdapter: DEBUG
```

On an invalid structured response, `AI structured parse failed` includes model,
`responseCharacters` and `rawContent`. It captures the generated message content only,
including prose, fences or malformed JSON exactly as received (JSON-escaped for safe
single-line logging). The field is capped at 6,000 rendered characters plus an explicit
TRUNCATED marker. Null means there was no generated content in the decoded response.
One such line is also emitted when individual suggestions fail parsing. It contains no
HTTP headers, authentication/session metadata or transport wrapper. Generated content
can itself contain user-related food text; enable this logger only for diagnosis.
No permissive parsing, fence stripping or JSON extraction has been introduced.

`AI generation started` adds `promptCharacters` (sum of all message contents, including
the user message) and `schemaCharacters` (compact serialized format schema). Provider
completion adds `responseCharacters`; existing context, inventory/catalog count and
stage timing logs remain. Character counts are Java UTF-16 lengths, not token counts
or serialized HTTP-body byte counts.

Model references are now request-local p0, p1, ... for inventory and t0, t1, ... for
additional templates. Each maps to the same Product/ProductTemplate IDs in the existing
Candidate allowlist; validation still uses IDs, never names. Template entries with an
already represented sourceTemplateId are omitted. With explicit maxAdditionalIngredients=0,
no additional template catalog is fetched/sent. Other requests retain the remaining common
catalog; selecting a smaller semantically relevant subset is intentionally deferred.

For the supplied 15-inventory/95-catalog production baseline, compact references alone
save exactly 4,625 context characters: 10,687 -> 6,062 and 10,753 -> 6,128 before any
owned-template removal. These are calculated same-data comparisons, not live production
measurements. Catalog count becomes 95 minus represented templates (or zero with the
explicit zero-extra limit); the precise count needs the next production log. System text
is 1,983 -> 2,062 characters, adding the explicit request-local reference instruction.
The schema remains 685 compact characters; no fields were removed. Quantity/unit were
already optional and remain validated whenever supplied.

Next, send "Hvad kan jeg lave med det jeg har i mit inventar?" and paste the consecutive
`AI context prepared`, `AI generation started`, `AI provider completed`, `AI structured
parse failed` and `AI proposal invalid` lines for that request. If it succeeds, include
`AI proposal parsed` and validation completion instead. Repeat with explicit zero extra
ingredients to compare the inventory-only request. Keep OLLAMA_MODEL and the 120s timeout
unchanged so the measurements are comparable.


## Quantity/unit schema regression

The production response with quantity=400 and unit=null exposed a gap between the
schema and validator. Ingredient items now use two anyOf object branches: an omitted/null
quantity, or a numeric quantity with a required non-null GRAM/MILLILITER/PIECE unit.
Reference-only proposals remain valid. Java still rejects missing/mismatched units;
no unit is inferred. The compact schema grows from 685 to 896 characters.
We use alternatives instead of if/then conditionals because llama.cpp's schema-to-grammar
converter documents conditionals as unsupported:
https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md
This constrains generation, but Java validation remains authoritative if a provider
ignores the schema. No real-model compliance or latency guarantee is implied.


## Reply/list consistency

The NEED_DISH-with-meals failure was not a Jackson enum mismatch: Java, Jackson and
the old schema all accepted NEED_DISH. The adapter's post-deserialization envelope
check rejected any non-SUGGESTIONS reply with a nonempty suggestions list, while the
old schema allowed that combination. maxAdditionalIngredients=1 did not alter parsing
or reply kinds; it only supplied the numeric constraint and applied normal validation.

AiMealProposal.Reply now owns the advertised values, their prompt instructions and
whether they allow suggestions. The adapter builds two schema alternatives from that
enum and the resource template: SUGGESTIONS with the existing bounded meal list, or
NEED_DISH/NO_SUGGESTIONS/OUT_OF_SCOPE with an empty list. The resource does not duplicate
the reply enum. Prompt reply instructions are generated from the same enum.
NEED_DISH remains the stateless clarification/help response, never a meal proposal.
A real meal must use SUGGESTIONS regardless of the additional-ingredient limit.
Incorrect combinations remain rejected; there is no reply alias or normalization.
Tests cover the production meal shape with maxAdditionalIngredients=1 and omitted
quantities/units, rejection of the contradictory NEED_DISH shape, and round-trip
parsing of every advertised reply. Earlier schema size measurements describe the old
schema; generation logs report the assembled schema size after this change.


## Known recipes and compact chat

RecipeMatchingService is independent of AI and UI. It queries the current user's saved
recipes and inventory plus active shared RecipeTemplates once, omits shared entries
already copied by that user, and compares one canonical portion. No recipe bodies go
to Ollama. Identity is strictly Product.sourceTemplateId -> recipe ProductTemplate.id;
matching names, aliases and broad categories never establish identity. Multiple stocks
with the same identity sum only with compatible units. Duplicate recipe requirements
aggregate by identity. RecipeQuantityNormalizer reuses dimensional and template-specific
conversions; QuantityRoundingPolicy formats shortages consistently. Physical stock is
used, as in Madhjælp; meal-plan reservations are not deducted.

Results are COOKABLE, NEAR_MATCH, CHECK_QUANTITIES or internally UNRESOLVED. Unknown
conversions/invalid quantities cannot become verified matches. Presence stock has an
explicit quantity caveat rather than an invented amount. Untracked requirements (e.g.
water) are excluded, consistent with existing RecipeInteractionService semantics.
Missing identity or a quantity shortage counts once per template. An explicit zero limit
returns only fully verified COOKABLE results. Positive limits bound known missing items;
quantity-unknown results remain visibly qualified. Null applies no ingredient-count limit.
Ordering is deterministic: missing count, uncertainty count, name, source, ID.

For the narrow Danish discovery phrases used by the quick prompts and the acceptance
question, AiChatService returns up to five known matches immediately, bypassing Ollama.
If none match, the original AI flow runs. Questions containing additional preferences,
exclusions or ingredients are deliberately not classified as generic discovery. The narrow
"hvad kan jeg lave hvis jeg køber højst N ekstra ingredienser" phrase supports N=0..20;
an explicit request limit can only tighten it. This is not general intent/number extraction.

POST /v1/ai/chat still returns answer. Optional knownRecipes adds IDs, RECIPE/TEMPLATE
source, name, state, missing count/names and quantity-uncertain names, through OpenAPI
and the generated DTOs. The shared component renders these as stored-recipe cards using
safe text nodes. Buttons invoke existing recipe/detail dialogs at one portion, matching
the calculation. Opening a card minimizes the assistant and preserves its state. No recipe
copy, cooking action or inventory/shopping mutation is performed.

Empty-state quick prompts are chips and disappear once a message is sent. The existing
limit selector is now an accessible composer-adjacent pill. The message list gets the
flexible space; the composer has no nested scroll container. At >=64rem the non-modal
panel is 30rem wide, up to 44rem tall within the visible viewport. Below 64rem it becomes
a near-full-height sheet above the bottom navigation with safe-area padding. At heights
<=500px it uses the remaining visual viewport, hides secondary metadata/help and compacts
the input; the composer remains at the bottom. The shared visualViewport handling responds
to the on-screen keyboard. Desktop/page/mobile still use the same live component/state.
PWA cache version is 46. Real application dialogs remain modal.

The acceptance fixture loads the actual curated "Kødboller i tomatsovs med pasta" recipe
and canonical product/conversion data, with the reported stock amounts. It finds no missing
ingredients and CHECK_QUANTITIES for presence-tracked spices/oil. This is deliberately not
an unconditional "enough quantity" claim. Production must retain the identity links and
conversions, including the flour tablespoon conversion. Unlinked custom products cannot
satisfy canonical requirements by name. Changes after the snapshot and portion changes
require rechecking; physical phone keyboard behavior should still be checked on-device.


## Soft ingredient preferences and presentation

Known-recipe cards now show "Du har alt, du skal bruge." when no ingredients are missing,
or a missing-ingredient list. They no longer render presence/unknown-quantity warnings.
This is presentation only: CHECK_QUANTITIES, uncertainIngredients, shortages and the
strict zero-extra limit remain unchanged internally and in the API.

RecipeMatchRanker accepts a request-local Set<UUID> of preferred ProductTemplate IDs.
RecipeMatchingService.findMatches(maximum, preferredTemplateIds) applies the normal
eligibility checks and then ranks all remaining matches before any UI result limit.
The no-preference overload remains the default. RecipeMatch retains actual ingredient
identity IDs internally; those IDs are not added to the chat DTO. Ordering is:
missing ingredient count ascending, quantity certainty first, number of distinct preferred
identities present descending, uncertainty count ascending, name, source, recipe ID.
Soft preferences never filter out otherwise valid recipes or change inventory facts.

Natural-language preference extraction is not implemented. ProductTemplateService.search
already searches names and aliases, but returns candidate matches, not a guaranteed identity
or ingredient family. "kylling" can identify multiple cuts; "pasta" does not establish
Farfalle equivalence. Categories are too broad for these preferences. No mappings were added.
The smallest next integration is to resolve/confirm preferred template IDs using that existing
search (e.g. a request-specific ingredient picker), then pass the resulting ID set into the
new overload before taking the top five. If AI interprets the phrase later, it should select
only offered references, Java should resolve them to allowed IDs, and this ranker should
perform the ordering. The current AI schema, discovery routing and HTTP contract are unchanged.
PWA asset cache version is now 47.


## Discovery/ranking correction

The "på lager" bypass came from MealDiscoveryRequest's whole-sentence BASIC regex:
its optional inventory suffix allowed "i [mit] inventar/lager", not "på lager".
It was not caused by RecipeMatchRanker; routing bypassed the matcher entirely.
The router now recognizes generic discovery using word-level discovery/meal cues and
neutral connective/inventory vocabulary, independently of ordering and punctuation.
It does not enumerate complete Danish sentences. Unrecognized content (including
ingredient names, exclusions and preference clauses) goes to the existing AI path
instead of being silently discarded. This small conservative lexical router is not
general Danish language understanding. Numeric purchase limits still intersect with
the explicit selector; "kun" with stock wording applies zero.

Known matching returns immediately when results exist, with no provider dependency.
Ranking after the hard eligibility/maximum filter is now: any preferred identity first,
fewer missing ingredients within that group, quantity certainty/uncertainty count,
name/source/ID. Without preferences every match shares the same preference score, so
availability leads. Matching any supplied preferred ID is a soft boost, not a filter;
non-preferred matches remain. A zero limit still excludes every purchase requirement
and retains the existing stricter COOKABLE check. No natural-language preference
extraction or model contract change is included.

The existing zero selector option was already present. Its values remain null/0/1/2/3,
now labelled Ingen fast grænse / Kun det jeg har / Højst 1/2/3 ekstra. Cache version 48.
Generated suggestion presentation now shows the meal name and "Du har allerede
ingredienserne." when no shortages exist, or only actionable missing names/amounts.
It does not list stock quantities, unknown usage, zero-shortage counts or validator
reports. It makes an ingredient-presence statement, not a portion-sufficiency claim.
All validation result objects, quantity/unit checks and hard limits remain unchanged.
Semantic discrepancies between generated titles and their ingredient IDs remain outside
this correction; stored recipes are the primary path for generic discovery.

## Small intent interpretation

Unrecognized free-form messages now pass through `AiIntentPort`, implemented by the
existing Ollama adapter with the same model/client/timeouts. Recognized discovery
wording (including the existing quick actions) still bypasses interpretation. The
lexical router was not expanded. The internal structured contract is:

```json
{"intent":"MEAL_DISCOVERY","inventoryAware":true,"preferredIngredientTerms":["kylling"],"excludedIngredientTerms":[]}
```

Intent values are MEAL_DISCOVERY, GENERAL_COOKING and OTHER. All four fields are
required; each term array permits at most five nonblank strings of at most 80
characters. The schema derives intent values from the Java enum. Parsing remains
strict. The classifier receives only a short system instruction and the user message:
no inventory, catalog, recipe bodies or quantities. Its compact JSON schema is 491
characters. INFO logs report intentPromptCharacters, intentSchemaCharacters,
intentModelDurationMs, intentResponseCharacters, intent, preferredTermCount and
resolvedPreferenceCount. DEBUG includes parsed interpretation and resolution IDs.

For MEAL_DISCOVERY without exclusions, Java resolves terms using exact normalized
ProductTemplate names or existing aliases. This adds an exact query beside the
existing substring search; substring matches alone are not safe identity resolution.
Only a single distinct template ID is accepted. Unknown/ambiguous terms are ignored,
not guessed. Existing identities do not generalize Pasta to Farfalle or Kylling to
all chicken cuts. No substitutions or new aliases are introduced.

The existing matcher applies the selector's hard limit before preference ranking.
Known matches return immediately, without creative meal generation. Inventory is
always user-scoped regardless of inventoryAware (which is interpretation metadata).
GENERAL_COOKING/OTHER, requests with exclusions, and discovery without known matches
retain the existing chat path. Exclusions are detected but deterministic exclusion
filtering is deferred; bypassing known results avoids silently disregarding them.
Natural-language numeric limits outside the existing deterministic router are not
newly extracted: use the selector for a reliable hard constraint.

Timeouts, malformed JSON and unknown intent values are logged and fall back to the
existing chat path. This prevents a classifier-only error from failing a request,
but cannot make an unavailable Ollama provider available. Two sequential provider
calls can increase latency on fallback; no timeout was increased and live speed
is not established by mocked tests. HTTP/OpenAPI/frontend contracts are unchanged.

Verification: full Gradle check (including OpenAPI and packaged security checks),
frontend state tests and browser tests at desktop/mobile widths. New deterministic
tests cover tiny adapter requests, every intent enum, invalid output/timeouts,
free-form routing, exact/ambiguous/unresolved preferences, quick-action bypass and
real matcher/ranker integration with the zero-extra hard filter. No real Ollama.

## Catalog preferences and two-portion discovery

The intent interpreter is unchanged. The bundled/migrated catalog has no exact
name or alias `kylling`, so the previous unique-name/alias resolver returned zero.
MEAT is too broad to identify chicken; Product.sourceTemplateId and recipe
ingredients establish concrete identity, not a chicken family. The seeded recipe
`Pasta med kylling og tomat` references Kyllingebryst
(`47814405-2163-3beb-b98e-5c31ad175fa8`), 150 g per portion.

V41 introduces `product_template_discovery_terms`, reusable catalog metadata for
soft discovery preferences only. Explicit curated membership associates `kylling`
with Hakket kylling, Hel kylling, Kyllingebryst, Kyllingelår, Kyllingeoverlår,
Kyllingeunderlår and Kyllingevinger. Hønsebouillon and Kyllingefond also exist but
are deliberately not members of this meat preference group. This is not aliasing
kylling to one cut, and does not make any cut satisfy another cut's stock requirement.
The relationship is stored as catalog data, not inferred by Java string matching.

`ProductTemplateService.resolveDiscoveryTerm` returns explicit group members, or
falls back to a unique exact existing name/alias. Unknown and ambiguous ungrouped
terms remain unresolved. `IngredientPreferenceResolver` accepts all validated IDs
and passes their union to the existing RecipeMatchRanker. Hard filters still run
first; preference boosts then precede missing count and deterministic tie breakers.
Adding another broad preference later requires reviewed catalog membership, not
another condition in chat code. No ontology, substitution, or AI contract changes.

DEBUG logs show Catalog discovery resolution (CURATED_GROUP / EXACT_IDENTITY /
UNRESOLVED / AMBIGUOUS_IDENTITY), compact candidate IDs/names, AI preference
resolution resolvedTemplateIds, and Recipe preference ranking recipeId/recipeName,
preferredTemplateMatches, matchedTemplateIds and missingIngredients. Existing INFO
preferredTermCount/resolvedPreferenceCount remain; production should now show 7
resolved preferences for `kylling` after migration V41.

`AiChatService.MEAL_DISCOVERY_PORTIONS = 2` is passed to the reusable matcher's
three-argument findMatches overload and interpolated into the response text.
Quantities are multiplied before unit normalization/aggregation and comparison.
200 g per portion against 300 g stock therefore has a 100 g shortage for discovery.
Existing matcher overloads retain one portion for other consumers. Stored recipes,
presence/untracked semantics and the cleaned presence presentation are unchanged.

The PostgreSQL integration regression migrates the real catalog, resolves `kylling`,
loads the actual seeded chicken pasta recipe and routes the production message
through mocked intent -> real catalog resolution -> matcher/ranker -> chat response.
It verifies chicken-first ordering, zero-extra exclusion, no-preference/unresolved
fallback ordering, stable results, and 300 g chicken required for two portions.
Additional matcher tests cover 300/400 g stock, shortage/count/filter/ranking changes
and unchanged presence behavior. No live production database or Ollama is accessed.


## Phase 1: shared recipe/planning availability

Known-recipe discovery now uses `InventoryAvailabilityService.snapshot(null)` once
per discovery request. Both `RecipeMatchingService` and
`RecipeInteractionService.calculate` use `forTemplate(snapshot, template)` for
stock identity, tracking compatibility and effective availability. Recipe scaling
still uses `RecipeQuantityNormalizer`; ranking rules are unchanged.

- Only `Product.sourceTemplateId == ProductTemplate.id` establishes calculation
  identity. Renamed linked products still count. Legacy same-name products without
  the link do not count; the product-management name fallback is deliberately not
  used by either calculator. No data is automatically linked or migrated.
- Canonical stock with a different storage unit, an incompatible untracked mode,
  invalid numeric stock, or multiple products for one template is uncertain.
  Presence on either side is never upgraded into quantitative sufficiency.
- All other `PLANNED` entries reserve stock. `COOKED` and `SKIPPED` entries do not.
  Saved-plan calculation still excludes its own plan ID.
- A failed reservation conversion preserves reservation details and a warning.
  Reserved, available and planned-shortfall quantities are null, not zero.
  Collective requirements carry the existing warning and a null missing quantity;
  discovery uses `CHECK_QUANTITIES` and cannot pass the zero-extra hard filter.
- Presence requirements use null numeric stock/requirement fields. Their
  `satisfied` flag means only that the ingredient is present. Existing chat and
  requirement rendering explicitly qualify quantity uncertainty.
- Collective selection aggregation is unchanged. Calculations do not mutate
  physical inventory. An explicitly invoked cooking operation still deducts its
  known quantity even if a different plan has an unknown reservation; unknown
  ingredient conversions or incompatible stock metadata are not deducted.

This supersedes historical physical-stock and hidden-uncertainty descriptions
above for **known-recipe discovery**. AI-generated inspiration still has its
separate physical-stock validation contract. No planning intent, chat draft,
selection action, endpoint or shopping confirmation is introduced here.

## Phase 2: read-only meal-plan candidate discovery

The small intent contract adds `MEAL_PLAN_DISCOVERY`, nullable integer
`requestedMealCount`, and `limitedIngredientTerms` alongside `inventoryAware`,
`preferredIngredientTerms`, and `excludedIngredientTerms`. Weekday ranges describe
meal counts only; there is no date field. The provider receives only its small
classification prompt/schema and the current message, without inventory context.

`AiChatService` delegates this intent directly to `MealPlanDiscoveryService`.
Java defaults an omitted count to 5 and rejects counts outside 1–14 with a Danish
validation answer. Every candidate is evaluated independently at 2 portions.
`RecipeMatchingService.findOwnedMatches` restricts eligibility to the current
user's recipes before matching, without consulting recipe templates. It reuses
Phase 1 availability after existing reservations and the existing ranker.

Preferences, exclusions and limited terms use `IngredientPreferenceResolver`
and the reviewed ProductTemplate discovery-term mechanism. Preferences influence
ranking; resolved exclusions remove recipes containing those identities. Terms
that cannot be resolved are reported separately and are not claimed as enforced.
Limited terms retain their original wording, resolved identity union and unresolved
terms, but frequency limits are never enforced in this phase. The migrated catalog
resolves chicken to seven identities and resolves egg; generic pasta is unresolved.

The response adds optional `mealPlanProposal` with `requestedMealCount`,
`defaultPortions`, `candidates` (existing `AiKnownRecipe` cards),
`unresolvedPreferredIngredientTerms`, `unresolvedExcludedIngredientTerms`,
`limitedIngredientTerms`, `limitedIngredientTemplateIds`, and
`unresolvedLimitedIngredientTerms`. Plan candidates are not duplicated in
`knownRecipes`. The bound is `min(requestedMealCount + 3, 10)`. A shortage is based
on the total eligible owned recipes, not on the display cap. Requests over ten
also explain that only ten candidates are displayed.

The frontend renders the proposal's cards using the existing safe-text renderer
and open-recipe action. No selection controls, draft, dates, collective preview,
saving, shopping mutation or recipe copying exist in this route. No full AI meal
generation is called after classification, even when no owned recipes qualify.

Automated classifier tests use stubbed provider JSON to verify the prompt,
schema and parsing contract. Live gemma3:4b regression checks also exercise the
negation/reduction phrases. The original egg failure was reproduced as a wrong
positive preference in provider output; the stored canonical egg identity and
owned recipe ingredient reference match. The original pasta failure returned
the same term in both excluded and limited lists.

Intent calls now use temperature 0, explicit whole-phrase examples, and a framed,
JSON-quoted user message. The plan intent defensively removes an exclusion when
the same trimmed, case-insensitive, Unicode-normalized term is also limited.
Distinct hard exclusions remain; ordinary meal discovery is unchanged. The small
contract cannot represent separate hard and frequency constraints on the exact
same term, so LIMITED wins that contradiction. No phrase matching or ingredient
mappings were added to Java.

The two original prompts were verified through the production adapter/resolver/
matcher against the local database with a read-only connection: canonical egg
recipes were excluded and unresolved pasta produced only the limited warning.
The provider can still emit overlapping lists; normalization handles that before
resolution or eligibility filtering. Live model quality should still be checked
when changing model or classifier instructions.
