# Kontrolleret RecipeTemplate-forfatterflow

Globale RecipeTemplates ændres som kildekode og aldrig via en runtime- eller admin-API:

```text
struktureret JSON-kladde → validering → menneskelig diff → kanonisk JSON
→ eksplicit Flyway-migration → test → deployment
```

Værktøjet starter ikke Spring Boot, forbinder ikke til databasen og udfører ikke migrationen.

## Kladdeformat

Formatet er det samme læsbare format som `seed/recipe-templates.json`. Se
[`examples/recipe-template-draft.json`](examples/recipe-template-draft.json).

- `key`, ingredienskeys og PreparedComponent-keys er stabile `UPPER_SNAKE_CASE`-referencer.
- `productTemplate` bruger ProductTemplate-katalogets læsbare `key` (eksisterende navn accepteres for bagudkompatibilitet).
- Alle mængder er for én portion og bruger en `RecipeUnit`: `GRAM`, `MILLILITER`, `DECILITER`, `TEASPOON`, `TABLESPOON`, `PIECE` eller `GRINDER_TURN`.
- `preparation` kan indeholde tekst eller `{ "instruction": "...", "component": "COMPONENT_KEY" }`.
- `preparedComponents[].ingredients` allokerer eksisterende ingredienser; de opretter ikke nye lagerkrav.
- PROCESS-trin bruger en stabil CookingProcess-key. En binding bruger enten `ingredient`, `component` eller en dokumenteret override-værdi.
- `equipmentRequirements` kan bruge en læsbar equipment-type eller et objekt med `equipmentType`/`label`.

### Skalerbare instruktioner

Preparation og TEXT-trin accepterer fortsat en almindelig `instruction`-streng. Når teksten skal
vise en opskriftsmængde, bruges i stedet strukturerede dele:

```json
{"instruction":{"parts":[
  {"text":"Mål "},
  {"ingredient":"MEAT","quantity":200,"unit":"GRAM"},
  {"text":", "},
  {"ingredient":"EGG","quantity":0.5,"unit":"PIECE"},
  {"text":" og "},
  {"ingredient":"FLOUR","quantity":1,"unit":"TABLESPOON"},
  {"text":" op til farsen."}
]}}
```

En ingredient-del uden `quantity`/`unit` viser hele RecipeIngredient-mængden. Angives de, er det
en delreference, som skal være enhedskompatibel og højst ingrediensens total. `{ "component":
"MEATBALL_MIX" }` viser komponentens navn uden at folde dens indhold ud. `{ "scaledNumber": 6 }`
er den enkle typede løsning til portionsafhængige antal: 6, 12 og 24 ved henholdsvis 1, 2 og 4
portioner. Der findes intet generisk udtrykssprog.

Indlejr aldrig en skalerbar Recipe-mængde direkte i statisk tekst. `"Tilsæt 200 g tomater"` er
forkert; brug en ingredient-del. Tal, som beskriver en uændret størrelse eller tid — fx `2 cm`,
`30 sekunder` eller `vend efter 5 minutter` — må og skal forblive statisk tekst.

Når en CookingProcess ejer hele handlingen, placeres PROCESS-trinnet det rigtige sted i
topniveauets rækkefølge. Opret ikke et tidligere TEXT-trin som “sæt pastavandet over”, hvis et
senere `BOIL_PASTA` allerede ejer vand, salt og kogning. Parallel procesplanlægning findes endnu
ikke; begrænsningen dokumenteres frem for at duplikere handlingen.

Den samme JSON-kontrakt er den tiltænkte grænse for eventuelt fremtidigt AI-genereret indhold: AI-output skal gennem præcis samme validator og review. Der er ingen AI-integration i værktøjet.

## Kommandoer

Windows/IntelliJ-terminal:

```powershell
.\gradlew.bat processRecipeTemplateDraft --args="my-recipe.json --dry-run"
.\gradlew.bat processRecipeTemplateDraft --args="my-recipe.json"
.\gradlew.bat validateRecipeTemplateDraft --args="docs/examples/recipe-template-draft.json"
.\gradlew.bat importRecipeTemplateDraft --args="my-recipe.json"
.\gradlew.bat importRecipeTemplateDraft --args="my-recipe.json --update"
.\gradlew.bat generateRecipeAuthoringReference
```

`processRecipeTemplateDraft` er den anbefalede kommando. Den vælger udelukkende ADD eller UPDATE
ud fra den stabile RecipeTemplate-key. `--dry-run` viser handling, eksisterende template, kandidatnavn
og en kort optælling uden at ændre filer. En ens eller lignende titel med en anden key medfører aldrig
automatisk update; værktøjet advarer ved en identisk normaliseret titel.

Validate-only ændrer ingen filer og er nyttig til fejlsøgning. De lavere `importRecipeTemplateDraft`
kommandoer bevares til eksplicit kontrol: en eksisterende key kræver `--update`, mens `--update`
afviser en ukendt key.

## Validering

