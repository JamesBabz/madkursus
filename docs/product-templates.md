# ProductTemplates

## Kanonisk kilde og identitet

Det komplette globale katalog vedligeholdes i
`src/main/resources/seed/product-templates.json`. Kilden indeholder 384 poster
og viser for hver template dens stabile `key`, eksisterende UUID, navn, kategori,
naturlige måleenhed, standard-lagertilstand, aliaser og `common`-flag.

UUID'erne er de samme som i V6 og V14.1 og beregnes som:

```text
UUID.nameUUIDFromBytes(
  UTF-8("madkursus-template:" + name.trim().toLowerCase(Locale.ROOT))
)
```

Navnet er derfor historisk identitetsbærende. Omdøb ikke en template ved at
beregne et nyt UUID eller en ny key; en eventuel navneændring skal bevare både
eksisterende `id` og `key` og udføres med en eksplicit Flyway `UPDATE`.

Den gamle V6-kilde under `db/seed/` forbliver urørt, fordi en ny database stadig
skal kunne afvikle den allerede publicerede migration med samme checksum og
resultat. Den kanoniske kilde samler sluttilstanden fra V6, V10 og V14.1.
Der er ingen runtime-synkronisering. Denne oprydning kræver derfor ingen ny
datamigration og skriver ikke til brugerdata.

## Datamodel og semantik

- `product_templates`: navn, kategori, `default_unit`,
  `default_tracking_mode` og `common`.
- `product_template_aliases`: dansk søgemetadata med original og normaliseret
  aliastekst.
- `products.source_template_id`: historisk reference fra et brugerejet Product.

`defaultUnit` beskriver, hvordan ingrediensen naturligt måles: `GRAM`,
`MILLILITER` eller `PIECE`. `defaultTrackingMode` beskriver kun standarden ved
oprettelse af et Product: `QUANTITY` eller `PRESENCE`. Eksempelvis er Salt målt i
gram, men spores som tilstedeværelse. De to begreber må ikke sammenblandes.

Ved oprettelse kopieres navn, kategori, defaultUnit og defaultTrackingMode samt
ProductTemplate-ID'et til `sourceTemplateId`. Aliaser kopieres ikke. Det nye
Product er derefter uafhængigt; senere templateændringer synkroniserer ikke
brugerens navn, enhed eller tracking mode.

Kategorierne følger `ProductCategory` i domænet. Kildens enum-værdier valideres
direkte mod domæne-enums i testene.

## Aliaser

Aliaser anvendes kun ved katalogsøgning. Eksempler:

- `oksefars` → Hakket oksekød
- `soya` → Sojasauce
- `maizena` → Majsstivelse

Danske tegn bevares i både navn og alias. Det eneste bevidste overlappende alias
er `oliven`, som matcher både Grønne oliven og Sorte oliven; brugerens valg er
derfor nødvendigt.

## Sikker ændringsproces

### Tilføj

1. Tilføj posten til den kanoniske JSON med unik key og et deterministisk UUID.
2. Opret en ny additiv Flyway-migration, som indsætter template og aliaser med
   præcis samme UUID.
3. Udvid validering/tests, kør rent build og verificér søgning før deploy.

### Opdatér defaults eller aliaser

1. Opdatér den kanoniske JSON.
2. Opret en ny Flyway-migration med målrettede `UPDATE`/aliasændringer efter det
   eksisterende UUID eller en stabil key-strategi dokumenteret i migrationen.
3. Ændr ikke eksisterende Products; nye defaults gælder kun fremtidige kopier.

### Fjernelse

En ProductTemplate kan være refereret af Products, RecipeIngredients,
RecipeTemplateIngredients og procesbindings. En casual `DELETE` er derfor ikke
en sikker arbejdsgang. Undersøg alle referencer først, og foretræk en fremtidig
deaktiveringsmekanisme, som bevarer historiske relationer. Denne opgave indfører
ingen sletning eller ny deaktiveringsadfærd.

## PostgreSQL-inspektion

```sql
SELECT id, name, normalized_name, category, default_unit,
       default_tracking_mode, common
FROM product_templates
ORDER BY name;
```

```sql
SELECT pt.name, a.alias, a.normalized_alias
FROM product_template_aliases a
JOIN product_templates pt ON pt.id = a.template_id
ORDER BY pt.name, a.alias;
```

```sql
SELECT default_tracking_mode, COUNT(*)
FROM product_templates
GROUP BY default_tracking_mode
ORDER BY default_tracking_mode;
```

```sql
SELECT category, COUNT(*)
FROM product_templates
GROUP BY category
ORDER BY category;
```

```sql
SELECT p.id, p.name AS product, p.inventory_tracking_mode,
       pt.id AS template_id, pt.name AS template
FROM products p
LEFT JOIN product_templates pt ON pt.id = p.source_template_id
WHERE p.source_template_id IS NOT NULL
ORDER BY pt.name, p.name;
```

```sql
SELECT rt.name AS recipe_template, rti.sort_order, pt.id, pt.name,
       rti.quantity, rti.unit
FROM recipe_template_ingredients rti
JOIN recipe_templates rt ON rt.id = rti.recipe_template_id
JOIN product_templates pt ON pt.id = rti.product_template_id
ORDER BY rt.name, rti.sort_order;
```

Før en eventuel udfasning bør også bindings og brugeropskrifter undersøges:

```sql
SELECT pt.id, pt.name,
       COUNT(DISTINCT p.id) AS products,
       COUNT(DISTINCT ri.id) AS recipe_ingredients,
       COUNT(DISTINCT rti.id) AS template_ingredients,
       COUNT(DISTINCT rpb.id) AS recipe_process_bindings,
       COUNT(DISTINCT rtpb.id) AS template_process_bindings
FROM product_templates pt
LEFT JOIN products p ON p.source_template_id = pt.id
LEFT JOIN recipe_ingredients ri ON ri.product_template_id = pt.id
LEFT JOIN recipe_template_ingredients rti ON rti.product_template_id = pt.id
LEFT JOIN recipe_process_bindings rpb ON rpb.product_template_id = pt.id
LEFT JOIN recipe_template_process_bindings rtpb ON rtpb.product_template_id = pt.id
GROUP BY pt.id, pt.name
ORDER BY pt.name;
```
