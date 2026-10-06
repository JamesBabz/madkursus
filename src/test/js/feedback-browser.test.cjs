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
  let failSubmission = false, failMutation = false, failLoading = false;
  let items = [{id: 'first', type: 'BUG', title: '<img src=x onerror=alert(1)>', description: 'Line one\nLine two', status: 'OPEN', createdBy: '00000000-0000-0000-0000-000000000001', createdByUsername: 'user-one', createdAt: '2026-09-27T12:00:00Z'},
    {id: 'second', type: 'FEEDBACK', title: 'Another user', description: 'Suggestion', status: 'DONE', createdBy: 'ffffffff-ffff-ffff-ffff-ffffffffffff', createdByUsername: 'user-two', createdAt: '2026-09-26T12:00:00Z'}];
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
        if (method === 'GET') {if(failLoading)return route.fulfill({status:500,json:{message:'Load failed'}});body = items;}
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
  return {browser, page, calls, errors, failSubmit(value) {failSubmission = value;}, failChange(value) {failMutation = value;}, failLoad(value) {failLoading = value;}, setItems(value) {items = structuredClone(value);}};
}

for (const width of [320, 390, 1280]) {
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
      assert.equal(await page.locator('#feedback-description').inputValue(), 'Details');
      assert.equal(await page.locator('#feedback-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),true);
      state.failSubmit(false);await page.locator('#send-feedback').click();await page.locator('#feedback-dialog').waitFor({state:'hidden'});
      await page.locator('#more-send-feedback').click();await page.keyboard.press('Escape');
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
    await first.locator('.feedback-delete').click();assert.equal(calls.at(-1).method,'PATCH');await first.locator('.inline-confirmation .danger-button').click();
    await page.waitForFunction(() => document.querySelectorAll('.feedback-card').length === 1);
    assert.equal(calls.at(-1).method, 'DELETE');
    await page.locator('.feedback-card .feedback-delete').click();await page.locator('.feedback-card .inline-confirmation .danger-button').click();
    await page.waitForFunction(() => !document.querySelector('.feedback-card'));
    assert.match(await page.locator('#feedback-admin-message').textContent(), /ingen indsendte/);
    assert.deepEqual(errors, []);
  } finally {await browser.close();}
});

