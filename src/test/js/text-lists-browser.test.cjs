const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
let browser;
before(async()=>{browser=await chromium.launch({headless:true,channel:process.env.CHAT_TEST_BROWSER_CHANNEL||'msedge'});});
after(async()=>{await browser?.close();});
async function setup(t,{invalid=false,validationError="Angiv enhed",width=390,clipboardFailure=false}={}) {
  const page=await browser.newPage({viewport:{width,height:844},serviceWorkers:'block'});
  t.after(()=>page.close());
  await page.addInitScript(fail=>{
    window.copied=[];
    Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{if(fail)throw new Error('denied');window.copied.push(text);}}});
  },clipboardFailure);
  const calls=[],errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const inventory=[['Kyllingebryst',600,'GRAM','QUANTITY'],['Løg',3.5,'PIECE','QUANTITY'],['Salt',null,'GRAM','PRESENCE'],['Vandhanevand',null,'MILLILITER','UNTRACKED'],['Mælk',250,'MILLILITER','QUANTITY']]
    .map(([name,quantity,unit,mode],i)=>({id:String(i),product:{id:String(i),name,category:'OTHER',defaultUnit:unit,inventoryTrackingMode:mode},quantity,unit}));
  const result={valid:!invalid,imported:false,items:invalid?[{line:2,text:'Ukendt 2',error:validationError}]:[
    {line:1,name:'Kyllingebryst',quantity:400,unit:'GRAM',newProduct:false},
    {line:2,name:'Æg',quantity:10,unit:'PIECE',newProduct:false},
    {line:3,name:'Cola',quantity:null,newProduct:true}
  ]};
  await page.route('http://madkursus.test/**',async route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.pathname.startsWith('/v1/')) {
      let body=[];
      if(url.pathname.endsWith('/me'))body={id:'user',username:'Shopper',admin:false};
      if(url.pathname.endsWith('/csrf'))body={token:'csrf'};
      if(url.pathname.endsWith('/registration-status'))body={enabled:false};
      if(url.pathname==='/v1/inventory')body=inventory;
      if(req.method()!=='GET')calls.push({path:url.pathname,text:req.postData(),contentType:req.headers()['content-type']});
      if(url.pathname.startsWith('/v1/shopping-list/import'))body={...result,imported:!invalid&&url.pathname==='/v1/shopping-list/import'};
      return route.fulfill({json:body});
    }
    const file=path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname);
    try{return route.fulfill({body:await fs.readFile(file),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});}
    catch{return route.fulfill({status:404,body:''});}
  });
  await page.goto('http://madkursus.test/');await page.locator('#application').waitFor();
  t.after(()=>assert.deepEqual(errors,[]));
  return {page,calls};
}
async function openPaste(page){await page.locator('#show-shopping').click();await page.locator('#open-shopping-text').click();}
test('clipboard contains only ordered bullet lines, Danish quantities/units and PRESENCE, excluding UNTRACKED',async t=>{
  const {page}=await setup(t);await page.locator('#show-inventory').click();await page.locator('#copy-inventory').click();
  assert.equal(await page.locator('#toast-message').textContent(),'✓ Lager kopieret');
  assert.deepEqual(await page.evaluate(()=>window.copied),['- Kyllingebryst: 600 g\n- Løg: 3,5 stk.\n- Salt: har\n- Mælk: 250 ml']);
});
test('clipboard failure gives feedback without claiming success',async t=>{
  const {page}=await setup(t,{clipboardFailure:true});await page.locator('#show-inventory').click();await page.locator('#copy-inventory').click();
  await page.getByText(/Lageret kunne ikke kopieres/).waitFor();assert.deepEqual(await page.evaluate(()=>window.copied),[]);
});
test('paste preview is read only, then confirmation sends the exact plain text once',async t=>{
  const {page,calls}=await setup(t);await openPaste(page);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'close-shopping-text');
  const text='- Kyllingebryst: 400 g\nÆg 10\nCola';
  await page.locator('#shopping-text-input').fill(text);await page.locator('#preview-shopping-text').click();
  await page.locator('#confirm-shopping-text').waitFor();
  assert.equal(calls.length,1);assert.equal(calls[0].path,'/v1/shopping-list/import/preview');
  assert.ok(calls[0].contentType.startsWith('text/plain'));assert.equal(calls[0].text,text);
  assert.match(await page.locator('#shopping-text-preview').textContent(),/Kyllingebryst — \+400 g/);
  assert.match(await page.locator('#shopping-text-preview').textContent(),/Cola — nyt produkt, tilføjes/);
  await page.evaluate(()=>{submitShoppingText(true);submitShoppingText(true);});
  await page.locator('#shopping-text-dialog').waitFor({state:'hidden'});
  assert.equal(calls.length,2);assert.equal(calls[1].path,'/v1/shopping-list/import');assert.equal(calls[1].text,text);
});
for(const action of ['cancel','Escape'])test(`${action} after preview makes no import request`,async t=>{
  const {page,calls}=await setup(t);await openPaste(page);
  await page.locator('#shopping-text-input').fill('Cola');await page.locator('#preview-shopping-text').click();await page.locator('#confirm-shopping-text').waitFor();
  if(action==='cancel')await page.locator('#cancel-shopping-text').click();else await page.keyboard.press('Escape');
  await page.locator('#shopping-text-dialog').waitFor({state:'hidden'});assert.equal(calls.length,1);
});
test('editing after preview invalidates confirmation',async t=>{
  const {page,calls}=await setup(t);await openPaste(page);
  await page.locator('#shopping-text-input').fill('Cola');await page.locator('#preview-shopping-text').click();await page.locator('#confirm-shopping-text').waitFor();
  await page.locator('#shopping-text-input').fill('Karry');assert.equal(await page.locator('#confirm-shopping-text').isVisible(),false);
  await page.evaluate(()=>submitShoppingText(true));assert.equal(calls.length,1);
});
test('invalid line shows original text and error and cannot be confirmed',async t=>{
  const {page,calls}=await setup(t,{invalid:true});await openPaste(page);
  await page.locator('#shopping-text-input').fill('Cola\nUkendt 2');await page.locator('#preview-shopping-text').click();await page.locator('#shopping-text-error').waitFor();
  assert.match(await page.locator('#shopping-text-preview').textContent(),/Linje 2: Ukendt 2 — Angiv enhed/);
  assert.equal(await page.locator('#confirm-shopping-text').isVisible(),false);assert.equal(calls.length,1);
});
for(const width of [320,390,1280])test(`paste dialog fits ${width}px and opens without textarea focus`,async t=>{
  const {page}=await setup(t,{width});await openPaste(page);
  assert.equal(await page.evaluate(()=>document.activeElement.tagName),'BUTTON');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  const box=await page.locator('#shopping-text-input').boundingBox();assert.ok(box.height>=190);assert.ok(box.x>=0&&box.x+box.width<=width);
  await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/shopping-text-${width}.png`});
});

for (const [technical,expected] of [
  ['Quantity must be a whole number','Mængden skal være et helt tal.'],
  ['Unexpected database connection failure','Unexpected database connection failure']
]) test('import preview error: '+technical,async t=>{
  const {page}=await setup(t,{invalid:true,validationError:technical});await openPaste(page);
  await page.locator('#shopping-text-input').fill('Hvedemel: 1000,5');await page.locator('#preview-shopping-text').click();
  await page.locator('#shopping-text-error').waitFor();
  assert.ok((await page.locator('#shopping-text-preview').textContent()).includes(expected));
  assert.equal(await page.locator('#confirm-shopping-text').isVisible(),false);
});
test('static UI, placeholders and accessibility labels use the Danish dictionary',async t=>{
  const {page}=await setup(t);
  assert.equal(await page.locator('#show-inventory').innerText(),'Lager');
  assert.equal(await page.locator('#open-shopping-text').textContent(),'Indsæt liste');
  assert.equal(await page.locator('#auth-tab-login').textContent(),'Log ind');
  assert.equal(await page.locator('#chat-launcher').getAttribute('aria-label'),'Åbn Madhjælp');
  assert.equal(await page.locator('#shopping-text-input').getAttribute('placeholder'),'Kyllingebryst: 400 g\nÆg 10 stk.\nCola');
  const audit=await page.evaluate(async()=>{
    const doc=new DOMParser().parseFromString(await (await fetch('/')).text(),'text/html');
    localizeHtml(doc);
    return [...doc.querySelectorAll('[data-i18n]')].filter(el=>!el.textContent||el.textContent!==t(el.dataset.i18n)).map(el=>el.dataset.i18n);
  });
  assert.deepEqual(audit,[]);
});
test('native required-field validation is Danish and clears when edited',async t=>{
  const {page}=await setup(t);
  const message=await page.evaluate(()=>{
    const input=document.querySelector('#login-username');input.value='';input.checkValidity();return input.validationMessage;
  });
  assert.equal(message,'Udfyld dette felt.');
  assert.equal(await page.evaluate(()=>{
    const input=document.querySelector('#login-username');input.value='Test';input.dispatchEvent(new Event('input',{bubbles:true}));return input.validationMessage;
  }),'');
});
test('resetting a form clears localized custom validity',async t=>{
  const {page}=await setup(t);
  assert.equal(await page.evaluate(()=>{
    const input=document.querySelector('#login-username');input.value='';input.checkValidity();
    document.querySelector('#login-form').reset();input.value='Test';return input.checkValidity();
  }),true);
});
