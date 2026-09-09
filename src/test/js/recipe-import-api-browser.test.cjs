const {test}=require('node:test');
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');

test('paste and validate against the production MVC/controller/importer/tool chain', {skip:!process.env.RECIPE_IMPORT_TEST_API}, async()=>{
  const browser=await chromium.launch({headless:true,channel:process.env.CHAT_TEST_BROWSER_CHANNEL||'msedge'});
  try {
    const page=await browser.newPage({viewport:{width:390,height:844},serviceWorkers:'block'});
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.route('http://madkursus.test/**',async route=>{
      const request=route.request(),url=new URL(request.url());
      if(url.pathname==='/v1/admin/recipe-template-import/validate') {
        assert.ok(request.headers()['content-type'].startsWith('text/plain'));
        const response=await fetch(process.env.RECIPE_IMPORT_TEST_API,{method:'POST',body:request.postData()});
        return route.fulfill({status:response.status,contentType:'application/json',body:await response.text()});
      }
      // Only surrounding account/navigation data is stubbed. Validation always reaches Java.
      if(url.pathname.startsWith('/v1/')) {
        let body=[];
        if(url.pathname.endsWith('/me'))body={id:'admin',username:'Admin',admin:true};
        if(url.pathname.endsWith('/csrf'))body={token:'csrf'};
        if(url.pathname.endsWith('/registration-status'))body={enabled:false};
        if(url.pathname==='/v1/admin/recipe-template-import')body={enabled:true};
        return route.fulfill({json:body});
      }
      const file=path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname);
      return route.fulfill({body:await fs.readFile(file),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});
    });
    await page.goto('http://madkursus.test/');await page.locator('#application').waitFor();
    await page.locator('#show-more').click();await page.locator('#more-recipe-import').click();
    const input=page.locator('#recipe-import-json'),validate=page.locator('#recipe-import-validate');
    const draft=JSON.parse(await fs.readFile(path.join(__dirname,'../resources/recipe-import/representative-draft.json'),'utf8'));
    await input.fill(JSON.stringify(draft));await validate.click();await page.getByText('Kladden er gyldig',{exact:true}).waitFor();
    draft.steps[1].instruction.parts[2].quantity=0;
    draft.ingredients[0].productTemplate='UNKNOWN_PRODUCT_REGRESSION';
    await input.fill(JSON.stringify(draft));await validate.click();
    await page.getByText('steps[1].instruction.parts[2].quantity',{exact:true}).waitFor();
    await page.getByText('ingredients[0].productTemplate',{exact:true}).waitFor();
    assert.equal(await page.locator('.recipe-import-error').count(),2);
    assert.equal(await page.locator('#recipe-import-submit').isVisible(),false);
    await page.screenshot({path:'build/recipe-import-api-errors.png',fullPage:true});
    draft.steps[1].instruction.parts[2].quantity=0.125;draft.ingredients[0].productTemplate='PENNE';
    delete draft.steps[0].bindings.PASTA.quantity;delete draft.steps[0].bindings.PASTA.unit;
    await input.fill(JSON.stringify(draft));await validate.click();
    await page.getByText('steps[0].bindings.PASTA.quantity',{exact:true}).waitFor();
    await input.fill('{\n "key": !\n}');await validate.click();
    await page.getByText(/Invalid JSON at line 2, column/).waitFor();
    assert.deepEqual(errors,[]);
  } finally {await browser.close();}
});
