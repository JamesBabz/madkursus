// Run with node --test; uses Playwright like the other browser tests.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');

async function setup(width, admin) {
  const browser = await chromium.launch({headless: true, ...(process.env.CHAT_TEST_BROWSER_CHANNEL ? {channel: process.env.CHAT_TEST_BROWSER_CHANNEL} : {})});
  const page = await browser.newPage({viewport: {width, height: 844}, serviceWorkers: 'block'});
  const calls = [], errors = [];
  let failSubmission = false, failMutation = false;
  let items = [{id: 'first', type: 'BUG', title: '<img src=x onerror=alert(1)>', description: 'Line one\nLine two', status: 'OPEN', createdBy: 'user-one', createdAt: '2026-09-27T12:00:00Z'},
    {id: 'second', type: 'FEEDBACK', title: 'Another user', description: 'Suggestion', status: 'DONE', createdBy: 'user-two', createdAt: '2026-09-26T12:00:00Z'}];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('http://madkursus.test/**', async route => {
    const request = route.request(), url = new URL(request.url()), method = request.method();
    if (url.pathname.startsWith('/v1/')) {
      let body = [];
      if (url.pathname.endsWith('/registration-status')) body = {enabled: false};
      if (url.pathname.endsWith('/csrf')) body = {token: 'test-csrf'};
      if (url.pathname.endsWith('/me')) body = {id: 'user-one', username: 'Test', admin};
      if (url.pathname.endsWith('/recipe-template-import')) body = {enabled: false};
      if (url.pathname === '/v1/feedback' || url.pathname.startsWith('/v1/admin/feedback')) {
        calls.push({path: url.pathname, method, body: request.postDataJSON()});
        if (method !== 'GET') assert.equal(request.headers()['x-xsrf-token'], 'test-csrf');
        if (url.pathname === '/v1/feedback') {
          await new Promise(resolve => setTimeout(resolve, 150));
          return route.fulfill({status: failSubmission ? 500 : 201, body: ''});
        }
        if (method === 'GET') body = items;
        else {
          if (failMutation) return route.fulfill({status: 500, json: {message: 'Failure'}});
          const id = url.pathname.split('/').pop();
          if (method === 'PATCH') { body = items.find(item => item.id === id); body.status = request.postDataJSON().status; }
          if (method === 'DELETE') { items = items.filter(item => item.id !== id); return route.fulfill({status: 204}); }
        }
      }
      return route.fulfill({json: body});
    }
    const file = path.join(__dirname, '../../main/resources/static', url.pathname === '/' ? 'index.html' : url.pathname);
    return route.fulfill({body: await fs.readFile(file), contentType: url.pathname.endsWith('.js') ? 'text/javascript' : url.pathname.endsWith('.css') ? 'text/css' : 'text/html'});
  });
  try {
    await page.goto('http://madkursus.test/');
    await page.locator('#show-more').click();
  } catch (error) { await browser.close(); throw error; }
  return {browser, page, calls, errors, failSubmit(value) {failSubmission = value;}, failChange(value) {failMutation = value;}};
}

for (const width of [390, 1280]) {
  test(`member submits both types with native modal behavior at ${width}px`, async () => {
    const state = await setup(width, false);
    const {page, browser, calls, errors} = state;
    try {
      assert.equal(await page.locator('#more-feedback-admin').isVisible(), false);
      for (const type of ['FEEDBACK', 'BUG']) {
        await page.locator('#more-send-feedback').click();
        assert.equal(await page.evaluate(() => document.activeElement.id), 'close-feedback');
        await page.locator('#feedback-type').selectOption(type);
        await page.locator('#feedback-title').fill(' A title ');
        await page.locator('#feedback-description').fill('Some details');
        await page.locator('#send-feedback').click();
        assert.equal(await page.locator('#send-feedback').isDisabled(), true);
        await page.locator('#feedback-dialog').waitFor({state: 'hidden'});
        assert.match(await page.locator('#toast-message').textContent(), /Din besked er sendt/);
        assert.equal(await page.locator('#feedback-title').inputValue(), '');
        assert.deepEqual(calls.at(-1).body, {type, title: 'A title', description: 'Some details'});
      }
      state.failSubmit(true);
      await page.locator('#more-send-feedback').click();
      await page.locator('#feedback-title').fill('Retain this');
      await page.locator('#feedback-description').fill('Details');
      await page.locator('#send-feedback').click();
      await page.locator('#feedback-error').waitFor({state: 'visible'});
      assert.equal(await page.locator('#feedback-title').inputValue(), 'Retain this');
      assert.equal(await page.locator('#send-feedback').isEnabled(), true);
      await page.keyboard.press('Escape');
      assert.equal(await page.locator('#feedback-dialog').isVisible(), false);
      assert.equal(calls.some(call => call.path.startsWith('/v1/admin/feedback')), false);
      assert.deepEqual(errors, []);
    } finally {await browser.close();}
  });
}

test('admin sees safe text, changes status, recovers from failures and deletes', async () => {
  const state = await setup(320, true);
  const {page, browser, calls, errors} = state;
  try {
    await page.locator('#more-feedback-admin').click();
    await page.locator('.feedback-card').first().waitFor();
    assert.equal(await page.locator('.feedback-card').count(), 2);
    assert.equal(await page.locator('.feedback-card img').count(), 0);
    const first = page.locator('.feedback-card').first();
    assert.match(await first.textContent(), /Line one\nLine two/);
    assert.match(await first.textContent(), /user-one/);
    assert.match(await first.textContent(), /2026/);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await first.locator('select').selectOption('IN_PROGRESS');
    await page.waitForFunction(() => !document.querySelector('.feedback-card select').disabled);
    assert.deepEqual(calls.at(-1).body, {status: 'IN_PROGRESS'});
    state.failChange(true);
    await first.locator('select').selectOption('DONE');
    await first.locator('[role=alert]').waitFor({state: 'visible'});
    assert.equal(await first.locator('select').inputValue(), 'IN_PROGRESS');
    state.failChange(false);
    await first.locator('button').click();
    await page.waitForFunction(() => document.querySelectorAll('.feedback-card').length === 1);
    assert.equal(calls.at(-1).method, 'DELETE');
    await page.locator('.feedback-card button').click();
    await page.waitForFunction(() => !document.querySelector('.feedback-card'));
    assert.match(await page.locator('#feedback-admin-message').textContent(), /ingen indsendte/);
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});
