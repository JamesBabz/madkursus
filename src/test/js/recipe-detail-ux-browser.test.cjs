const {test} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const recipe = {id: 'one', name: 'Kødboller med pasta', ingredients: [],
  preparationSteps: [{instruction: 'Find ingredienser frem', sortOrder: 1}], equipment: ['Gryde'],
  steps: [
    ...['Kog pasta', 'Steg kødboller'].map((name, index) => ({id: `step-${index}`, type: 'PROCESS', sortOrder: index,
      cookingProcessId: `process-${index}`, parameterBindings: [], renderedProcess: {processName: name,
        passiveDurationSeconds: 120, activeDurationSeconds: 30, durationSummary: '2 minutter', inputSummary: 'Pasta og vand',
        preparationInstructions: ['Find gryden frem'], completionCriterion: 'Pastaen er mør',
        instructions: ['Tilbered ingredienserne'], warnings: ['Pas på det kogende vand']}})),
    ...Array.from({length: 20}, (_, i) => ({id: `text-${i}`, type: 'TEXT', sortOrder: i + 2,
      instruction: `Trin ${i + 3}: Vend forsigtigt ingredienserne sammen og kontrollér konsistensen inden servering.`}))]};

for (const width of [320, 390, 1280]) {
  test(`recipe detail and editor UX at ${width}px`, async () => {
    const browser = await chromium.launch({headless: true, channel: process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'});
    try {
      const page = await browser.newPage({viewport: {width, height: 844}, serviceWorkers: 'block', reducedMotion: 'reduce'});
      const errors = [], requests = []; page.on('pageerror', error => errors.push(error.message));
      await page.route('http://madkursus.test/**', async route => {
        const url = new URL(route.request().url());
        if (url.pathname.startsWith('/v1/')) {
          requests.push(url.pathname); let body = [];
          if (url.pathname.endsWith('/me')) body = {id: 'user', username: 'Test', admin: false};
          if (url.pathname.endsWith('/csrf')) body = {token: 'csrf'};
          if (url.pathname.endsWith('/registration-status')) body = {enabled: false};
          if (url.pathname === '/v1/recipes/one') body = recipe;
          if (url.pathname === '/v1/cooking-processes') body = recipe.steps.slice(0, 2).map(step => ({id: step.cookingProcessId, name: step.renderedProcess.processName, parameters: []}));
          return route.fulfill({json: body});
        }
        try {
          const file = path.join(__dirname, '../../main/resources/static', url.pathname === '/' ? 'index.html' : url.pathname);
          return route.fulfill({body: await fs.readFile(file), contentType: url.pathname.endsWith('.js') ? 'text/javascript' : url.pathname.endsWith('.css') ? 'text/css' : 'text/html'});
        } catch {return route.fulfill({status: 404, body: ''});}
      });
      await page.goto('http://madkursus.test/'); await page.locator('#application').waitFor();
      await page.evaluate(() => openRecipe('one'));
      const dialog = page.locator('#recipe-detail-dialog');
      for (const section of ['ingredients', 'preparation', 'equipment', 'instructions']) {
        assert.equal(await page.locator(`#recipe-${section}-section`).evaluate(node => node.open), section === 'instructions');
      }
      await page.locator('#recipe-ingredients-section > summary').click();
      await page.locator('#recipe-preparation-section > summary').click();
      assert.equal(await page.locator('#recipe-ingredients-section').evaluate(node => node.open), true);
      assert.equal(await page.locator('#recipe-instructions-section').evaluate(node => node.open), true);
      await page.evaluate(() => renderRecipeDetail());
      assert.equal(await page.locator('#recipe-ingredients-section').evaluate(node => node.open), true);
      const process = dialog.locator('.process-details').first();
      assert.match(await process.locator('summary').textContent(), /Kog pasta.*2 minutter.*Pasta og vand/);
      await process.locator('summary').click();
      assert.match(await process.locator('.process-instructions').textContent(), /Tilbered ingredienserne/);
      assert.match(await process.locator('.process-completion').textContent(), /Pastaen er mør/);
      assert.equal(await process.locator('.process-warnings').isVisible(), true);
      assert.match(await process.locator('.process-warnings').textContent(), /kogende vand/);
      const timers = dialog.locator('.process-timer'), bar = page.locator('#recipe-timer-bar');
      assert.equal(await timers.first().locator('input').isVisible(), false);
      assert.equal(await timers.first().locator('.timer-name').textContent(), 'Kog pasta');
      assert.equal(await bar.isVisible(), false);
      await timers.first().getByRole('button', {name: 'Omdøb timeren Kog pasta'}).click();
      await timers.first().getByLabel('Timernavn').fill('Pasta til børn');
      await timers.first().getByLabel('Timernavn').press('Enter');
      assert.equal(await timers.first().locator('input').isVisible(), false);
      await timers.first().getByRole('button', {name: 'Start timeren Pasta til børn', exact: true}).click();
      await timers.nth(1).getByRole('button', {name: 'Start timeren Steg kødboller', exact: true}).click();
      assert.ok((await timers.first().boundingBox()).height <= 100, 'Compact timer stays within two touch-target rows');
      await fs.mkdir('build', {recursive: true});
      await page.screenshot({path: `build/recipe-inline-timers-${width}.png`});
      if (width < 600) {
        assert.equal(await bar.isVisible(), true); await bar.locator('summary').click();
        assert.equal(await bar.locator('.active-timer-row').count(), 2);
        const selectSecond = bar.getByRole('button', {name: 'Vis timeren Steg kødboller i oversigten'});
        await selectSecond.click();
        assert.equal(await selectSecond.getAttribute('aria-pressed'), 'true');
        assert.equal(await bar.locator('.active-timer-row').nth(1).locator('small').textContent(), 'Kører');
        await bar.locator('summary').click(); assert.match(await bar.locator('summary').textContent(), /Steg kødboller/);
        await bar.locator('summary').click();
        await bar.getByRole('button', {name: 'Vis timeren Pasta til børn i oversigten'}).press('Space');
        assert.match(await bar.locator('summary').textContent(), /Pasta til børn/);
        await selectSecond.press('Enter'); assert.match(await bar.locator('summary').textContent(), /Steg kødboller/);
        await bar.getByRole('button', {name: 'Sæt timeren Pasta til børn på pause'}).click();
        assert.equal(await selectSecond.getAttribute('aria-pressed'), 'true');
        assert.match(await bar.locator('summary').textContent(), /Steg kødboller/);
        assert.equal(await timers.first().getByRole('button', {name: 'Start timeren Pasta til børn', exact: true}).count(), 1);
        const paused = await timers.first().locator('.timer-remaining').textContent();
        assert.equal(await bar.locator('.active-timer-row').first().locator('span').textContent(), paused);
        assert.equal(await bar.locator('.active-timer-row').nth(1).locator('small').textContent(), 'Kører');
        await page.evaluate(() => {document.querySelector('#recipe-detail-dialog').scrollTop = 700;});
        const box = await bar.boundingBox(), bounds = await dialog.boundingBox();
        assert.ok(box.y >= bounds.y && box.y + box.height <= bounds.y + bounds.height, 'Timer bar stays inside the scrolled dialog');
        await fs.mkdir('build', {recursive: true}); await page.screenshot({path: `build/recipe-detail-ux-${width}.png`});
        await bar.getByRole('button', {name: 'Nulstil timeren Pasta til børn'}).click();
        assert.equal(await timers.first().getByRole('button', {name: 'Nulstil timeren Pasta til børn'}).evaluate(node => node === document.activeElement), true);
        assert.equal(await bar.locator('.active-timer-row').count(), 1);
        await bar.getByRole('button', {name: 'Nulstil timeren Steg kødboller'}).click(); assert.equal(await bar.isVisible(), false);
      } else {
        assert.equal(await bar.isVisible(), false);
        await fs.mkdir('build', {recursive: true}); await page.screenshot({path: `build/recipe-detail-ux-${width}.png`});
      }
      assert.ok(await dialog.evaluate(node => node.scrollWidth <= node.clientWidth), 'No horizontal overflow');
      if (width >= 600) {
        await timers.first().getByRole('button', {name: 'Nulstil timeren Pasta til børn'}).click();
        await timers.nth(1).getByRole('button', {name: 'Nulstil timeren Steg kødboller'}).click();
      }
      await page.evaluate(() => {closeRecipeDetail(); openRecipeEditor(currentRecipe);});
      const editor = page.locator('#recipe-editor-dialog');
      assert.equal(await editor.locator('#editor-instructions').evaluate(node=>node.open),false);
      await editor.locator('#editor-instructions > summary').click();
      assert.equal(await editor.locator('.step-input-row').first().locator('strong').nth(1).textContent(), 'Kog pasta');
      assert.equal(await editor.locator('.step-input-row').nth(1).locator('strong').nth(1).textContent(), 'Steg kødboller');
      assert.equal(requests.filter(url => url === '/v1/cooking-processes').length, 0, 'Process names need no fetch');
      await editor.locator('.step-input-row').first().getByRole('button', {name: 'Rediger', exact: true}).click();
      await page.waitForFunction(() => document.activeElement?.id === 'cooking-process-select');
      assert.equal(await page.locator('#cooking-process-select').inputValue(), 'process-0');
      const picker = await page.locator('#process-picker').boundingBox(), editorBounds = await editor.boundingBox();
      assert.ok(picker.y >= editorBounds.y && picker.y < editorBounds.y + editorBounds.height, 'Opened editor is in view');
      await page.locator('#cancel-process-step').click(); assert.equal(await page.locator('#process-picker').isVisible(), false);
      await page.evaluate(() => {closeRecipeEditor(); openRecipe('one');});
      await page.waitForFunction(() => document.querySelector('#recipe-detail-dialog').open);
      assert.equal(await page.locator('#recipe-ingredients-section').evaluate(node => node.open), false);
      assert.equal(await bar.isVisible(), false);
      assert.deepEqual(errors, []);
    } finally {await browser.close();}
  });
}
