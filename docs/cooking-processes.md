# CookingProcesses

CookingProcesses are shared, application-managed instruction definitions. Recipes and recipe templates reference a process and store only their concrete parameter bindings. The process text is deliberately not copied: a versioned change to a global process therefore changes the rendering of every current and future `PROCESS` step that references it. Binding overrides remain local to the recipe or template.

## Source and schema

The canonical reviewable source library is [`src/main/resources/seed/cooking-processes.json`](../src/main/resources/seed/cooking-processes.json). It contains the process key, equipment requirements, typed parameters and defaults, ordered instruction templates, and completion criterion. The identical legacy copy under `db/seed/` remains untouched because the already-applied V19 migration reads it; tests ensure the two representations have not drifted.

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
