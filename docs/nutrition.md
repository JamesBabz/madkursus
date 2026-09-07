# Nutrition and carbohydrate calculation

`ProductTemplate` owns optional internal `NutritionData`. A value always records carbohydrate grams, a positive
basis quantity, a `RecipeUnit`, and provenance. Missing data is distinct from a known zero. The first curated data
is deliberately small: PENNE (Frida food 305, 73.9 g available carbohydrate declaration/100 g), KARTOFFEL
(Frida food 4, 16.7 g/100 g), and VAND (known zero/100 ml). Values were reviewed 2026-08-25. Other catalog
products remain unknown until a trustworthy matching food or packaged-product source is curated.

For each `RecipeIngredient`, the application converts its structured quantity to the nutrition basis using generic
same-dimension recipe conversions (dl/teaspoon/tablespoon to ml) or an explicit `ProductTemplateUnitConversion`.
It never assumes density or another cross-dimension conversion. Contribution is:

`converted basis amount × carbohydrate grams / nutrition basis quantity`

Only `RecipeIngredient` / `RecipeTemplateIngredient` rows are inputs. Prepared components, cooking-process
bindings, instructions, and inventory tracking modes do not add or suppress nutrition. Results expose known total,
per-portion total, completeness, unknown count, and one row per ingredient.

Future Hedia or Kulhydrattælleren work belongs behind an optional adapter consuming `CarbohydrateResult`; it must
not reimplement the calculation. Before such work, investigate current APIs, authentication, custom-meal support,
terms/legal constraints, and supported nutrients. No suitable API is assumed here.

## Maintenance and external-source boundary

`ADMIN_USERNAME` identifies the single current nutrition administrator and grants `ROLE_ADMIN` at login. Only
`/v1/admin/**` may mutate global nutrition. The Fødevaredata screen stores provider, external food ID, source
version/date, URL and notes alongside the value and explicit basis. ProductTemplate identity remains independent
of external IDs, and changes never rewrite user Products or inventory.

DTU Frida 5.5 is imported from DTU's official CC BY 4.0 downloadable dataset
(`https://doi.org/10.11583/DTU.29500682`, November 2025). The checked-in import resource contains Food ID,
Danish name, available carbohydrate declaration (falling back to available carbohydrate when a declaration is
absent), state text, version/date and source URL. Import is an explicit admin action; there is no startup or runtime
network synchronization.

Imported rows are versioned in `dtu_reference_foods` and remain separate from canonical ProductTemplates.
`product_template_dtu_mappings` records only explicit admin approvals and the value approved at that time. A new
dataset version adds reference rows without changing existing mappings or nutrition. When the latest row for an
approved Food ID changes carbohydrate value, the mapping is marked `REQUIRES_REVIEW`; only a new approval updates
canonical nutrition.

Suggestions compare normalized ProductTemplate names and aliases with normalized DTU names. Exact full-name and
unique base-name/alias candidates are separated from ambiguous or token-overlap candidates. Raw/cooked,
fresh/canned, branded/unbranded and generic/prepared variants are never persisted by suggestion generation. Every
single or bulk update requires the administrator to select the candidate and approve it. Runtime rendering never
calls DTU and recipe calculations continue to read only ProductTemplate nutrition.

The review matcher additionally expands conservative singular/plural forms and ignores preparation descriptors only
when deciding whether the underlying food word overlaps. This can create `REVIEW_REQUIRED` candidates, never an
automatic or high-confidence mapping. Candidate ranking gives a small preference to matching state words while still
showing raw, cooked, frozen, dried, canned, and prepared states for explicit review. The current item-level NO_MATCH
analysis is documented in `dtu-no-match-diagnostic.md` and `dtu-no-match-diagnostic.json`.

The future meal-calculator boundary is `CarbohydrateResult.carbohydratesForConsumedPortions`. Prepared portions
determine `preparedTotalGrams`; independently selected consumed portions multiply `perPortionGrams`. Medication or
dose advice is explicitly outside this model.
# Secondary-source assessment

DTU remains the preferred provider. USDA FoodData Central is the best secondary candidate for generic gaps: it has a supported REST API and versioned JSON/CSV downloads, stable `fdcId` identifiers, carbohydrate nutrients, and CC0/public-domain licensing. Its principal limitation is US/English naming and preparation conventions, so imported candidates must use the same explicit review workflow and must never overwrite a DTU or manual value automatically.

Open Food Facts is useful for European branded products and exposes nutrition through a supported API, but its community-contributed data has no accuracy guarantee and its database is ODbL licensed. It should not be combined into Madkursus until the database attribution/share-alike obligations have been reviewed. Scraping is neither needed nor acceptable.

The existing provenance columns already support `provider`, stable external food ID, source version/date, URL, and note. A future provider import should therefore use provider values `DTU`, `USDA_FDC` (or another reviewed secondary provider), and `MANUAL`, keep provider catalogs separate, rank suggestions per provider, prefer DTU in the UI, and require explicit approval before canonical ProductTemplate nutrition changes.

Official references:

- https://fdc.nal.usda.gov/api-guide/
- https://fdc.nal.usda.gov/download-datasets/
- https://openfoodfacts.github.io/openfoodfacts-server/api/
- https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/
