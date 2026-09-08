// Optional browser checks: install Playwright and run this file with node --test.
const { test } = require('node:test');
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');

for (const width of [1280, 390, 320]) {
  test(`shared drawer and page at ${width}px`, async () => {
    const browser = await chromium.launch({ headless: true, ...(process.env.CHAT_TEST_BROWSER_CHANNEL ? { channel: process.env.CHAT_TEST_BROWSER_CHANNEL } : {}) });
    try {
      const context = await browser.newContext({ viewport: { width, height: 844 }, serviceWorkers: 'block' });
      const page = await context.newPage();
      const errors = [], requests = [];
      let authenticated = true;
      page.on('pageerror', error => errors.push(error.message));
      await page.route('http://madkursus.test/**', async route => {
        const request = route.request(), pathname = new URL(request.url()).pathname;
        if (pathname.startsWith('/v1/')) {
          let body = [];
          if (pathname === '/v1/ai/chat/model') body = { model: 'browser-test-model:4b' };
          if (pathname === '/v1/recipes/known-recipe' || pathname === '/v1/recipe-templates/known-template') {
            assert.equal(new URL(request.url()).searchParams.get('portions'), '1');
            body = { id: 'known-recipe', name: 'Kødboller i tomatsovs med pasta', ingredients: [], steps: [], description: '' };
          }
          if (pathname === '/v1/inventory') body = [{ id: 'stock', quantity: 500, unit: 'GRAM', product: { id: 'potatoes', name: 'Kartofler', category: 'VEGETABLE', inventoryTrackingMode: 'QUANTITY' } }];
          if (pathname.endsWith('/registration-status')) body = { enabled: false };
          if (pathname.endsWith('/csrf')) body = { token: 'test-csrf' };
          if (pathname.endsWith('/me')) {
            if (!authenticated) return route.fulfill({ status: 401, json: { message: 'Login required' } });
            body = { id: 'test-user', username: 'Test', admin: false };
          }
          if (pathname === '/v1/ai/chat') {
            requests.push(request.postDataJSON());
            assert.deepEqual(Object.keys(request.postDataJSON()), ['message']);
            assert.equal(request.headers()['x-xsrf-token'], 'test-csrf');
            await new Promise(resolve => setTimeout(resolve, 250));
            body = { answer: 'Kendte opskrifter.\n<img src=x onerror=alert(1)>', knownRecipes: [
              { id: 'known-recipe', source: 'RECIPE', name: 'Kødboller i tomatsovs med pasta', state: 'COOKABLE', missingIngredients: [], uncertainIngredients: [] },
              { id: 'known-template', source: 'TEMPLATE', name: 'Chili', state: 'NEAR_MATCH', missingIngredients: ['Bønner'], uncertainIngredients: [] }
            ] };
          }
          return route.fulfill({ json: body });
        }
        const file = path.join(__dirname, '../../main/resources/static', pathname === '/' ? 'index.html' : pathname);
        const contentType = pathname.endsWith('.js') ? 'text/javascript' : pathname.endsWith('.css') ? 'text/css' : 'text/html';
        await route.fulfill({ body: await fs.readFile(file), contentType });
      });
      await page.goto('http://madkursus.test/');
      const launcher = page.locator('#chat-launcher'), drawer = page.locator('#chat-drawer');
      await launcher.waitFor({ state: 'visible' });
      assert.equal(await drawer.isVisible(), false);
      if (width < 640) {
        const launcherBox = await launcher.boundingBox(), addBox = await page.locator('#open-inventory-add').boundingBox();
        assert.ok(launcherBox.y + launcherBox.height <= addBox.y);
      }
      await launcher.click();
      await drawer.waitFor({ state: 'visible' });
      assert.equal(await drawer.getAttribute('aria-modal'), 'false');
      assert.equal(await page.locator(':modal').count(), 0);
      assert.notEqual(await page.evaluate(() => getComputedStyle(document.body).overflow), 'hidden');
      assert.equal(await page.getByRole('button', { name: 'Minimér Madhjælp' }).count(), 1);
      await page.waitForFunction(() => document.querySelector('#chat-model').textContent === 'Bruger model: browser-test-model:4b');
      assert.equal(await page.locator('#chat-minimize').evaluate(element => element === document.activeElement), true);
      if (process.env.CHAT_TEST_SCREENSHOTS) {
        await fs.mkdir('build', { recursive: true });
        await page.screenshot({ path: `build/chat-empty-${width}.png`, fullPage: true });
      }
      assert.deepEqual(await page.locator('#chat-max-extra option').evaluateAll(options => options.map(o => [o.value, o.textContent])),
        [['', 'Ingen fast grænse'], ['0', 'Kun det jeg har'], ['1', 'Højst 1 ekstra'], ['2', 'Højst 2 ekstra'], ['3', 'Højst 3 ekstra']]);
      const limitBox = await page.locator('#chat-max-extra').boundingBox(), panelBox = await drawer.boundingBox();
      assert.ok(limitBox.width < panelBox.width - 40);
      await page.locator('[data-chat-prompt]').first().click();
      await page.locator('#chat-loading').waitFor({ state: 'visible' });
      assert.equal(await page.locator('#chat-send').isDisabled(), true);
      await page.locator('#chat-minimize').click();
      await launcher.click();
      await page.locator('.chat-message-assistant').waitFor();
      assert.equal(requests.length, 1);
      assert.equal(await page.locator('#chat-messages img').count(), 0);
      assert.equal(await page.locator('#chat-empty').isVisible(), false);
      const messageBox = await page.locator('#chat-messages').boundingBox(), composerBox = await page.locator('#chat-form').boundingBox();
      assert.ok(messageBox.height > composerBox.height);
      for (const [index, dialog] of [[0, '#recipe-detail-dialog'], [1, '#recipe-template-detail-dialog']]) {
        await page.locator('.chat-recipe-card button').nth(index).click();
        await page.locator(dialog).waitFor({ state: 'visible' });
        assert.equal(await drawer.isVisible(), false);
        assert.equal(await page.locator(dialog).evaluate(el => el.matches(':modal')), true);
        await page.keyboard.press('Escape'); await launcher.click();
      }
      await page.locator('#chat-input').fill('Hvad med suppe?');
      await page.keyboard.press('Escape');
      await drawer.waitFor({ state: 'hidden' });
      assert.equal(await launcher.evaluate(element => element === document.activeElement), true);
      if (width < 1024) { await page.locator('#show-more').click(); await page.locator('#more-ai').click(); }
      else await page.locator('#show-ai').click();
      assert.equal(await launcher.isVisible(), false);
      await page.waitForFunction(() => document.querySelector('#chat-model').textContent === 'Bruger model: browser-test-model:4b');
      assert.equal(await page.locator('#ai-view #chat-model').isVisible(), true);
      assert.equal(await page.locator('#chat-input').inputValue(), 'Hvad med suppe?');
      await page.locator('#chat-send').click();
      await page.waitForFunction(() => document.querySelectorAll('.chat-message-assistant').length === 2);
      await page.locator('#show-inventory').click();
      await launcher.click();
      assert.equal(await page.locator('.chat-message-assistant').count(), 2);
      await page.locator('#show-shopping').click();
      assert.equal(await drawer.isVisible(), false);
      assert.equal(await page.locator('#shopping-view').isVisible(), true);
      await page.locator('#show-inventory').click();
      await page.locator('#open-inventory-add').click();
      assert.equal(await page.locator('#inventory-add-dialog').evaluate(element => element.matches(':modal')), true);
      await page.keyboard.press('Escape');
      await launcher.click();
      assert.equal(await page.locator('.chat-message-assistant').count(), 2);
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      if (width < 1024) {
        await page.setViewportSize({ width, height: 380 });
        await page.locator('#chat-input').focus();
        await page.waitForTimeout(100);
        const bounds = await page.locator('#chat-input').boundingBox();
        assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= 380, JSON.stringify(bounds));
      }
      if (process.env.CHAT_TEST_SCREENSHOTS) {
        await fs.mkdir('build', { recursive: true });
        await page.screenshot({ path: `build/chat-drawer-${width}.png`, fullPage: true });
      }
      await page.locator('#chat-minimize').click();
      authenticated = false;
      await page.reload();
      await page.locator('#auth-screen').waitFor({ state: 'visible' });
      assert.equal(await launcher.isVisible(), false);
      assert.deepEqual(errors, []);
    } finally { await browser.close(); }
  });
}
