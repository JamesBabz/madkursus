# Codex project handover

Last reviewed: 2026-08-25. Repository state at review: `main` at `f638cb2` (`origin/main`), clean working tree before this file was added.

## 1. Purpose and current state

Madkursus is a Danish, single-deployment meal-planning and kitchen-management web application. It combines user-owned products and inventory, shopping lists, recipes, meal plans, cooking history, kitchen equipment, and a curated global library of product templates, recipe templates, and reusable cooking processes.

The application is functional end to end and is no longer just the minimal product/inventory skeleton described in parts of the README. It has:

- session-based registration/login and strict per-user ownership;
- product/inventory/shopping-list workflows with quantity, presence, and untracked semantics;
- user recipes, recipe-template copying, portion scaling, availability calculation, shopping-list generation, and inventory consumption when cooking;
- meal plans with planned/cooked/skipped states and preserved historical recipe names;
- reusable, typed cooking processes rendered into recipe instructions;
- prepared components, preparation steps, equipment requirements, and structured/scalable instruction text;
- a plain JavaScript/CSS PWA served by Spring Boot;
- controlled developer tooling for importing curated recipe templates.

The most recent completed work was:

1. adding persistent JDBC-backed HTTP sessions (`V34`);
2. repairing/locking down recipe-template migration history and adding `V35`;
3. fixing deletion of user recipes without corrupting historical meal-plan records;
4. importing and polishing the curated recipe **Karbonader med kartofler, gulerødder og brun pandesovs** through `V36`, plus dialog viewport/PWA asset adjustments.

There is no known uncommitted feature implementation or explicit in-progress branch. The next work should begin from the clean `main` state and first run the complete test suite on the new computer.

Handover verification note: on 2026-08-25, `cleanTest test` was attempted but did not reach test execution. Windows raised `AccessDeniedException` while Gradle read the project-local cache file `.gradle-user-home/caches/.../spring-data-commons-3.5.13.jar` during `compileJava`. This was an environment/cache permission failure, not a compile diagnostic or failing test assertion. Re-run from a healthy Gradle cache on the new machine; do not treat this handover as evidence that the current suite passed.

## 2. Architecture and important structure

This is Java 21 / Spring Boot 3.5, Gradle, PostgreSQL 17, Spring Data JPA, Flyway, Spring Security, Spring Session JDBC, OpenAPI Generator, and a framework-free HTML/CSS/JavaScript frontend.

The backend follows ports and adapters:

- `service/models`: framework-independent domain records/enums.
- `service/applications`: use cases, validation, ownership-sensitive behavior, rendering and calculations.
- `service/ports`: persistence/current-user interfaces.
- `inbound/rest`: generated-interface delegates and explicit DTO/domain mappers.
- `inbound/security`: Spring Security configuration and current-user adapter.
- `outbound/*`: port adapters, JPA entities/repositories, and mappers.
- `src/main/resources/openapi/madkursus-api.yaml`: authoritative HTTP contract. Generated sources live under `build/generated/openapi` and must not be committed or hand-edited.
- `src/main/resources/static`: the entire PWA frontend (`index.html`, `js/app.js`, CSS, manifest, service worker).
- `src/main/resources/db/migration`: immutable ordered database history, currently through V36 (including Java migrations in `src/main/java/db/migration`).
- `src/main/resources/seed`: evolving, reviewable canonical representation of current global system data.
- `src/main/resources/db/seed` and `db/migration/data`: historical migration inputs; these are snapshots, not current editable catalogs.
- `src/main/resources/recipe-templates`: readable source drafts for recently curated recipes.
- `tools/recipetemplate`: offline validation/import/migration-candidate CLI.
- `docs`: important design documentation. Read `system-seed-data.md`, `product-templates.md`, `recipe-templates.md`, `recipe-template-authoring.md`, `cooking-processes.md`, and `authentication.md` before changing their respective areas.

Application services deliberately do not depend on JPA types. REST DTO translation stays at the boundary. Persistence adapters rebuild aggregate child graphs rather than exposing repositories to services.

## 3. Decisions and requirements that must remain true

### Global catalogs are code, not runtime admin data

