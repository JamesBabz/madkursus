# AI-assisted nutrition mapping review

Generate the review input on Windows with:

```powershell
.\gradlew.bat exportNutritionMappingReview
```

The authoritative artifact is `build/nutrition-review/product-template-nutrition-review.json`. It contains every ProductTemplate without approved carbohydrate data, recipe-use context, production-safe automatic candidates, separate broader token-search candidates, and carbohydrate summaries for semantically compatible groups. The review goal is choosing a representative carbohydrate value—not matching protein, fat, or calories.

The export is read-only with respect to ProductTemplates, nutrition, and mappings. It does not import review decisions. The command uses an isolated temporary database populated from the repository's canonical migrations and bundled DTU Frida dataset; it never connects to or changes the configured application database.

## Expected response

Return one decision per reviewed ProductTemplate key:

```json
{
  "decisions": [
    {
      "productTemplateKey": "HAKKET_OKSEKOED",
      "decision": "MATCH",
      "provider": "DTU",
      "foodId": "942",
      "confidence": "HIGH",
      "reason": "Compatible raw minced-beef variants all contain 0 g carbohydrate; selected a real representative DTU row."
    },
    {
      "productTemplateKey": "TACO_KRYDDERI",
      "decision": "NO_SAFE_MATCH",
      "confidence": "HIGH",
      "reason": "Mixture composition varies materially."
    },
    {
      "productTemplateKey": "SOMETHING",
      "decision": "REVIEW",
      "confidence": "MEDIUM",
      "reason": "Plausible candidates differ materially in carbohydrate value."
    }
  ]
}
```

Allowed `decision` values are `MATCH`, `NO_SAFE_MATCH`, and `REVIEW`. Allowed `confidence` values are `HIGH`, `MEDIUM`, and `LOW`. A `MATCH` must use the stable ProductTemplate key, provider `DTU`, and a food ID present in the exported candidates. Do not return UUIDs or SQL.

## Future import boundary

A future importer should accept an explicitly supplied `nutrition-ai-review-result.json`, validate every ProductTemplate key and provider food ID against current canonical data, reject duplicates and unknown values, and apply only `MATCH` decisions with `HIGH` confidence after an explicit operator command. `REVIEW` and `NO_SAFE_MATCH` must remain unresolved. No importer is implemented yet.