Validatoren kontrollerer blandt andet obligatoriske/unikke keys, ProductTemplate- og CookingProcess-referencer, RecipeUnits, positive mængder, komponent- og forberedelsesreferencer, kompatible enhedsdimensioner, samlet over-allokering, obligatoriske proces-inputs, inputtyper samt at kun overrideable procesfelter overskrives. En komponent, der gives videre til en proces, tælles ikke endnu en gang.

Fejl stopper før nogen filskrivning. Der skabes derfor hverken en delvis kanonisk ændring eller en delvis migrationskandidat.

## Import, migration og review

En godkendt import erstatter kun objektet med den valgte recipe-key eller tilføjer ét nyt objekt. Teksten for andre templates bevares. Derefter genereres en deterministisk kandidat i:

```text
build/generated-recipe-template-migrations/V_NEXT__add_<key>.sql
build/generated-recipe-template-migrations/V_NEXT__update_<key>.sql
```

`V_NEXT` er bevidst ugyldig som endelig versionsbeslutning. Gennemgå SQL, vælg næste ledige Flyway-version og flyt/omdøb filen til `src/main/resources/db/migration`. Update-SQL bevarer RecipeTemplate-id'et og sletter/genopretter kun template-ejede children. ProductTemplates, CookingProcesses, `Recipe.sourceTemplateId` og allerede kopierede bruger-Recipes berøres ikke.

### Recovery: canonical ADD blev aldrig deployet

Hvis en ADD-import allerede nåede at opdatere `recipe-templates.json`, men den genererede migration
fejlede og aldrig blev anvendt i noget miljø, må den normale key-baserede proces ikke bruges: den vil
korrekt klassificere key'en som UPDATE. Regenerér i stedet den oprindelige ADD-kandidat eksplicit:

```powershell
.\gradlew.bat processRecipeTemplateDraft --args="path/to/draft.json --regenerate-add"
```

Recovery-tilstanden kræver, at key'en allerede findes kanonisk, at draften svarer præcist til det
kanoniske objekt (bortset fra `id`), og at template-ID'et ikke kolliderer. Den ændrer aldrig den
kanoniske JSON og genererer kun `V_NEXT__add_<key>.sql` med det bevarede parent-ID og korrigerede,
key-navngivne deterministiske child-ID'er. Hvis en ældre kurateret child-graf allerede ligger under
samme stabile parent-ID, erstattes kun denne template-ejede graf, så kandidatens sortering og children
kan indsættes uden kollision. Optionen må kun bruges, når ADD-migrationen med sikkerhed aldrig blev
deployet; den vælges aldrig automatisk.

Ingredient-baserede CookingProcess-bindings gemmer både den lokale RecipeIngredient-reference og
dens ProductTemplate-reference. Generatoren validerer bindingens komplette runtime-form med samme
semantiske validator som process-rendereren, før canonical JSON eller migrationskandidaten skrives.
Det lukker hullet, hvor en draft kunne være syntaktisk gyldig, men først fejle ved åbning af templaten.

## Kort arbejdsgang

1. Modtag eller opret `my-recipe.json`.
2. Kør eventuelt `processRecipeTemplateDraft --args="my-recipe.json --dry-run"`.
3. Kør `processRecipeTemplateDraft --args="my-recipe.json"`.
4. Gennemgå `git diff` og den genererede SQL-kandidat.
5. Omdøb bevidst `V_NEXT` til næste ledige Flyway-version.
6. Kør clean build og relevante databaseintegrationstests.
7. Commit og deploy via det normale, menneskeligt godkendte releaseflow.

Direkte databaseændringer frarådes, fordi de ikke kan reproduceres, reviewes eller anvendes sikkert på øvrige miljøer.

## Ekstern/AI-forfatter

Generér først den deterministiske forfatterreference:

```powershell
.\gradlew.bat generateRecipeAuthoringReference
```

Resultatet ligger i `build/recipe-authoring/recipe-authoring-reference.json` og genereres direkte
fra `product-templates.json`, `cooking-processes.json`, `RecipeUnit` og `EquipmentType`. Filen
indeholder ProductTemplate-keys, procesinput/defaults/afledte regler/overrides, enheder,
udstyrstyper og den kompakte RecipeTemplateDraft-kontrakt. Katalogernes SHA-256 hashes gør det
let at se, hvilket kanonisk grundlag referencen blev bygget fra.

Det tilsigtede flow er:

```text
afprøvet opskrift
→ ekstern forfatter modtager opskriften + den genererede reference
→ én RecipeTemplateDraft JSON
→ validateRecipeTemplateDraft
→ menneskelig review
→ importRecipeTemplateDraft
→ Flyway-review → tests → deployment
```

ProductTemplate- og CookingProcess-keys skal komme fra den genererede reference og må aldrig
opfindes. AI- eller andet eksternt output må aldrig importeres uden validate-only og menneskelig
gennemgang. Referencen indeholder kun forfatterviden — ingen credentials, miljøkonfiguration,
databaseforbindelser, brugerdata, Inventory eller MealPlans.