ProductTemplates, RecipeTemplates, and CookingProcesses are globally readable application-managed data. There are no public mutation endpoints by design. Their JSON files are reviewable desired-state sources, but startup never synchronizes them to the database. Every deployed data change needs an explicit additive Flyway migration.

Never edit an already published migration or its legacy seed input merely to make it match today's canonical JSON. New databases must reproduce the same historical states/checksums as deployed databases. Add a later migration instead. `RecipeTemplateRecoveryMigrationIntegrationTest` is the primary regression guard for this and intentionally migrates/version-checks intermediate states.

### Stable identities and copying semantics

- Stable catalog keys are uppercase `UPPER_SNAKE_CASE`; do not infer identity from a similar display title.
- ProductTemplate UUIDs historically derive from normalized names, but an existing template rename must preserve both ID and key. Do not recalculate identity.
- Creating a user Product from a ProductTemplate copies editable defaults and retains `sourceTemplateId`; later global changes do not mutate user Products.
- Adding a RecipeTemplate to a user's recipes makes an independent Recipe copy and retains `sourceTemplateId`. Later template changes do not rewrite copied Recipes.
- CookingProcess definitions are deliberately referenced, not snapshotted. Changing a process default/rule/text changes rendering for all recipes still referencing it; explicit recipe bindings remain local overrides.

### Quantities and inventory semantics

- Recipe quantities are authored per one portion and scale at rendering/calculation time.
- Preserve the authored practical RecipeUnit for display. Generic volume conversions are 1 tbsp = 15 ml, 1 tsp = 5 ml, 1 dl = 100 ml.
- Cross-dimensional conversion belongs to a ProductTemplate, never to a global assumption. The first rule is flour: 1 tbsp = 9 g (`V33`).
- `QUANTITY` means numeric stock, `PRESENCE` means only availability is relevant, and `UNTRACKED` means the ingredient can scale/render but creates no inventory, reservation, or automatic shopping requirement. Water is intentionally UNTRACKED.
- ProductTemplate `defaultUnit` and `defaultTrackingMode` are independent concepts and must not be conflated.
- `GRINDER_TURN` is its own dimension.

### Cooking-process model

Processes use typed parameters with explicit ownership:

- `INPUT`: recipe-provided input/allocation.
- `DEFAULT`: process-owned and not recipe-editable.
- `OVERRIDEABLE_DEFAULT`: hidden behind advanced editing and stored only when changed.
- `DERIVED`: calculated by a small typed Java rule; not stored unless advanced binding overrides it.

Do not introduce a generic expression language or frontend-derived cooking logic. Derived rules belong in typed Java (`CookingProcessDerivedRule`) and canonical process data. Current examples include potato water/salt, pasta's 1:10 water rule, and generic rice water. The rice rule is knowingly generic because product templates do not distinguish rice varieties.

Ingredient allocations and PreparedComponent allocations reference existing RecipeIngredient rows; they do not create duplicate stock requirements. A component passed to a process must not allocate its contents a second time. Composition uses expandable binding keys such as `ADDITIONS:<binding-id>`, not hard-coded egg/onion/binder roles.

Process timings do not scale with portions. Process-contributed preparation is appended to recipe-authored preparation and deterministically deduplicated; it never replaces authored preparation.

### Structured instructions

Any instruction containing a scalable recipe amount must use `RecipeStructuredInstruction` parts. Do not embed `"200 g tomatoes"` as static prose. Fixed dimensions/times such as 2 cm or 30 seconds remain static text. `scaledNumber` is the intentionally small typed mechanism for portion-dependent counts; there is no general expression syntax.

PROCESS steps own their complete action and should appear at the appropriate top-level position. Do not add an earlier duplicate TEXT action such as starting pasta water if `BOIL_PASTA` owns water, salt, and boiling. Parallel process scheduling is not implemented; document the sequential limitation instead of duplicating actions.

### Authentication/security

Authentication is server-side Spring Security `HttpSession`, persisted in PostgreSQL by Spring Session JDBC (`SPRING_SESSION*`, created by V34). Authentication/password data must not be put in local/session storage or readable cookies. The session cookie is HttpOnly, SameSite Strict, persistent for 30 days; server inactivity timeout is 30 days.

CSRF stays enabled. The frontend gets `GET /v1/auth/csrf` and sends `X-XSRF-TOKEN` for login, registration, logout, and mutations. API/auth responses must remain excluded from service-worker caching.

