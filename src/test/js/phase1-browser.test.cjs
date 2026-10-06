const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
let browser;
before(async () => { browser = await chromium.launch({headless: true, channel: process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'}); });
after(async () => { await browser?.close(); });

async function setup(t, width = 390) {
  const page = await browser.newPage({viewport: {width, height: 844}, serviceWorkers: 'block'});
  page.setDefaultTimeout(7000);
  t.after(() => page.close());
  const calls = [], errors = [];
  const product = {id: 'eggs', name: 'Æg', category: 'EGG', defaultUnit: 'PIECE', inventoryTrackingMode: 'QUANTITY'};
  const inventory = [
    {id: 'stock', product, quantity: 10, physicalQuantity: 10, reservedQuantity: 6, availableQuantity: 4, plannedShortfall: 0, plannedUsageCount: 1, unit: 'PIECE', reservations: [{recipeName: 'Æggekage', mealPlanName: 'Ugen', reservedQuantity: 6, portions: 2, unit: 'PIECE'}]},
    {id: 'presence', product: {...product, id: 'salt', name: 'Salt', category: 'SPICE', inventoryTrackingMode: 'PRESENCE'}, quantity: null, unit: 'GRAM', plannedUsageCount: 0, reservations: []},
    {id: 'short', product: {...product, id: 'milk', name: 'Mælk', category: 'DAIRY', defaultUnit: 'MILLILITER'}, quantity: 100, physicalQuantity: 100, reservedQuantity: 200, availableQuantity: 0, plannedShortfall: 100, plannedUsageCount: 1, unit: 'MILLILITER', reservations: []},
    {id: 'unknown', product: {...product, id: 'flour', name: 'Mel', category: 'BAKING', defaultUnit: 'GRAM'}, quantity: 500, availableQuantity: null, plannedUsageCount: 1, unit: 'GRAM', reservations: []}
  ];
  const shopping = [{id: 'shop', product, quantity: 6, unit: 'PIECE', purchased: false}];
  page.on('pageerror', e => errors.push(e.message));
  t.after(() => assert.deepEqual(errors, []));
  await page.route('http://madkursus.test/**', async route => {
    const request = route.request(), url = new URL(request.url());
    if (url.pathname.startsWith('/v1/')) {
      let body = [];
      if (url.pathname.endsWith('/me')) body = {id: 'user', username: 'Test', admin: false};
      if (url.pathname.endsWith('/csrf')) body = {token: 'csrf'};
      if (url.pathname.endsWith('/registration-status')) body = {enabled: false};
      if (url.pathname === '/v1/products') body = inventory.map(i => i.product);
      if (url.pathname === '/v1/inventory') body = inventory;
      if (url.pathname === '/v1/shopping-list') body = shopping;
      if (request.method() !== 'GET') {
        calls.push({path: url.pathname, method: request.method(), body: request.postData()});
        if (url.pathname.endsWith('/purchase')) shopping[0].purchased = true;
        if (url.pathname.endsWith('/undo-purchase')) shopping[0].purchased = false;
        body = url.pathname.startsWith('/v1/products') ? {...product, ...JSON.parse(request.postData() || '{}')} : shopping[0];
      }
      return route.fulfill({json: body});
    }
    try {
      const file = path.join(__dirname, '../../main/resources/static', url.pathname === '/' ? 'index.html' : url.pathname);
      return route.fulfill({body: await fs.readFile(file), contentType: url.pathname.endsWith('.js') ? 'text/javascript' : url.pathname.endsWith('.css') ? 'text/css' : 'text/html'});
    } catch { return route.fulfill({status: 404, body: ''}); }
  });
  await page.goto('http://madkursus.test/');
  await page.locator('#inventory-list .inventory-card').first().waitFor();
  return {page, calls};
}

for (const width of [320, 390, 1280]) {
  test(`local collections, stock states and product controls at ${width}px`, async t => {
    const {page, calls} = await setup(t, width);
    const stock = page.locator('#inventory-list');
    assert.equal(await stock.locator('.inventory-card').count(), 4);
    assert.equal(await stock.locator('.shortfall').count(), 1);
    assert.equal(await stock.locator('.reservation-link').count(), 3);
    await page.locator('#inventory-collection-search').fill('salt');
    assert.equal(await stock.locator('.inventory-card').count(), 1);
    const allCopied = await page.evaluate(async () => {
      let copied;
      Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: async text => { copied = text; }}});
      await copyInventory(); return copied;
    });
    assert.ok(allCopied.includes('Æg') && allCopied.includes('Salt') && allCopied.includes('Mælk'));
    assert.equal(calls.length, 0, 'search and copy do not mutate domain data');
    await stock.locator('.inventory-card').click();
    assert.equal(await page.locator('#edit-inventory-quantity-controls').isVisible(), false);
    await page.locator('#cancel-edit-inventory').click();
    await page.locator('#inventory-collection-search').fill('does not exist');
    assert.equal(await page.locator('#inventory-no-results').isVisible(), true);
    assert.equal(await page.locator('#inventory-empty').isVisible(), false);
    await page.locator('#inventory-collection-search').fill('');
    const eggs = page.locator('#inventory-list .inventory-card').filter({hasText: 'Æg'});
    await eggs.focus(); await eggs.press('Enter');
    await page.locator('#edit-inventory-quantity').fill('12');
    await page.locator('#edit-inventory-form button[type=submit]').click();
    await page.locator('#edit-inventory-dialog').waitFor({state: 'hidden'});
    assert.deepEqual(JSON.parse(calls.at(-1).body), {quantity: 12});
    await page.locator('#open-inventory-add').click();
    await page.locator('#inventory-search-results button').filter({hasText: 'Æg'}).click();
    await page.locator('#inventory-add-quantity').fill('4');
    await page.locator('#inventory-amount-form button[type=submit]').click();
    await page.locator('#inventory-add-dialog').waitFor({state: 'hidden'});
    assert.deepEqual(JSON.parse(calls.at(-1).body), {productId: 'eggs', quantity: 4});
    await page.evaluate(() => showView('products'));
    await page.locator('#products-collection-search').fill('æg');
    assert.equal(await page.locator('#product-list .product-card').count(), 1);
    await page.locator('#product-list .product-card').click();
    await page.locator('#edit-product-name').fill('Økologiske æg');
    await page.locator('#request-delete-product').click();
    assert.equal(await page.locator('#delete-product-confirmation').isVisible(), true);
    await page.locator('#keep-product').click();
    await page.locator('#save-edit-product').click();
    await page.locator('#edit-product-dialog').waitFor({state: 'hidden'});
    assert.deepEqual(JSON.parse(calls.at(-1).body), {name: 'Økologiske æg', category: 'EGG', defaultUnit: 'PIECE', inventoryTrackingMode: 'QUANTITY'});
    const noOverflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
    assert.equal(noOverflow, true);
    await page.locator('#open-form').click();
    await page.locator('#show-custom-product').click();
    await page.locator('#name').fill('Ny vare');
    await page.locator('#category').selectOption('OTHER');
    await page.locator('#default-unit').selectOption('PIECE');
    await page.locator('#save-product').click();
    await page.locator('#product-form-panel').waitFor({state: 'hidden'});
    assert.deepEqual(JSON.parse(calls.at(-1).body), {name: 'Ny vare', category: 'OTHER', defaultUnit: 'PIECE'});
  });

  test(`purchase, collapsed history, undo and explicit dialog regions at ${width}px`, async t => {
    const {page, calls} = await setup(t, width);
    await page.locator('#show-shopping').click();
    const row = page.locator('#shopping-active-list .shopping-row');
    await row.focus(); await row.press('Enter');
    await page.locator('#shopping-purchased-section').waitFor();
    assert.equal(await page.locator('#shopping-purchased-section').evaluate(n => n.open), false);
    assert.equal(await page.locator('#shopping-purchased-count').textContent(), '1');
    assert.equal(await page.evaluate(() => document.activeElement === document.querySelector('#shopping-purchased-section > summary')), true);
    await page.locator('#toast-action').click();
    await page.locator('#shopping-active-list .shopping-row').waitFor();
    assert.deepEqual(calls.map(c => c.path), ['/v1/shopping-list/items/shop/purchase', '/v1/shopping-list/items/shop/undo-purchase']);
    await page.locator('.shopping-edit-button').click();
    assert.equal(await page.locator('#edit-shopping-dialog .dialog-header').count(), 1);
    assert.equal(await page.locator('#edit-shopping-dialog .dialog-body').count(), 1);
    assert.equal(await page.locator('#edit-shopping-dialog .dialog-footer').count(), 1);
    for (const id of ['save-shopping-item', 'purchase-edit-shopping', 'edit-shopping-plus', 'edit-shopping-minus']) {
      const bounds = await page.locator(`#${id}`).boundingBox();
      assert.ok(bounds.width >= 44 && bounds.height >= 44);
      assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width);
    }
    if (width < 640) {
      await page.setViewportSize({width, height: 380});
      await page.locator('#edit-shopping-quantity').focus();
      await page.waitForTimeout(100);
      const input = await page.locator('#edit-shopping-quantity').boundingBox();
      const footer = await page.locator('#edit-shopping-dialog .dialog-footer').boundingBox();
      assert.ok(input.y >= 0 && input.y + input.height <= footer.y, 'quantity remains above the footer in a reduced viewport');
      await page.setViewportSize({width, height: 844});
    }
    await page.locator('#cancel-edit-shopping').click();
    await page.locator('#shopping-active-list .shopping-row').dispatchEvent('pointerdown', {clientX: 50, clientY: 200});
    await page.waitForTimeout(650);
    await page.locator('#edit-shopping-dialog').waitFor();
    assert.equal(calls.length, 2);
    await page.locator('#cancel-edit-shopping').click();
    if (process.env.PHASE1_SCREENSHOT_DIR) {
      await page.screenshot({path: path.join(process.env.PHASE1_SCREENSHOT_DIR, `shopping-${width}.png`)});
      await page.locator('#show-inventory').click();
      await page.locator('#inventory-loading').waitFor({state: 'hidden'});
      await page.evaluate(() => { document.querySelector('#toast').hidden = true; });
      await page.screenshot({path: path.join(process.env.PHASE1_SCREENSHOT_DIR, `inventory-${width}.png`)});
      await page.evaluate(() => showView('products'));
      await page.screenshot({path: path.join(process.env.PHASE1_SCREENSHOT_DIR, `products-${width}.png`)});
    }
  });
}

for (const key of ['Enter', 'Space']) {
  test(`reservation ${key} never opens the parent stock editor`, async t => {
    const {page, calls} = await setup(t);
    const action = page.locator('.inventory-card').filter({hasText: 'Æg'}).locator('.reservation-link');
    await action.focus(); await action.press(key);
    await page.locator('#inventory-reservation-dialog').waitFor();
    assert.equal(await page.locator('#edit-inventory-dialog').evaluate(n => n.open), false);
    assert.deepEqual(calls, []);
  });
  test(`shopping edit ${key} never purchases the parent row`, async t => {
    const {page, calls} = await setup(t);
    await page.locator('#show-shopping').click();
    const edit = page.locator('.shopping-edit-button');
    await edit.focus(); await edit.press(key);
    await page.locator('#edit-shopping-dialog').waitFor();
    assert.deepEqual(calls, []);
  });
}
