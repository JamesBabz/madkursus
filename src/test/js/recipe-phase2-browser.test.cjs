const {test} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');

const template = {id: 'rice', name: 'Ris', defaultUnit: 'GRAM'};
function recipe(portions = 1) {
  return {id: 'one', name: 'Ris med grønt', description: 'En enkel ret med sprøde grøntsager.',
    ingredients: [{id: 'ingredient', productTemplate: template, quantity: 125 * portions, unit: 'GRAM', sortOrder: 1}],
    preparedComponents: [{id: 'component', key: 'RICE', name: 'Kogte ris', sortOrder: 1,
      ingredients: [{id: 'allocation', recipeIngredientId: 'ingredient', productTemplate: template, quantity: 125 * portions, unit: 'GRAM', sortOrder: 1}], preparationSteps: []}],
    steps: [{id: 'step', type: 'TEXT', instruction: 'Vend ris og grønt sammen.', sortOrder: 1}],
    preparationSteps: [], equipmentRequirements: [], equipment: ['Gryde'],
    carbohydrates: {perPortionGrams: 30, totalGrams: 30 * portions, complete: false, unknownIngredientCount: 1,
      ingredients: [{name: 'Ris', productTemplateId: 'rice', known: false}]}};
}

async function fixture(width = 390) {
  const browser = await chromium.launch({headless: true, channel: process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'});
  const page = await browser.newPage({viewport: {width, height: 844}, serviceWorkers: 'block', reducedMotion: 'reduce'});
  const state = {patches: [], requests: [], added: false, errors: [], failBase: false, empty: false, completeNutrition: false};
  page.on('pageerror', error => state.errors.push(error.message));
  await page.route('http://madkursus.test/**', async route => {
    const request = route.request(), url = new URL(request.url());
    if (url.pathname.startsWith('/v1/')) {
      state.requests.push(`${request.method()} ${url.pathname}${url.search}`);
      let body = [];
      if (url.pathname.endsWith('/me')) body = {id: 'user', username: 'Test', admin: false};
      if (url.pathname.endsWith('/csrf')) body = {token: 'csrf'};
      if (url.pathname.endsWith('/registration-status')) body = {enabled: false};
      if (url.pathname === '/v1/recipes') body = state.empty ? [] : [recipe(), ...Array.from({length: 8}, (_, i) => ({...recipe(), id: `other-${i}`, name: `Suppe ${i + 1}`, description: i ? '' : 'En varm suppe.'}))];
      if (url.pathname === '/v1/recipes/one') {
        if (request.method() === 'PATCH') state.patches.push(request.postDataJSON());
        if (state.failBase && url.searchParams.get('portions') === '1') return route.fulfill({status: 500, json: {message: 'Midlertidig fejl'}});
        body = recipe(Number(url.searchParams.get('portions') || 1));
      }
      if (url.pathname === '/v1/recipe-templates') {
        const catalog = [{id: 'catalog', name: 'Grøn risret', description: 'Inspiration med grønt.', added: state.added, userRecipeId: state.added ? 'one' : null}, {id: 'other', name: 'Tomatsuppe', added: true, userRecipeId: 'one'}];
        body = catalog.filter(value => value.name.toLowerCase().includes((url.searchParams.get('query') || '').toLowerCase()));
      }
      if (url.pathname === '/v1/recipe-templates/catalog') body = {...recipe(Number(url.searchParams.get('portions') || 1)), id: 'catalog', name: 'Grøn risret', added: state.added, userRecipeId: state.added ? 'one' : null};
      if (url.pathname === '/v1/recipe-templates/catalog/add-to-my-recipes') {state.added = true; body = recipe();}
      if (body.carbohydrates && state.completeNutrition) body.carbohydrates = {...body.carbohydrates, complete: true, unknownIngredientCount: 0, ingredients: []};
      return route.fulfill({json: body});
    }
    try {
      const file = path.join(__dirname, '../../main/resources/static', url.pathname === '/' ? 'index.html' : url.pathname);
      return route.fulfill({body: await fs.readFile(file), contentType: url.pathname.endsWith('.js') ? 'text/javascript' : url.pathname.endsWith('.css') ? 'text/css' : 'text/html'});
    } catch {return route.fulfill({status: 404, body: ''});}
  });
  await page.goto('http://madkursus.test/'); await page.locator('#application').waitFor();
  return {browser, page, state};
}

test('editing after repeated portion changes saves per-portion ingredients and component allocations', async () => {
  const {browser, page, state} = await fixture();
  try {
    await page.evaluate(() => openRecipe('one', 3));
    await page.locator('#recipe-ingredients-section > summary').click();
    await page.locator('#recipe-portions-down').click();
    await page.waitForFunction(() => document.querySelector('#recipe-portions').textContent.includes('2'));
    await page.locator('#recipe-portions-up').click();
    await page.waitForFunction(() => document.querySelector('#recipe-portions').textContent.includes('3'));
    assert.match(await page.locator('#recipe-detail-ingredients').textContent(), /375/);
    if (await page.locator('#recipe-management').count()) await page.locator('#recipe-management > summary').click();
    await page.locator('#edit-recipe').click();
    await page.locator('#recipe-editor-dialog').waitFor();
    await page.locator('#recipe-form button[type="submit"]').click();
    await page.waitForFunction(() => !document.querySelector('#recipe-editor-dialog').open);
    assert.equal(state.patches.length, 1);
    assert.equal(Number(state.patches[0].ingredients[0].quantity), 125);
    assert.equal(Number(state.patches[0].preparedComponents[0].ingredients[0].quantity), 125);
    assert.equal(state.errors.length, 0);
  } finally {await browser.close();}
});

for (const width of [320, 390, 1280]) {
  test(`compact browsing, local search, catalog copying and contextual detail at ${width}px`, async () => {
    const {browser, page, state} = await fixture(width);
    try {
      await page.evaluate(() => showView('recipes'));
      const cards = page.locator('#recipe-list .collection-recipe-card');
      await cards.nth(8).waitFor();
      const box = await cards.first().boundingBox();
      assert.ok(box.height < 135, 'Useful card replaces the former 160px minimum plus decorative header');
      assert.ok(box.height >= 48);
      assert.equal(await cards.first().evaluate(node => getComputedStyle(node, '::before').content), 'none');
      if (width < 600) {
        const visible = await cards.evaluateAll(nodes => nodes.filter(node => {const r = node.getBoundingClientRect(); return r.top >= 0 && r.bottom < innerHeight - 130;}).length);
        assert.ok(visible >= 3, 'Several complete recipes fit above floating actions and navigation');
      }
      const requestsBeforeSearch = state.requests.length;
      await page.locator('#recipe-library-search').fill('SPRØDE');
      assert.equal(await cards.count(), 1);
      assert.equal(state.requests.length, requestsBeforeSearch, 'Personal search is local');
      await page.locator('#recipe-library-search').fill('ikke fundet');
      assert.equal(await cards.count(), 0);
      assert.equal(await page.locator('#recipes-search-empty').isVisible(), true);
      assert.equal(await page.locator('#recipes-empty').isVisible(), false);
      await page.locator('#recipe-library-search').fill('ris');
      await cards.first().press('Enter');
      await page.locator('#recipe-detail-dialog').waitFor();
      assert.equal(await page.locator('#recipe-management').evaluate(node => node.open), false);
      assert.equal(await page.locator('#edit-recipe').isVisible(), false);
      assert.equal(await page.locator('#recipe-nutrition-section').evaluate(node => node.open), false);
      assert.equal(await page.locator('#recipe-carbohydrates').isVisible(), false);
      assert.equal(await page.locator('#recipe-nutrition-section > .nutrition-warning').isVisible(), false);
      await page.locator('#recipe-nutrition-section > summary').click();
      await page.evaluate(() => renderRecipeDetail());
      assert.equal(await page.locator('#recipe-nutrition-section').evaluate(node => node.open), true);
      assert.equal(await page.locator('#recipe-nutrition-section > .nutrition-warning').isVisible(), true);
      await page.locator('#recipe-carbohydrates details > summary').click();
      assert.match(await page.locator('#recipe-carbohydrates').textContent(), /Ris/);
      for (const section of ['ingredients', 'preparation', 'equipment']) await page.locator(`#recipe-${section}-section > summary`).click();
      assert.match(await page.locator('#recipe-detail-preparation').textContent(), /250 g/);
      assert.match(await page.locator('#recipe-detail-equipment').textContent(), /Gryde/);
      await page.locator('#recipe-management > summary').press('Enter');
      await page.locator('#delete-recipe').click();
      assert.equal(await page.locator('#delete-recipe-confirmation').isVisible(), true);
      assert.equal(state.requests.filter(request => request.startsWith('DELETE')).length, 0);
      await page.locator('#keep-recipe').click();
      assert.equal(await page.locator('#delete-recipe-confirmation').isVisible(), false);
      assert.ok(await page.locator('#recipe-detail-dialog').evaluate(node => node.scrollWidth <= node.clientWidth));
      await page.locator('#close-recipe-detail').click();
      await page.locator('#show-recipe-templates').click();
      const catalogCards = page.locator('#recipe-template-list .collection-recipe-card');
      await catalogCards.nth(1).waitFor();
      assert.match(await catalogCards.nth(1).textContent(), /Tilføjet/);
      await page.locator('#recipe-template-catalog-search').fill('grøn');
      await page.waitForFunction(() => document.querySelectorAll('#recipe-template-list button').length === 1);
      await catalogCards.first().click();
      await page.locator('#recipe-template-detail-dialog').waitFor();
      assert.equal(await page.locator('#recipe-template-nutrition-section').evaluate(node => node.open), false);
      assert.equal(await page.locator('#recipe-template-nutrition-section > .nutrition-warning').isVisible(), false);
      await page.locator('#add-recipe-template').click();
      await page.waitForFunction(() => document.querySelector('#add-recipe-template').textContent.includes('Åbn'));
      assert.equal(state.requests.filter(request => request.startsWith('POST /v1/recipe-templates/catalog/add')).length, 1);
      await page.locator('#close-recipe-template-detail').click();
      assert.match(await catalogCards.first().textContent(), /Inspiration med grønt/);
      assert.match(await catalogCards.first().textContent(), /Tilføjet/);
      await catalogCards.first().click(); await page.locator('#add-recipe-template').click();
      await page.locator('#recipe-detail-dialog').waitFor();
      assert.equal(state.requests.filter(request => request.startsWith('POST /v1/recipe-templates/catalog/add')).length, 1, 'Already-added opens the existing personal recipe');
      await page.locator('#close-recipe-detail').click();
      await page.locator('#show-recipe-templates').click();
      await page.locator('#recipe-template-catalog-search').fill('ingen match');
      await page.locator('#recipe-templates-empty').waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await fs.mkdir('build', {recursive: true});
      await page.locator('#show-recipe-library').click(); await page.locator('#recipe-library-search').fill('');
      await page.screenshot({path: `build/recipe-browsing-${width}.png`});
      await cards.first().click();
      await page.locator('#recipe-detail-dialog').waitFor();
      await page.screenshot({path: `build/recipe-phase2-detail-${width}.png`});
      assert.deepEqual(state.errors, []);
    } finally {await browser.close();}
  });
}

test('empty library remains distinct from no search results and new recipe remains reachable', async () => {
  const {browser, page, state} = await fixture();
  try {
    state.empty = true; await page.evaluate(() => showView('recipes'));
    await page.locator('#recipes-empty').waitFor();
    await page.locator('#recipe-library-search').fill('ris');
    assert.equal(await page.locator('#recipes-search-empty').isVisible(), false);
    await page.locator('#recipes-empty-add').click();
    await page.locator('#recipe-editor-dialog').waitFor();
    assert.deepEqual(state.errors, []);
  } finally {await browser.close();}
});

test('failed base-recipe load keeps the detail open and allows retrying Edit', async () => {
  const {browser, page, state} = await fixture();
  try {
    await page.evaluate(() => openRecipe('one', 3)); state.failBase = true;
    await page.locator('#recipe-management > summary').click(); await page.locator('#edit-recipe').click();
    await page.waitForFunction(() => !document.querySelector('#edit-recipe').disabled);
    assert.equal(await page.locator('#recipe-detail-dialog').isVisible(), true);
    assert.equal(await page.locator('#recipe-editor-dialog').isVisible(), false);
    assert.match(await page.locator('#recipe-portions').textContent(), /3/);
    state.failBase = false; await page.locator('#edit-recipe').click();
    await page.locator('#recipe-editor-dialog').waitFor();
    assert.deepEqual(state.errors, []);
  } finally {await browser.close();}
});

for (const catalog of [false, true]) {
  test(`${catalog ? 'catalog' : 'personal'} nutrition summary indicates incomplete data without exposing collapsed warning text`, async () => {
    const {browser, page, state} = await fixture(320);
    const prefix = catalog ? 'recipe-template' : 'recipe';
    const reload = () => page.evaluate(async catalog => {
      if (catalog) await loadRecipeTemplateDetail('catalog', recipeTemplatePortions);
      else await reloadCurrentRecipeForPortions();
    }, catalog);
    try {
      await page.evaluate(async catalog => {if (catalog) await openRecipeTemplate('catalog'); else await openRecipe('one');}, catalog);
      const disclosure = page.locator(`#${prefix}-nutrition-section`), summary = disclosure.locator(':scope > summary');
      const warning = disclosure.locator(':scope > .nutrition-warning'), icon = summary.locator('.nutrition-warning-icon');
      assert.equal(await disclosure.evaluate(node => node.open), false);
      assert.equal(await warning.isVisible(), false);
      assert.equal(await summary.isVisible(), true);
      assert.equal(await icon.isVisible(), true);
      assert.equal(await icon.evaluate(node => getComputedStyle(node, '::before').content), '"⚠"');
      assert.match(await summary.getAttribute('aria-label') || await summary.textContent(), /Kulhydrater.*Ufuldstændige/);
      const fullWarning = await warning.textContent();
      assert.match(fullWarning, /ukendt bidrag/);
      assert.equal((await summary.textContent()).includes(fullWarning), false);
      await reload();
      assert.equal(await disclosure.evaluate(node => node.open), false);
      assert.equal(await icon.isVisible(), true); assert.equal(await warning.isVisible(), false);
      await summary.press('Enter');
      assert.equal(await warning.isVisible(), true);
      assert.equal(await page.locator(`#${prefix}-carbohydrates`).isVisible(), true);
      await reload();
      assert.equal(await disclosure.evaluate(node => node.open), true);
      assert.equal(await warning.textContent(), fullWarning);
      assert.equal(await warning.isVisible(), true);
      state.completeNutrition = true; await reload();
      assert.equal(await disclosure.evaluate(node => node.open), true);
      assert.equal(await icon.isVisible(), false); assert.equal(await warning.isVisible(), false);
      assert.equal(await summary.locator('.nutrition-warning-label').isVisible(), false);
      assert.equal(await disclosure.evaluate(node => node.classList.contains('has-warning')), false);
      assert.equal((await summary.innerText()).trim(), 'Kulhydrater');
      await summary.press('Space'); await reload();
      assert.equal(await disclosure.evaluate(node => node.open), false);
      assert.equal(await icon.isVisible(), false);
      state.completeNutrition = false; await reload();
      assert.equal(await disclosure.evaluate(node => node.open), false);
      assert.equal(await icon.isVisible(), true); assert.equal(await warning.isVisible(), false);
      assert.deepEqual(state.errors, []);
    } finally {await browser.close();}
  });
}