Registration availability is a server policy (`REGISTRATION_ENABLED`), not merely hidden UI. Production defaults to disabled. Use secure cookies behind HTTPS; direct local/plain-HTTP testing needs `SESSION_COOKIE_SECURE=false`.

Every user-owned lookup/mutation must remain scoped by the authenticated user ID. Avoid leaking existence of another user's IDs.

### Recipe deletion and historical plans

Deletion is intentionally nuanced. A recipe referenced by a currently PLANNED meal-plan entry cannot be deleted and returns a conflict. Historical COOKED/SKIPPED entries retain `recipe_name` but their nullable recipe reference is detached so the Recipe can be deleted. A skipped entry whose recipe was deleted cannot be reactivated.

Within the Recipe aggregate, dependent process/component/step/preparation/equipment children must be flushed before ingredients, then the parent. Ingredient-linked foreign keys make a bulk or unordered cascade deletion unsafe. See `RecipeService.delete`, `RecipeAdapterImpl.deleteByIdAndUserId`, `RecipeEntity.clearDependentChildren`, and the related tests. Only translate the expected `planned_recipes_recipe_id_fkey` violation into the user-facing active-plan conflict; rethrow unrelated integrity failures instead of hiding them.

## 4. Database and migration history

PostgreSQL is mandatory in normal use. Hibernate uses `ddl-auto: validate`; Flyway owns all schema changes. Spring Session schema initialization is explicitly disabled because V34 owns those tables.

Important evolution landmarks:

- V1-V10: base product/inventory schema, users/ownership, product templates, duplicate prevention, tracking/template origin.
- V11-V17: recipes, cook history, meal plans, kitchen equipment, historical planned-recipe preservation.
- V18-V21: CookingProcess schema and seed, ingredient bindings, recipe-template v2 upgrade.
- V22-V29: grinder turns, curated process/template changes, preparation/equipment, value ownership, timings, prepared components.
- V30-V33: structured instructions, untracked water, curated meatballs/pasta template, product-specific unit conversions.
- V34: persistent JDBC sessions.
- V35: update the meatballs/pasta template to rapeseed oil while preserving migration history.
- V36: curated karbonader recipe.

Two migration implementation styles coexist: SQL migrations under resources and Java migrations in package `db.migration`. Do not renumber casually; note the existing `V14_1` version. Always inspect all filenames before choosing the next version.

V32 had a historical-generation mistake around frying fat/product IDs. The repository's current V32 represents the intended historical V32 state, while V35 performs the later rapeseed-oil transition. Do not squash V35 into V32 or regenerate older children against current canonical data. The integration test asserts V21/V32/V33/V34/V35 progression, deterministic IDs, final rendering, and the ability to copy the template.

The recipe-template CLI produces `build/generated-recipe-template-migrations/V_NEXT__...sql`. `V_NEXT` is deliberately invalid as a final version: review the diff, choose the next unused Flyway number, and move/rename it into the migration directory. Generated update SQL preserves the parent template ID, deletes/recreates only template-owned children, and intentionally leaves user Recipes and `sourceTemplateId` untouched.

Recovery option `--regenerate-add` exists only for the case where canonical JSON was updated but its ADD migration definitely never deployed. It requires exact canonical equivalence and preserved parent identity. Never select it automatically and never use it if the ADD may exist in any environment.

Production data lives in the Compose named volume `postgres_data`. Never run `docker compose down -v` unless irreversible deletion of every database in that volume is intended. `POSTGRES_*` variables only initialize a fresh volume; changing them later does not change existing credentials or create databases.

## 5. API and frontend interaction

The OpenAPI file is contract-first. `openApiGenerate` builds Spring controllers/delegates/DTOs under `build`; handwritten delegates implement generated interfaces and mappers translate to/from domain records. Change the YAML first, regenerate, then update mapper/delegate/service/tests. Do not patch generated Java.

Current API areas include auth/CSRF; read-only product templates and cooking processes; CRUD products, inventory, shopping list, recipes, meal plans, and kitchen equipment; template-to-product/inventory/shopping-list/recipe creation; recipe/meal-plan requirement calculation; add-missing; cook; meal-plan status transitions.

