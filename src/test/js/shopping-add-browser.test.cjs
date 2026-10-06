const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
let browser;
before(async () => {browser = await chromium.launch({headless: true, channel: process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'});});
after(async () => {await browser?.close();});

async function shopping(t, {width = 390, failCreate = false, failAdd = false} = {}) {
  const page = await browser.newPage({viewport: {width, height: 844}, serviceWorkers: 'block'});
  page.setDefaultTimeout(7000);
  t.after(() => page.close());
  const errors = [], calls = [], items = [];
  const products = [
    {id: 'existing', name: 'Eggs', category: 'EGG', defaultUnit: 'PIECE', inventoryTrackingMode: 'QUANTITY'},
    {id: 'presence', name: 'Salt', category: 'SPICE', defaultUnit: 'GRAM', inventoryTrackingMode: 'PRESENCE'}
  ];
  page.on('pageerror', error => errors.push(error.message));
  t.after(() => assert.deepEqual(errors, []));
  await page.route('http://madkursus.test/**', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method();
    if (url.pathname.startsWith('/v1/')) {
      let body = [];
      if (url.pathname.endsWith('/me')) body = {id: 'user', username: 'Shopper', admin: false};
      if (url.pathname.endsWith('/csrf')) body = {token: 'csrf'};
      if (url.pathname === '/v1/products') body = products;
      if (url.pathname === '/v1/shopping-list') body = items;
      if (method === 'POST') {
        const payload = request.postDataJSON(); calls.push({path: url.pathname, payload});
        assert.equal(request.headers()['x-xsrf-token'], 'csrf');
        await new Promise(resolve => setTimeout(resolve, 100));
        if (url.pathname === '/v1/products') {
          if (failCreate) {failCreate = false; return route.fulfill({status: 409, json: {message: 'Product already exists'}});}
          body = {id: 'created', ...payload, inventoryTrackingMode: 'QUANTITY', sourceTemplateId: null}; products.push(body);
        }
        if (url.pathname === '/v1/shopping-list/items') {
          if (failAdd) {failAdd = false; return route.fulfill({status: 500, json: {message: 'Could not add item'}});}
          const product = products.find(product => product.id === payload.productId);
          body = {id: 'item-' + product.id, product, quantity: payload.quantity ?? null, unit: product.defaultUnit, purchased: false}; items.push(body);
        }
      }
      return route.fulfill({json: body});
    }
    const file = path.join(__dirname, '../../main/resources/static', url.pathname === '/' ? 'index.html' : url.pathname);
    try {return route.fulfill({body: await fs.readFile(file), contentType: url.pathname.endsWith('.js') ? 'text/javascript' : url.pathname.endsWith('.css') ? 'text/css' : 'text/html'});}
    catch {return route.fulfill({status: 404, body: ''});}
  });
  await page.goto('http://madkursus.test/');
  await page.locator('#application').waitFor();
  await page.locator('#show-shopping').click();
  await page.locator('#shopping-empty-add').click();
  return {page, calls, products};
}

test('existing quantity and presence products still use the normal shopping endpoint', async t => {
  const {page, calls} = await shopping(t);
  for (const [name, id, quantity] of [['Eggs', 'existing', 2], ['Salt', 'presence', null]]) {
    await page.locator('#shopping-search').fill(name);
    await page.locator('#shopping-search-results button').filter({hasText: name}).click();
    assert.equal(await page.locator('#shopping-new-product-fields').isVisible(), false);
    assert.equal(await page.locator('#shopping-add-quantity').isVisible(), quantity !== null);
    if (quantity !== null) await page.locator('#shopping-add-quantity').fill(String(quantity));
    await page.locator('#shopping-amount-form button[type=submit]').click();
    await page.locator('#shopping-add-dialog').waitFor({state: 'hidden'});
    assert.deepEqual(calls.at(-1), {path: '/v1/shopping-list/items', payload: {productId: id, ...(quantity === null ? {} : {quantity})}});
    if (name === 'Eggs') await page.locator('#open-shopping-add').click();
  }
  assert.equal(calls.some(call => call.path === '/v1/products'), false);
});

for (const width of [320, 1280]) {
  test(`create a missing Product and add it without leaving shopping at ${width}px`, async t => {
    const {page, calls} = await shopping(t, {width});
    assert.equal(await page.evaluate(() => document.activeElement.id), 'close-shopping-add');
    await page.locator('#shopping-search').fill('New item');
    await page.locator('#shopping-create-product').click();
    assert.equal(await page.locator('#shopping-product-name').inputValue(), 'New item');
    await page.locator('#shopping-product-category').selectOption('OTHER');
    await page.locator('#shopping-product-unit').selectOption('PIECE');
    assert.equal(await page.locator('#shopping-add-quantity').getAttribute('step'), '0.5');
    await page.locator('#shopping-add-quantity').fill('2.5');
    assert.equal(await page.evaluate(() => document.querySelector('#shopping-add-dialog').scrollWidth <= document.querySelector('#shopping-add-dialog').clientWidth), true);
    await page.locator('#shopping-amount-form button[type=submit]').click();
    assert.equal(await page.locator('#close-shopping-add').isDisabled(), true);
    await page.keyboard.press('Escape');
    await page.locator('#shopping-add-dialog').waitFor({state: 'hidden'});
    assert.deepEqual(calls, [
      {path: '/v1/products', payload: {name: 'New item', category: 'OTHER', defaultUnit: 'PIECE'}},
      {path: '/v1/shopping-list/items', payload: {productId: 'created', quantity: 2.5}}
    ]);
    assert.equal(await page.locator('#shopping-view').isVisible(), true);
    assert.match(await page.locator('#shopping-active-list').textContent(), /New item/);
    await page.locator('#open-shopping-add').click();
    assert.equal(await page.locator('#shopping-search').inputValue(), '');
    await page.locator('#shopping-search').fill('New item');
    await page.locator('#shopping-search-results button').filter({hasText: 'New item'}).waitFor();
    assert.equal(await page.locator('#shopping-create-product').isVisible(), false);
  });
}

test('failed shopping add retains the newly created Product and retries only the add', async t => {
  const {page, calls} = await shopping(t, {failAdd: true});
  await page.locator('#shopping-search').fill('Retry item');
  await page.locator('#shopping-create-product').click();
  await page.locator('#shopping-add-quantity').fill('100');
  await page.locator('#shopping-amount-form button[type=submit]').click();
  await page.locator('#shopping-add-error').waitFor();
  assert.equal(await page.locator('#shopping-new-product-fields').isVisible(), false);
  assert.equal(await page.locator('#shopping-add-quantity').inputValue(), '100');
  await page.locator('#shopping-amount-form button[type=submit]').click();
  await page.locator('#shopping-add-dialog').waitFor({state: 'hidden'});
  assert.deepEqual(calls.map(call => call.path), ['/v1/products', '/v1/shopping-list/items', '/v1/shopping-list/items']);
});

test('creation failure preserves the form and does not add a shopping item', async t => {
  const {page, calls} = await shopping(t, {failCreate: true});
  await page.locator('#shopping-search').fill('Another item');
  await page.locator('#shopping-create-product').click();
  await page.locator('#shopping-add-quantity').fill('100');
  await page.locator('#shopping-amount-form button[type=submit]').click();
  await page.locator('#shopping-add-error').waitFor();
  assert.equal(await page.locator('#shopping-product-name').inputValue(), 'Another item');
  assert.deepEqual(calls.map(call => call.path), ['/v1/products']);
  await page.locator('#back-shopping-search').click();
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('#shopping-add-dialog').isVisible(), false);
});
