# RecipeTemplates

## Kilder og versionering

De 15 globale opskriftsskabeloner vedligeholdes i den læsbare, kanoniske kilde
`src/main/resources/seed/recipe-templates.json`. Alle mængder er for én portion.

Den ældre fil `src/main/resources/db/seed/madkursus-recipe-templates-seed.json`
bevares uændret, fordi den allerede anvendte Flyway-migration V15 læser dens
oprindelige tekstformat på en ny database. V21 læser derefter den kanoniske kilde
og opgraderer de globale templates til TEXT- og PROCESS-trin. Der findes ingen
startup-synkronisering, og ændringer i JSON-filen slår derfor ikke lydløst igennem.

En ændring kræver altid en ny additiv Flyway-migration. Ret aldrig en allerede
anvendt migration eller dens input på en måde, der ændrer dens checksum/adfærd.

## Datamodel

- `recipe_templates`: global identitet, navn og metadata.
- `recipe_template_ingredients`: ProductTemplate-reference, én-portionsmængde,
  RecipeUnit, tilberedning og rækkefølge.
- `recipe_template_steps`: ordnede TEXT- eller PROCESS-trin. PROCESS refererer
  kun den globale `cooking_processes`-række.
- `recipe_template_process_bindings`: konkrete parameterværdier og eventuelle
  referencer til `recipe_template_ingredients`.

ProductTemplates opløses i migrationen via normaliseret navn. CookingProcesses
opløses via den stabile `process_key`, eksempelvis `BOIL_POTATOES`. Ukendte
referencer, manglende obligatoriske bindings, dublerede keys og over-allokering
får migrationen til at fejle tydeligt.

## Procesbindinger og delallokering

En INGREDIENT_QUANTITY-binding angiver både ingrediensens lokale `key` og den
mængde/enhed, processen bruger. Flere procestrin må referere samme
RecipeTemplateIngredient. Summen, efter konvertering mellem kompatible
volumenenheder, må højst være template-ingrediensens total. En restmængde er
gyldig. Eksempelvis bruger “Kylling med ris og grøntsager” 5 ml af den samme olie
i kyllingeprocessen og 5 ml i grøntsagsprocessen, mens ingredienslisten fortsat
kun har én række på 10 ml.

Ved preview skalerer backend kun ingrediensbindinger med antallet af portioner.
Varighed, varme, temperatur, tal og tekst skaleres ikke. Rendering genbruger
`CookingProcessService`, inklusive brugerens foretrukne komfurmapping og abstrakt
fallback. Template-detail-endpointet accepterer `?portions=N`.

## Kopiering og uafhængighed

Ved “Føj til mine opskrifter” kopieres ingredienser, TEXT-trin, PROCESS-referencer
og bindings til en ny brugerejet Recipe. Template-ingrediens-id'er remappes til de
nye RecipeIngredient-id'er. `sourceTemplateId` er kun historisk oprindelse.

Allerede kopierede Recipes bliver ikke ændret af V21 eller senere template-
opdateringer. Redigering af en kopi ændrer hverken RecipeTemplate eller den
globale CookingProcess. Ændringer i en global CookingProcess påvirker derimod
renderingen af PROCESS-trin, som fortsat refererer processen; definitionen
snapshottes ikke ind i hver opskrift.

## Tilføj eller opdatér en template

1. Kontrollér ProductTemplate-navne og CookingProcess-keys i seed-kilderne.
2. Redigér en ny version af den læsbare template-kilde. Brug stabile lokale
   ingredienskeys og bind alle nødvendige procesparametre.
3. Kontrollér én-portionsmængder og summen af alle ingrediensallokeringer.
4. Opret en ny Flyway-migration, som udfører den eksplicitte, deterministiske
   ændring. Den må kun ændre global template-data og dens ejede child-rækker.
5. Tilføj/tilpas seed-, rendering-, skalering- og kopitests. Kør OpenAPI-
   validering, source generation og et rent build mod en databasekopi.

## PostgreSQL-inspektion

```sql
SELECT id, name, active, created_at, updated_at
FROM recipe_templates
ORDER BY name;
```

```sql
SELECT rt.name AS recipe, rti.sort_order, pt.name AS ingredient,
       rti.quantity, rti.unit, rti.preparation
FROM recipe_template_ingredients rti
JOIN recipe_templates rt ON rt.id = rti.recipe_template_id
JOIN product_templates pt ON pt.id = rti.product_template_id
ORDER BY rt.name, rti.sort_order;
```

```sql
SELECT rt.name AS recipe, rts.sort_order, rts.step_type,
       rts.instruction, cp.process_key, cp.name AS process
FROM recipe_template_steps rts
JOIN recipe_templates rt ON rt.id = rts.recipe_template_id
LEFT JOIN cooking_processes cp ON cp.id = rts.cooking_process_id
ORDER BY rt.name, rts.sort_order;
```

```sql
SELECT rt.name AS recipe, rts.sort_order AS step_no, cp.process_key,
       b.parameter_key, pt.name AS ingredient, b.quantity, b.unit,
       b.duration_seconds, b.temperature_celsius, b.heat_level
FROM recipe_template_process_bindings b
JOIN recipe_template_steps rts ON rts.id = b.recipe_template_step_id
JOIN recipe_templates rt ON rt.id = rts.recipe_template_id
JOIN cooking_processes cp ON cp.id = rts.cooking_process_id
LEFT JOIN product_templates pt ON pt.id = b.product_template_id
ORDER BY rt.name, rts.sort_order, b.parameter_key;
```

Kontrollér delallokeringer med:

```sql
SELECT rt.name AS recipe, pt.name AS ingredient, rti.quantity AS total,
       rti.unit, COALESCE(SUM(b.quantity), 0) AS allocated
FROM recipe_template_ingredients rti
JOIN recipe_templates rt ON rt.id = rti.recipe_template_id
JOIN product_templates pt ON pt.id = rti.product_template_id
LEFT JOIN recipe_template_process_bindings b ON b.recipe_ingredient_id = rti.id
GROUP BY rt.name, pt.name, rti.quantity, rti.unit
ORDER BY rt.name, pt.name;
```
