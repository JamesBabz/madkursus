# CookingProcesses

CookingProcesses are shared, application-managed instruction definitions. Recipes and recipe templates reference a process and store only their concrete parameter bindings. The process text is deliberately not copied: a versioned change to a global process therefore changes the rendering of every current and future `PROCESS` step that references it. Binding overrides remain local to the recipe or template.

## Value ownership

Each parameter has an explicit `source`:

- `INPUT`: a normal recipe input, usually an ingredient allocation.
- `DEFAULT`: process-owned knowledge which is not editable per recipe.
- `OVERRIDEABLE_DEFAULT`: a process default shown only under advanced settings. A binding is stored only when it differs.
- `DERIVED`: calculated at render time by a named `CookingProcessDerivedRule`; the calculated value is never persisted. An advanced binding replaces the calculation.

This means changing a global default or rule intentionally affects every usage without an override. Rules are a small typed Java enum/implementation, not JSON expressions or frontend calculations. `BOIL_POTATOES` currently uses 3 ml tap water and 0.001 tsp salt per gram of potatoes. `BOIL_PASTA` uses the documented 1:10 pasta/water rule and 1 tsp salt per 100 g pasta (so 200 g gives 2 l and 2 tsp). `BOIL_RICE` currently uses 1.5 ml water per gram as a generic white-rice default; the ProductTemplate model does not yet distinguish rice varieties.

Tap water is a non-stock-managed process consumable and does not create a Product or shopping-list row. Derived salt is displayed as a practical amount and represents a PRESENCE requirement: it must be available, but no numeric amount is deducted. An explicit recipe salt allocation remains the single ingredient requirement for that usage.

## Composition inputs

`Rør fars` demonstrates the reusable composition shape: one `BASE` ingredient plus zero or more `ADDITIONS` members. Each member remains a binding to the existing RecipeIngredient with its own allocated quantity; no ingredient row is copied. Persisted member keys use `ADDITIONS:<binding-id>`, allowing arbitrary additions without fixed egg/onion/binder semantics. The same pattern can later support marinades, dressings, doughs, and fillings without adding a scripting language.

Process ingredient inputs may alternatively bind a Recipe-owned PreparedComponent.
Collapsed summaries prefer its human name; expanded rendering uses its concrete,
scaled contents. Passing the component does not allocate those ingredients again.
Input summaries show at most two meaningful inputs and append `X øvrige` for the rest.

## Active and passive duration

Processes expose canonical seconds for active work and passive waiting. Either duration can be fixed process metadata or reference a `DURATION` parameter such as `SIMMER_TIME`; a parameter reference is the single source of truth, so an advanced recipe override changes both the instruction and summary. Timing never scales with portions. Zero or absent values are omitted from summaries, for example `5 min aktiv · 15 min ventetid`.

## Process-contributed preparation

`preparation` entries reference a declared process parameter and contain a validated instruction template. Ingredient-set requirements expand once per selected member, retaining its concrete allocated quantity. At render time these entries are scaled with the selected portions, appended after recipe-authored preparation, and deduplicated deterministically by normalized instruction. Recipe-authored preparation is never replaced.

Recipe and RecipeTemplate detail views render each top-level PROCESS step as an accessible, collapsed `<details>` header containing only the process name and timing summary. Expanded content contains its subordinate bullet list, resolved quantities and heat, warnings, and completion criterion; top-level recipe numbering remains visually separate.

## Source and schema

The canonical reviewable source library is [`src/main/resources/seed/cooking-processes.json`](../src/main/resources/seed/cooking-processes.json). It contains the process key, equipment requirements, typed parameters and defaults, ordered instruction templates, and completion criterion. The legacy copy under `db/seed/` remains untouched as the immutable V19 input. Later changes, including `PAN_FRY_MEATBALLS` and the improved pasta process, are imported explicitly by V23; tests ensure the evolving canonical library remains a superset of the V19 snapshot.

Flyway migration `V18` creates these definition tables:

- `cooking_processes`
- `cooking_process_parameters`
- `cooking_process_steps`
- `cooking_process_equipment_requirements`

It also adds `TEXT`/`PROCESS` discrimination to recipe and template steps and creates `recipe_process_bindings` and `recipe_template_process_bindings`. `V19` imports seed version 1 once using deterministic UUIDs and validates every placeholder before inserting anything. `V20` connects ingredient bindings to the concrete RecipeIngredient/RecipeTemplateIngredient, allowing one ingredient row to be allocated partially across several processes. There is no startup synchronizer.

## Inspecting process data

```sql
SELECT id, process_key, name, active, updated_at
FROM cooking_processes
ORDER BY name;

SELECT cp.name, p.parameter_key, p.label, p.parameter_type, p.required,
       p.value_source, p.derived_rule, p.derived_from,
       p.unit, p.default_quantity, p.default_unit, p.default_duration_seconds,
       p.default_temperature_celsius, p.default_heat_level, p.sort_order
FROM cooking_process_parameters p
JOIN cooking_processes cp ON cp.id = p.cooking_process_id
ORDER BY cp.name, p.sort_order;

SELECT cp.name, s.sort_order, s.instruction_template,
       cp.completion_criteria_template
FROM cooking_process_steps s
JOIN cooking_processes cp ON cp.id = s.cooking_process_id
ORDER BY cp.name, s.sort_order;

SELECT cp.name, e.equipment_type, e.requirement_level
FROM cooking_process_equipment_requirements e
JOIN cooking_processes cp ON cp.id = e.cooking_process_id
ORDER BY cp.name, e.requirement_level, e.equipment_type;

SELECT r.name AS recipe, rs.sort_order, cp.name AS process,
       b.parameter_key, pt.name AS product, b.quantity, b.unit,
       b.duration_seconds, b.temperature_celsius, b.heat_level
FROM recipe_steps rs
JOIN recipes r ON r.id = rs.recipe_id
JOIN cooking_processes cp ON cp.id = rs.cooking_process_id
LEFT JOIN recipe_process_bindings b ON b.recipe_step_id = rs.id
LEFT JOIN product_templates pt ON pt.id = b.product_template_id
WHERE rs.step_type = 'PROCESS'
ORDER BY r.name, rs.sort_order, b.parameter_key;
```

## Changing or adding processes

1. Review and update the JSON source. Keep keys stable and uppercase; placeholders may only use declared parameter keys.
2. Add tests for the new or changed definition.
3. Create a new additive Flyway migration. Never edit `V18` or `V19` after they have been applied.
4. In that migration, update existing rows by stable `process_key`, or insert a new process with deterministic IDs. Update `updated_at` explicitly.
5. Run OpenAPI validation, the clean Gradle test/build, and inspect the joins above.

The source JSON documents the desired current library, while applied databases advance only through explicit migrations. Editing JSON alone does not silently rewrite production data.

## API and ownership

Authenticated clients can read definitions through `GET /v1/cooking-processes` and `GET /v1/cooking-processes/{id}`. There are no global mutation endpoints. Recipe ownership checks remain on the recipe aggregate, so process bindings cannot be edited independently or used to bypass recipe ownership.