const sortingItems=[
  {id:'a',type:'BUG',title:'En lang fejlbeskrivelse der skal ombrydes naturligt på en lille telefon',description:'Første linje\nAnden linje',status:'OPEN',createdBy:'00000000-0000-0000-0000-000000000001',createdByUsername:'Åse',createdAt:'2026-10-05T12:00:00Z'},
  {id:'b',type:'FEEDBACK',title:'Forslag',description:'En idé',status:'DONE',createdBy:'ffffffff-ffff-ffff-ffff-ffffffffffff',createdByUsername:'Anna',createdAt:'2026-10-01T12:00:00Z'},
  {id:'c',type:'FEEDBACK',title:'I gang',description:'Besked',status:'IN_PROGRESS',createdBy:'22222222-2222-2222-2222-222222222222',createdByUsername:'Ægir',createdAt:'2026-10-04T12:00:00Z'},
  {id:'d',type:'BUG',title:'Åben',description:'Besked',status:'OPEN',createdBy:'11111111-1111-1111-1111-111111111111',createdByUsername:'Øyvind',createdAt:'2026-10-03T12:00:00Z'}
];
for(const width of [320,390,1280]){
  test(`feedback sorting and stable targeted actions at ${width}px`,async()=>{
    const state=await setup(width,true);const {page,browser,calls}=state;
    try{
      state.setItems(sortingItems);await page.locator('#more-feedback-admin').click();await page.locator('.feedback-card').first().waitFor();
      const order=()=>page.locator('.feedback-card').evaluateAll(rows=>rows.map(r=>r.dataset.feedbackId));
      assert.deepEqual(await order(),['a','c','d','b']);
      for(const item of sortingItems){const row=page.locator(`[data-feedback-id="${item.id}"]`);assert.match(await row.locator('.feedback-metadata').textContent(),new RegExp(item.createdByUsername));assert.equal((await row.locator('.feedback-metadata').textContent()).includes(item.createdBy),false);}
      const readCount=calls.length;
      await page.evaluate(()=>window.originalFeedbackRow=document.querySelector('[data-feedback-id="a"]'));
      for(const [value,expected] of [['oldest',['b','d','c','a']],['user-asc',['b','c','d','a']],['user-desc',['a','d','c','b']],['status',['c','a','d','b']]]){
        await page.locator('#feedback-sort').selectOption(value);assert.deepEqual(await order(),expected);
        assert.equal(await page.evaluate(()=>window.originalFeedbackRow===document.querySelector('[data-feedback-id="a"]')),true);
      }
      assert.equal(calls.length,readCount,'sorting is local and does not mutate data');
      const target=page.locator('[data-feedback-id="c"]');await target.locator('select').selectOption('OPEN');
      await page.waitForFunction(()=>!document.querySelector('[data-feedback-id="c"] select').disabled);
      assert.equal(calls.at(-1).path,'/v1/admin/feedback/c');assert.deepEqual(calls.at(-1).body,{status:'OPEN'});
      assert.deepEqual(await order(),['a','c','d','b']);
      // Full description remains available without dominating the collapsed list.
      await page.locator('[data-feedback-id="a"] summary').click();assert.equal(await page.locator('[data-feedback-id="a"] .feedback-description p').isVisible(),true);
      await page.locator('#feedback-sort').selectOption('oldest');
      const removed=page.locator('[data-feedback-id="b"]');await removed.locator('.feedback-delete').click();
      await removed.locator('.inline-confirmation .secondary-button').click();assert.equal(calls.at(-1).method,'PATCH');
      await removed.locator('.feedback-delete').click();await page.locator('#feedback-sort').selectOption('user-desc');
      assert.equal(await removed.locator('.inline-confirmation').isVisible(),true);
      state.failChange(true);await removed.locator('.inline-confirmation .danger-button').click();await removed.locator('[role="alert"]').waitFor();
      assert.equal(await removed.isVisible(),true);assert.equal(await removed.locator('.inline-confirmation .danger-button').isEnabled(),true);
      state.failChange(false);await removed.locator('.inline-confirmation .danger-button').click();await removed.waitFor({state:'detached'});
      assert.equal(calls.at(-1).path,'/v1/admin/feedback/b');assert.equal(calls.at(-1).method,'DELETE');
      assert.deepEqual(await order(),['a','d','c']);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
      for(const selector of ['#feedback-sort','#reload-feedback','.feedback-actions select','.feedback-delete','.feedback-description > summary']){
        for(const box of await page.locator(selector).evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().height)))assert.ok(box>=44);
      }
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase5-feedback-admin-${width}.png`,fullPage:true});
      // Supporting navigation and feedback dialog stay consistent on a phone and desktop.
      await page.locator('#close-feedback-admin').click();await page.locator('#more-send-feedback').click();
      await page.locator('#feedback-title').fill('Mobil feedback');await page.locator('#feedback-description').fill('En beskrivelse');
      if(width<400){await page.evaluate(()=>{document.documentElement.style.setProperty('--dialog-viewport-height','500px');document.documentElement.style.setProperty('--dialog-viewport-bottom','344px');});await page.locator('#feedback-description').focus();await page.evaluate(()=>keepFocusedDialogControlVisible({offsetTop:0,height:500}));await page.waitForFunction(()=>document.querySelector('#feedback-description').getBoundingClientRect().bottom<=document.querySelector('#feedback-form .dialog-footer').getBoundingClientRect().top+1);const input=await page.locator('#feedback-description').boundingBox(),footer=await page.locator('#feedback-form .dialog-footer').boundingBox();await page.screenshot({path:`build/phase5-keyboard-${width}.png`});assert.ok(await page.locator('#feedback-dialog').evaluate(n=>n.querySelector('.dialog-header').getBoundingClientRect().top<=n.getBoundingClientRect().top+1),'sticky header covers the dialog top while scrolling');assert.ok(input.y+input.height<=footer.y+1,JSON.stringify({input,footer,dialog:await page.locator("#feedback-dialog").boundingBox()}));}
      await page.screenshot({path:`build/phase5-feedback-dialog-${width}.png`});
      assert.equal(await page.locator('#feedback-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),true);
      assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });
}
test('feedback admin handles empty results and failed loading with retry',async()=>{
  const state=await setup(390,true);try{
    state.failLoad(true);await state.page.locator('#more-feedback-admin').click();await state.page.locator('#feedback-admin-message.error').waitFor();
    assert.match(await state.page.locator('#feedback-admin-message').textContent(),/kunne ikke hentes/);
    state.failLoad(false);state.setItems([]);await state.page.locator('#reload-feedback').click();await state.page.waitForFunction(()=>document.querySelector('#feedback-admin-list').getAttribute('aria-busy')==='false');
    assert.equal(await state.page.locator('.feedback-card').count(),0);assert.match(await state.page.locator('#feedback-admin-message').textContent(),/ingen indsendte/);
  }finally{await state.browser.close();}
});

for(const width of [320,390,768,1024,1280]){
  test(`supporting nutrition controls, states and dialogs at ${width}px`,async()=>{
    const state=await setup(width,true);const {page,browser}=state;
    let failLoad=true,failSave=true;
    const food={foodId:'food',danishName:'Letmælk',datasetVersion:'v1',carbohydrateGrams:4.5};
    let entries=[{productTemplateId:'milk',key:'MILK',name:'Letmælk med et langt navn som skal ombrydes naturligt',aliases:['Mælk'],status:'MISSING',nutrition:null,dtuSuggestion:{classification:'EXACT',candidates:[{food}]},recipeUsages:[]}];
    const writes=[];
    try{
      await page.route('http://madkursus.test/v1/admin/product-template-nutrition**',async route=>{
        if(route.request().method()==='GET')return route.fulfill({status:failLoad?500:200,json:failLoad?{message:'Load failed'}:entries});
        writes.push({path:new URL(route.request().url()).pathname,body:route.request().postDataJSON()});
        if(failSave)return route.fulfill({status:500,json:{message:'Database failure'}});
        entries[0].nutrition=writes.at(-1).body;entries[0].status='KNOWN';return route.fulfill({json:entries[0]});
      });
      await page.locator('#more-nutrition').click();await page.locator('#nutrition-admin-message.error').waitFor();assert.equal(await page.locator('#reload-nutrition').isVisible(),true);
      failLoad=false;await page.locator('#reload-nutrition').click();await page.locator('.nutrition-row').waitFor();
      await page.locator('#nutrition-dtu-status').selectOption('READY');
      const check=page.locator('.nutrition-product input');const bounds=await check.boundingBox();assert.ok(bounds.width<=24&&bounds.height<=24);
      await page.locator('.nutrition-product strong').click();assert.equal(await check.isChecked(),true);
      const gap=await page.locator('.nutrition-product').evaluate(n=>n.querySelector('.nutrition-product-name').getBoundingClientRect().left-n.querySelector('input').getBoundingClientRect().right);assert.ok(gap>=0&&gap<=12);
      await page.locator('#nutrition-search').fill('zzzz');assert.equal(await page.locator('.nutrition-row').count(),0);assert.match(await page.locator('#nutrition-admin-message').textContent(),/Ingen varer matcher/);
      await page.locator('#nutrition-search').fill('');assert.equal(await check.isChecked(),true);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
      for(const n of await page.locator('.nutrition-row-actions button,#select-all-nutrition').all()){const box=await n.boundingBox();if(await n.getAttribute('type')!=='checkbox')assert.ok(box.height>=44);}
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase5-nutrition-admin-${width}.png`,fullPage:true});
      await page.locator('#approve-selected-dtu').click();await page.locator('#nutrition-bulk-confirm-dialog').waitFor();
      assert.ok(await page.locator('#close-nutrition-bulk-confirm').getAttribute('aria-label'));assert.equal(await page.locator('#nutrition-bulk-confirm-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),true);
      await page.locator('#cancel-nutrition-bulk-confirm').click();assert.equal(writes.length,0);
      await page.locator('.nutrition-row-actions button').first().click();await page.locator('#nutrition-edit-dialog').waitFor();
      await page.locator('#nutrition-grams').fill('4,5');await page.locator('#nutrition-source').fill('Min kilde');
      await page.locator('#nutrition-edit-form button[type="submit"]').click();await page.locator('#nutrition-edit-error').waitFor();
      assert.match(await page.locator('#nutrition-edit-error').textContent(),/oplysninger er bevaret/);assert.equal(await page.locator('#nutrition-grams').inputValue(),'4,5');assert.equal(await page.locator('#nutrition-source').inputValue(),'Min kilde');
      assert.equal(await page.locator('#nutrition-edit-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),true);
      assert.ok(await page.locator('#close-nutrition-edit').getAttribute('aria-label'));
      await page.screenshot({path:`build/phase5-nutrition-edit-${width}.png`});
      failSave=false;await page.locator('#nutrition-edit-form button[type="submit"]').click();await page.locator('#nutrition-edit-dialog').waitFor({state:'hidden'});
      assert.equal(writes.length,2);assert.equal(writes[0].path,'/v1/admin/product-template-nutrition/milk');assert.deepEqual(writes[1].body,writes[0].body);
      // The existing matching dialog has the same accessible header/body structure.
      await page.evaluate(entry=>openNutritionMatch(entry),entries[0]);await page.locator('#nutrition-match-dialog').waitFor();
      assert.ok(await page.locator('#close-nutrition-match').getAttribute('aria-label'));assert.equal(await page.locator('#nutrition-match-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),true);await page.locator('#close-nutrition-match').click();
      entries=[];await page.evaluate(()=>loadNutritionAdmin());assert.match(await page.locator('#nutrition-admin-message').textContent(),/ingen varer at gennemgå/);
      assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });
}

for(const width of [320,390,1280])test(`supporting More and login consistency at ${width}px`,async()=>{
 const {page,browser,errors}=await setup(width,false);try{
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  for(const button of await page.locator('.settings-list > button:visible').all())assert.ok((await button.boundingBox()).height>=44);
  await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase5-more-${width}.png`,fullPage:true});
  await page.evaluate(()=>showUnauthenticatedApp());await page.locator('#auth-screen').waitFor();
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.equal(await page.locator('#login-username').getAttribute('autocomplete'),'username');
  await page.screenshot({path:`build/phase5-login-${width}.png`});assert.deepEqual(errors,[]);
 }finally{await browser.close();}
});

test('equal feedback usernames have deterministic time and ID ties independent of creator UUID',async()=>{
 const state=await setup(390,true);try{
  state.setItems([
   {...sortingItems[0],id:'tie-b',createdByUsername:'Anna',createdAt:'2026-10-01T12:00:00Z'},
   {...sortingItems[1],id:'tie-a',createdByUsername:'anna',createdAt:'2026-10-01T12:00:00Z'},
   {...sortingItems[2],id:'newest',createdByUsername:'ANNA',createdAt:'2026-10-05T12:00:00Z'}
  ]);
  await state.page.locator('#more-feedback-admin').click();await state.page.locator('.feedback-card').first().waitFor();
  for(const value of ['user-asc','user-desc']){
   await state.page.locator('#feedback-sort').selectOption(value);
   assert.deepEqual(await state.page.locator('.feedback-card').evaluateAll(rows=>rows.map(r=>r.dataset.feedbackId)),['newest','tie-a','tie-b']);
  }
  assert.equal(state.calls.filter(c=>c.method!=='GET').length,0);
 }finally{await state.browser.close();}
});
