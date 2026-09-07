# DTU Frida 5.5 NO_MATCH diagnostic

This report classifies all 182 ProductTemplates that the original matcher classified as `NO_MATCH` against the
1,381-food DTU Frida 5.5 catalog. The complete item-level report, including aliases, current nutrition state,
category, explanation, and three ranked DTU names, is in `dtu-no-match-diagnostic.json`.

## Method

The original matcher was reproduced unchanged first. Each unmatched ProductTemplate name and alias was then
compared with normalized DTU full and base names. Candidate ranking was used only for diagnosis; similarity alone
does not create or approve a mapping. Categories G and the matching change were reviewed separately so the report
does not call every weak lexical neighbor an algorithm defect.

## Reason distribution

| Category | Reason | Count |
|---|---|---:|
| A | Naming mismatch | 9 |
| B | Missing alias | 10 |
| C | More specific Madkursus product | 13 |
| D | More specific DTU foods / ambiguous variants | 16 |
| E | Product not represented in DTU | 77 |
| F | Non-food / special Madkursus template | 10 |
| G | Matching algorithm limitation | 23 |
| H | Other / manual investigation needed | 24 |
| | **Total** | **182** |

Representative examples:

- A: `Sojasauce` vs `Soja sauce`; `Tahini` vs `Tahin, lys`; `Cashewnødder` vs singular DTU names.
- B: `Tørgær` vs `Gær, tørret`; `Panko` vs `Rasp`; `Knoldselleri` vs `Selleri, rod, ...`.
- C: `Tipo 00-mel`, `Græsk yoghurt`, `Ramen-nudler`, and specific herb preparations.
- D: `Penne`, `Muslinger`, `Pølser`, and poultry cuts with several state/fat/cut variants.
- E: `Ciabatta`, `Ricotta`, `Couscous`, `Gnocchi`, and branded/international sauces absent from Frida 5.5.
- F: `Neutral olie`, `Wokgrøntsager`, mixed berries, and culinary spice blends without one canonical food.
- G: singular/plural pairs and ProductTemplate descriptors such as `Frosne blåbær` vs `Blåbær, dybfrost`.
- H: cases where the lexical evidence is insufficient to distinguish missing coverage from a terminology gap.

## Matching change

The improved matcher expands conservative Danish inflection forms for review matching and removes preparation/form
descriptors from the required food-name token set. It ranks candidates with the same content-token evidence and a
small state agreement boost. State words are retained in displayed DTU names and never treated as authorization.
Every newly found candidate is `REVIEW_REQUIRED`, not `EXACT` or `HIGH_CONFIDENCE`.

This moves 23 original `NO_MATCH` items to `REVIEW_REQUIRED`:

| Classification | Before | After |
|---|---:|---:|
| EXACT | 19 | 19 |
| HIGH_CONFIDENCE | 60 | 60 |
| REVIEW_REQUIRED | 124 | 147 |
| NO_MATCH | 182 | 159 |

The remaining 159 items are deliberately not promoted. The report indicates that missing/specialized DTU coverage
is a larger contributor than the safely correctable matching limitation: categories E and F account for 87 items,
while the generalized algorithm limitation accounts for 23. Categories A, B, C, D, and H still need explicit human
review or curated aliases because preparation, food form, or subtype can affect the correct value.
