const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
let browser;
before(async () => { browser = await chromium.launch({headless:true, channel:process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'}); });
after(async () => { await browser?.close(); });

async function editor(t, {unit='PIECE', quantity=6, presence=false, width=390, hold=false, fail=false}={}) {
  const page = await browser.newPage({viewport:{width, height:844}, serviceWorkers:'block'});
  t.after(() => page.close());
  const calls=[], errors=[];
  const item={id:'eggs', product:{id:'product', name:'Æg', category:'EGG', defaultUnit:unit,
    inventoryTrackingMode:presence?'PRESENCE':'EXACT'}, quantity:presence?null:quantity, unit, purchased:false};
  let deleted=false, release;
  const gate=hold?new Promise(resolve => {release=resolve;}):Promise.resolve();
  page.on('pageerror', e => errors.push(e.message));
  await page.route('http://madkursus.test/**', async route => {
    const request=route.request(), url=new URL(request.url());
    if (url.pathname.startsWith('/v1/')) {
      let body=[];
      if (url.pathname.endsWith('/me')) body={id:'user',username:'Shopper',admin:false};
      if (url.pathname.endsWith('/csrf')) body={token:'csrf'};
      if (url.pathname.endsWith('/registration-status')) body={enabled:false};
      if (url.pathname==='/v1/shopping-list') body=deleted?[]:[item];
      if (url.pathname.startsWith('/v1/shopping-list/items/')) {
        const payload=request.postData()?JSON.parse(request.postData()):null;
        calls.push({method:request.method(), path:url.pathname, payload});
        await gate;
        if (fail) return route.fulfill({status:409,json:{message:'Kunne ikke gennemføre handlingen'}});
        if (request.method()==='DELETE') {deleted=true; return route.fulfill({status:204});}
        if (payload && payload.quantity!=null) item.quantity=payload.quantity;
        if (url.pathname.endsWith('/purchase')) item.purchased=true;
        body=item;
      }
      return route.fulfill({json:body});
    }
    const file=path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname);
    try {return route.fulfill({body:await fs.readFile(file),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});}
    catch {return route.fulfill({status:404,body:''});}
  });
  await page.goto('http://madkursus.test/');
  await page.locator('#application').waitFor();
  await page.locator('#show-shopping').click();
  await page.getByRole('button',{name:'Rediger Æg',exact:true}).click();
  t.after(() => assert.deepEqual(errors, []));
  return {page,calls,item,release, input:page.locator('#edit-shopping-quantity'),dialog:page.locator('#edit-shopping-dialog')};
}

test('Gem persists edited 10 PIECE without purchasing', async t => {
  const {page,input,dialog,calls,item}=await editor(t);
  await input.fill('10'); await page.locator('#save-shopping-item').click();
  await dialog.waitFor({state:'hidden'});
  assert.deepEqual(calls,[{method:'PATCH',path:'/v1/shopping-list/items/eggs',payload:{quantity:10}}]);
  assert.equal(item.quantity,10); assert.equal(item.purchased,false);
});
test('Købt sends edited 10 in one purchase request and completes the row', async t => {
  const {page,input,dialog,calls}=await editor(t);
  await input.fill('10'); await page.locator('#purchase-edit-shopping').click();
  await dialog.waitFor({state:'hidden'});
  assert.deepEqual(calls,[{method:'POST',path:'/v1/shopping-list/items/eggs/purchase',payload:{quantity:10}}]);
  assert.equal(await page.locator('#shopping-active-list .shopping-row').count(),0);
  assert.equal(await page.locator('#shopping-purchased-list .shopping-row-quantity').textContent(),'10 stk');
});
for (const close of ['Annuller','X','Escape']) test(`${close} discards changes without mutation`, async t => {
  const {page,input,dialog,calls}=await editor(t);
  await input.fill('10');
  if(close==='Escape') await page.keyboard.press('Escape');
  else await page.locator(close==='X'?'#close-edit-shopping':'#cancel-edit-shopping').click();
  await dialog.waitFor({state:'hidden'});
  assert.deepEqual(calls,[]);
  await page.getByRole('button',{name:'Rediger Æg',exact:true}).click();
  assert.equal(await input.inputValue(),'6');
});
test('Fjern deletes immediately without confirmation or saving edits', async t => {
  const {page,input,calls,dialog}=await editor(t);
  await input.fill('10'); await page.locator('#request-delete-shopping-item').click();
  await dialog.waitFor({state:'hidden'});
  assert.deepEqual(calls,[{method:'DELETE',path:'/v1/shopping-list/items/eggs',payload:null}]);
  assert.equal(await page.locator('#delete-shopping-confirmation').count(),0);
});
for (const [unit,quantity,increment] of [['PIECE',6.5,1],['GRAM',250,100],['MILLILITER',250,100]]) {
  test(`${unit} convenience increment is ${increment}, direct editing keeps existing validity`, async t => {
    const {page,input,calls}=await editor(t,{unit,quantity});
    assert.equal(await input.evaluate(el=>el===document.activeElement),false);
    await page.locator('#edit-shopping-plus').click(); assert.equal(Number(await input.inputValue()),quantity+increment);
    assert.equal(await input.evaluate(el=>el===document.activeElement),false);
    await page.locator('#edit-shopping-minus').click(); assert.equal(Number(await input.inputValue()),quantity);
    await input.fill(unit==='PIECE'?'0.5':'1');
    await page.locator('#edit-shopping-minus').click(); assert.ok(Number(await input.inputValue())>0);
    assert.equal(await input.evaluate(el=>el.checkValidity()),true);
    assert.deepEqual(calls,[]);
  });
}
test('dialog initial focus is on its header button, including after a field was previously focused', async t => {
  const {page}=await editor(t);
  const results=await page.evaluate(()=>{
    document.querySelector('#edit-shopping-dialog').close();
    return [...document.querySelectorAll('dialog')].map(dialog=>{
      dialog.showModal();
      const initial=document.activeElement;
      const field=dialog.querySelector('input:not([type="hidden"]), textarea');
      field?.focus();
      dialog.close(); dialog.showModal();
      const reopened=document.activeElement;
      dialog.close();
      return {id:dialog.id, initial:initial===dialog.querySelector('.section-heading button'),
        reopened:reopened===dialog.querySelector('.section-heading button')};
    });
  });
  for(const result of results) {assert.equal(result.initial,true,result.id);assert.equal(result.reopened,true,result.id);}
});
test('desktop Tab and explicit input click still focus quantity and Escape closes',async t=>{
  const {page,input,dialog}=await editor(t,{width:1280});
  assert.equal(await page.evaluate(()=>document.activeElement.id),'close-edit-shopping');
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(()=>document.activeElement.id),'edit-shopping-minus');
  await page.keyboard.press('Tab');
  assert.equal(await input.evaluate(el=>el===document.activeElement&&el.matches(':focus-visible')),true);
  await page.locator('#edit-shopping-plus').click(); await input.click();
  assert.equal(await input.evaluate(el=>el===document.activeElement),true);
  await page.keyboard.press('Escape'); await dialog.waitFor({state:'hidden'});
});
test('other edit and add openers do not override initial focus with an input',async t=>{
  const {page,item}=await editor(t);
  await page.locator('#cancel-edit-shopping').click();
  const results=await page.evaluate(item=>{
    const inputsFocused=[];
    const record=event=>{if(event.target.matches('input, textarea'))inputsFocused.push(event.target.id);};
    document.addEventListener('focusin',record);
    for(const open of [()=>openProductEditor(item.product),openInventoryAdd,openShoppingAdd,()=>openRecipeEditor()]) {
      open(); document.querySelector('dialog[open]').close();
    }
    document.removeEventListener('focusin',record);
    return inputsFocused;
  },item);
  assert.deepEqual(results,[]);
});
test('inventory delete still requires confirmation and can be cancelled',async t=>{
  const {page,item}=await editor(t);
  await page.locator('#cancel-edit-shopping').click();
  const mutations=[];
  await page.route('http://madkursus.test/v1/inventory/**',route=>{
    mutations.push(route.request().method());return route.fulfill({status:204});
  });
  await page.evaluate(item=>openInventoryEditor(item),item);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'close-edit-inventory');
  await page.locator('#request-delete-inventory').click();
  assert.equal(await page.locator('#delete-inventory-confirmation').isVisible(),true);
  assert.deepEqual(mutations,[]);
  await page.locator('#cancel-delete-inventory').click();
  assert.equal(await page.locator('#delete-inventory-confirmation').isVisible(),false);
  assert.deepEqual(mutations,[]);
  await page.locator('#request-delete-inventory').click(); await page.locator('#delete-inventory').click();
  await page.locator('#edit-inventory-dialog').waitFor({state:'hidden'});
  assert.deepEqual(mutations,['DELETE']);
});
test('PRESENCE hides numeric controls and purchases without quantity', async t => {
  const {page,calls,dialog}=await editor(t,{presence:true});
  assert.equal(await page.locator('#edit-shopping-quantity-controls').isVisible(),false);
  assert.equal(await page.locator('#edit-shopping-plus').isVisible(),false);
  await page.locator('#purchase-edit-shopping').click(); await dialog.waitFor({state:'hidden'});
  assert.deepEqual(calls,[{method:'POST',path:'/v1/shopping-list/items/eggs/purchase',payload:{quantity:null}}]);
});
test('double submission is guarded and editor controls are disabled during purchase', async t => {
  const {page,input,calls,release,dialog}=await editor(t,{hold:true});
  await input.fill('10');
  await page.evaluate(() => {purchaseEditedShoppingItem(); purchaseEditedShoppingItem();});
  await page.waitForFunction(()=>document.querySelector('#edit-shopping-form').getAttribute('aria-busy')==='true');
  for(const id of ['save-shopping-item','request-delete-shopping-item','purchase-edit-shopping','edit-shopping-plus','edit-shopping-quantity'])
    assert.equal(await page.locator(`#${id}`).isDisabled(),true);
  await page.keyboard.press('Escape'); assert.equal(await dialog.isVisible(),true);
  release(); await dialog.waitFor({state:'hidden'});
  assert.equal(calls.length,1);
});
test('failed purchase keeps edits and restores controls without a preliminary save', async t => {
  const {page,input,calls,dialog}=await editor(t,{fail:true});
  await input.fill('10'); await page.locator('#purchase-edit-shopping').click();
  await page.locator('#edit-shopping-error').waitFor();
  assert.equal(await dialog.isVisible(),true); assert.equal(await input.inputValue(),'10');
  assert.equal(await page.locator('#purchase-edit-shopping').isEnabled(),true);
  assert.equal(calls.length,1); assert.ok(calls[0].path.endsWith('/purchase'));
});
for(const width of [320,390,1280]) test(`dialog actions fit and have large tap targets at ${width}px`,async t=>{
  const {page}=await editor(t,{width});
  const boxes=await page.locator('.shopping-edit-actions > button').evaluateAll(buttons=>buttons.map(b=>{
    const r=b.getBoundingClientRect(); return {width:r.width,height:r.height,left:r.left,right:r.right,top:r.top};
  }));
  assert.equal(boxes.length,4);
  for(const box of boxes) {assert.ok(box.width>=44);assert.ok(box.height>=48);assert.ok(box.left>=0&&box.right<=width);}
  assert.equal(boxes[0].top,boxes[1].top); assert.ok(boxes[2].top>boxes[0].top);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await fs.mkdir('build',{recursive:true});
  await page.screenshot({path:`build/shopping-editor-${width}.png`});
});
