const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/app.js'), 'utf8');
function productionFunction(name) {
  const lines = source.split(/\r?\n/);
  const start = lines.findIndex(line => line.startsWith(`function ${name}(`) || line.startsWith(`async function ${name}(`));
  assert.ok(start >= 0, `Missing function ${name}`);
  if (lines[start].endsWith('}')) return lines[start];
  const end = lines.findIndex((line, index) => index > start && line === '}');
  return lines.slice(start, end + 1).join('\n');
}
function element() {
  return {children: [], textContent: '', append(...children) { this.children.push(...children); },
    replaceChildren(...children) { this.children = children; }};
}

for (const template of [false, true]) {
  for (const [base, unit, expected] of [[125, 'GRAM', ['125 g', '250 g', '375 g']],
    [0.5, 'PIECE', ['½ stk.', '1 stk.', '1½ stk.']]]) {
    test(`${template ? 'RecipeTemplate' : 'Recipe'} ${base} ${unit}: portions up and down`, async () => {
      const nodes = new Map();
      const requests = [];
      const context = vm.createContext({
        document: {createElement: element, querySelector(selector) {
          if (!nodes.has(selector)) nodes.set(selector, element());
          return nodes.get(selector);
        }},
        recipeUnitLabels: {GRAM: 'g', PIECE: 'stk.'},
        RECIPE_API: '/v1/recipes', RECIPE_TEMPLATE_API: '/v1/recipe-templates',
        currentRecipe: {id: 'recipe'}, currentRecipeTemplate: {id: 'recipe'},
        renderCarbohydrates() {}, renderUnknownCarbohydrates() {},
        async jsonRequest(url) {
          requests.push(url);
          const portions = Number(new URL(url, 'http://test').searchParams.get('portions'));
          // Detail endpoints return a projection already scaled by the backend.
          return {id: 'recipe', ingredients: [{quantity: base * portions, unit,
            productTemplate: {name: 'Ingredient'}, sortOrder: 1}], steps: []};
        }
      });
      for (const name of ['scaledDecimal', 'danishDecimal', 'recipeUnitLabel',
        'renderRecipeDetail', 'renderRecipeTemplateDetail', 'renderRecipeTemplateMeta',
        'loadRecipeTemplateDetail', 'reloadCurrentRecipeForPortions']) {
        vm.runInContext(productionFunction(name), context);
      }
      for (const portions of [1, 2, 3, 2, 1, 3, 1]) {
        context.recipePortions = context.recipeTemplatePortions = portions;
        await vm.runInContext(template
          ? 'loadRecipeTemplateDetail("recipe", recipeTemplatePortions)'
          : 'reloadCurrentRecipeForPortions()', context);
        const selector = template ? '#recipe-template-detail-ingredients' : '#recipe-detail-ingredients';
        const amount = () => nodes.get(selector).children[0].children[1].textContent;
        assert.equal(amount(), expected[portions - 1]);
        vm.runInContext(template ? 'renderRecipeTemplateDetail()' : 'renderRecipeDetail()', context);
        assert.equal(amount(), expected[portions - 1], 'Repeated rendering must not accumulate scaling');
        assert.equal((template ? context.currentRecipeTemplate : context.currentRecipe).ingredients[0].quantity,
          base * portions, 'Rendering must preserve the API quantity');
      }
      assert.deepEqual(requests.map(url => Number(new URL(url, 'http://test').searchParams.get('portions'))),
        [1, 2, 3, 2, 1, 3, 1]);
    });
  }
}