The frontend is a same-origin PWA, not a separate Node project. `app.js` calls `/v1/*`, sends the session cookie automatically, and refreshes CSRF tokens as required. It renders recipe PROCESS steps as accessible collapsed `<details>` sections, keeping top-level recipe numbering visually separate. The service-worker cache version must be bumped whenever cached frontend assets change; `FrontendAssetsTest` guards several asset relationships. Recent `dialog-viewport.js` work compensates for mobile visual-viewport/keyboard behavior—test dialogs on a real mobile browser before simplifying it.

## 6. Build, test, and local run

Prerequisites: Java 21 and Docker/Compose. On Windows use the committed wrapper:

```powershell
docker compose up -d postgres
.\gradlew.bat cleanTest test --no-daemon --no-watch-fs --console=plain
.\gradlew.bat check --no-daemon --no-watch-fs --console=plain
.\gradlew.bat bootRun
```

The current Compose file requires `POSTGRES_BIND_ADDRESS`; for local use provide an explicit safe bind address (normally `127.0.0.1`) in `.env` or the environment. The app defaults to `jdbc:postgresql://localhost:5432/madkursus`, user/password `madkursus`. Health is `GET /api/health`; Swagger UI is `/swagger-ui.html` and generated docs `/v3/api-docs`.

Tests use Zonky embedded PostgreSQL, not H2, because migration and constraint behavior is PostgreSQL-specific. A complete suite exercises migrations and may be slower than unit-only runs. `check` also validates OpenAPI and verifies that the executable Boot JAR actually contains Spring Security. The plain JAR task is disabled deliberately to prevent deploying the dependency-free artifact.

Useful authoring commands:

```powershell
.\gradlew.bat processRecipeTemplateDraft --args="path\draft.json --dry-run"
.\gradlew.bat processRecipeTemplateDraft --args="path\draft.json"
.\gradlew.bat validateRecipeTemplateDraft --args="path\draft.json"
.\gradlew.bat generateRecipeAuthoringReference
```

`processRecipeTemplateDraft` is preferred. It decides ADD versus UPDATE only by stable key and warns, but does not update, on a duplicate normalized title. Validation must complete before any file is written. The tool does not start Spring, connect to a database, or deploy a migration.

## 7. Deployment/production

`Dockerfile` performs a Java 21 Gradle build and runs only the executable Boot JAR in a Java 21 JRE image as unprivileged user `madkursus`. Compose runs PostgreSQL 17 and the app; app-to-DB traffic uses the private service name `postgres`.

The documented target is an Ubuntu host reached over Tailscale. PostgreSQL may bind to the host's Tailscale IPv4 for development access but must never be router-forwarded/publicly exposed. Restrict port 5432 at the firewall to the Tailscale interface/devices. Port 8080 is exposed for initial testing; production should put the app behind HTTPS before enabling secure cookies.

Create `.env` from `.env.example`, use strong matching credentials, keep `REGISTRATION_ENABLED=false`, `SESSION_COOKIE_SECURE=true`, and use `jdbc:postgresql://postgres:5432/madkursus_prod`. If an old volume exists, create the production database explicitly rather than renaming/deleting a development DB. Back up the volume/database before migration or deployment work; no automated backup/restore setup is present in this repository.

No CI/CD, reverse-proxy, TLS certificate, secret manager, monitoring, or automated backup configuration is visible in the repository. Treat those as operational gaps, not implied completed setup.

## 8. Known limitations and technical debt

- Parallel cooking-process scheduling is not modeled. Recipes are a top-level sequential list even when real actions overlap.
- Rice uses a generic white-rice derived water rule because product varieties are not modeled.
- Global template/process removal has no safe public lifecycle/deactivation workflow. Prefer future deactivation over casual deletion because many historical/user references exist.
- CookingProcess edits intentionally have global rendering impact because definitions are not snapshotted; future versioning would require a deliberate model change.
- The frontend is a large framework-free `app.js`; preserve its simplicity but expect increasing maintenance cost as screens grow.
- Several Java files use unusually compressed one-line formatting. Follow the local style where editing nearby code, but do not perform unrelated mass reformatting because it obscures behavioral diffs.
- README introductory/API sections lag the breadth of the implementation. Domain docs are generally more authoritative.
- Production hardening items listed above (HTTPS proxy, backups, monitoring, CI/CD) are not implemented here.
- No AI recipe-generation integration exists. If added later, AI output must pass the exact same structured JSON validator, deterministic import workflow, human diff review, and explicit migration step as human-authored drafts.

## 9. Failed approaches and lessons to retain

- Editing current canonical seed JSON alone does not update any deployed DB. Conversely, pointing old migrations at evolving canonical JSON makes fresh installs differ from old installations. Keep historical snapshots immutable and advance with migrations.
- A canonical ADD imported without its migration can be misclassified as UPDATE on the next run. Use the narrowly guarded `--regenerate-add` recovery path only when the ADD was never deployed.
- Recipe aggregate deletion cannot rely on a simple repository delete/cascade because process bindings and prepared-component allocations reference ingredients. Delete/flush dependent children before ingredients.
- Do not catch every `DataIntegrityViolationException` and report "active meal plan"; that hid unrelated schema bugs. Match only the expected constraint.
- Do not store or derive scalable ingredient amounts in prose or frontend JavaScript; this caused drift between portions, inventory math, and displayed instructions. Structured parts and backend rendering are the source of truth.
- Do not create duplicate TEXT steps for actions owned by a process. This creates contradictory timing/ingredients and double-count risk.
- Do not deploy the `*-plain.jar`; it lacks runtime dependencies/security. The build disables it and verifies the Boot JAR.
- Secure session cookies do not work over direct plain HTTP. Temporarily disable the secure flag only for that test path, then restore it for HTTPS production.

## 10. Current point and next logical tasks

There is no recorded partially implemented feature. The safest continuation sequence is:

1. Clone on the new machine, confirm Java 21/Docker, copy required `.env` values securely (never commit the real `.env`), and run `cleanTest test` then `check`.
2. Verify Flyway against a backup/copy of the actual target database through V36 and confirm persistent sessions and both newest curated recipes render/copy correctly.
3. Smoke-test login persistence, CSRF mutations, recipe deletion in planned vs historical meal plans, mobile dialogs, offline/PWA refresh, shopping calculations, and cooking/inventory consumption.
4. Before selecting new product/process/recipe work, read the relevant domain doc and follow the canonical JSON + explicit migration + tests workflow.
5. If deployment is moving too, inventory and transfer non-repository state separately: production `.env`/secrets, DNS/reverse-proxy config, Tailscale/firewall rules, PostgreSQL backup/restore, and any server-specific runbook. None of those secrets or external states can safely be reconstructed from Git.

Reasonable backlog directions inferred from documented limitations—not confirmed commitments—are process parallelism/scheduling, rice-variety modeling, safe global-template deactivation/versioning, frontend modularization, CI/CD, HTTPS/reverse proxy, monitoring, and automated backups. Do not treat these as approved priorities without asking the owner.

## 11. Conversation context and uncertainty

This handover was produced from the repository, its Git history, tests, documentation, and the conversation context available in the current Codex task. The user's explicit priority is continuity with minimal context loss, current code as source of truth, no functionality changes during handover, and special emphasis on reasoning/pitfalls rather than restating code.

No earlier project-development conversation transcript was available in this task beyond the handover request itself. Therefore personal preferences, rejected feature proposals, exact production host details, secret values, uncommitted verbal backlog, and the motivation behind decisions not recorded in docs/commits could not be recovered. Statements labeled as inferred backlog above are intentionally not presented as requirements.

## 12. New-instance safety checklist

Before changing behavior, a new Codex instance should establish:

- the current branch/worktree and whether the user has local changes;
- whether the real production database has reached V36 and whether any migration was manually repaired;
- whether development only is moving or production operations/secrets/data must also move;
- the desired next feature and priority, since no active unfinished feature is recorded;
- whether a change affects canonical system data, copied user data, global process rendering, inventory math, migration history, service-worker caching, or ownership/security;
- tests at both the application-service boundary and real PostgreSQL/migration boundary for any such change.

With the repository plus this file, the most important remaining missing context is external state: actual databases/backups, environment secrets, server/Tailscale/firewall/TLS configuration, and any decisions that existed only in unavailable prior chat messages. Ask the owner for those rather than guessing.
