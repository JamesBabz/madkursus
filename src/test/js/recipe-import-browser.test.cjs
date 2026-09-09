const {test}=require('node:test');
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
for(const scenario of [{width:1280,admin:true,enabled:true},{width:390,admin:true,enabled:true},{width:390,admin:true,enabled:false},{width:1280,admin:false,enabled:true}]) {
  test(`recipe import ${JSON.stringify(scenario)}`,async()=>{
    const browser=await chromium.launch({headless:true,channel:process.env.CHAT_TEST_BROWSER_CHANNEL||'msedge'});
    try {
      const page=await browser.newPage({viewport:{width:scenario.width,height:844},serviceWorkers:'block'});
      const calls=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
      await page.route('http://madkursus.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());let body=[];
        if(url.pathname.startsWith('/v1/')) {
          if(url.pathname.endsWith('/me'))body={id:'admin',username:'Admin',admin:scenario.admin};
          if(url.pathname.endsWith('/csrf'))body={token:'csrf'};
          if(url.pathname.endsWith('/registration-status'))body={enabled:false};
          if(url.pathname==='/v1/admin/recipe-template-import')body={enabled:scenario.enabled};
          if(url.pathname.match(/recipe-template-import\/(validate|import)$/)) {
            calls.push({path:url.pathname,body:req.postData()});assert.equal(req.headers()['x-xsrf-token'],'csrf');
            assert.ok(req.headers()['content-type'].startsWith('text/plain'));
            await new Promise(resolve=>setTimeout(resolve,100));
            body=req.postData()==='{'?{valid:false,errors:['Ugyldig JSON: forventede en nøgle']}:{valid:true,imported:url.pathname.endsWith('/import'),key:'KARBONADER',name:'Karbonader <img src=x>',action:'UPDATE',ingredients:10,preparationSteps:2,preparedComponents:1,cookingProcesses:3,textSteps:2,processSteps:3,warnings:[],canonicalFile:'recipe-templates.json',migrationFile:'V42__update_karbonader.sql'};
          }
          return route.fulfill({json:body});
        }
        const file=path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname);
        try{return route.fulfill({body:await fs.readFile(file),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});}catch{return route.fulfill({status:404,body:''});}
      });
      await page.goto('http://madkursus.test/');await page.locator('#application').waitFor();
      if(!scenario.admin||!scenario.enabled){assert.equal(await page.locator('#more-recipe-import').isVisible(),false);assert.deepEqual(errors,[]);return;}
      await page.locator('#show-more').click();await page.locator('#more-recipe-import').click();
      const input=page.locator('#recipe-import-json'),submit=page.locator('#recipe-import-submit');
      assert.equal(await submit.isVisible(),false);
      await input.fill('{');await page.locator('#recipe-import-validate').click();
      await page.getByText('Ugyldig JSON: forventede en nøgle',{exact:true}).waitFor();assert.equal(await submit.isVisible(),false);
      const draft=await fs.readFile(path.join(__dirname,'../../main/resources/recipe-templates/karbonader-med-kartofler-guleroedder-og-brun-pandesovs.json'),'utf8');
      await input.fill(draft);await page.locator('#recipe-import-validate').click();assert.equal(await input.isDisabled(),true);await submit.waitFor();
      assert.ok((await page.locator('#recipe-import-result').textContent()).includes('UPDATE'));assert.equal(await page.locator('#recipe-import-result img').count(),0);
      await input.fill(draft+' ');assert.equal(await submit.isVisible(),false);
      await page.locator('#recipe-import-validate').click();await submit.waitFor();await submit.click();
      await page.getByText('Importeret lokalt',{exact:true}).waitFor();assert.equal(await submit.isVisible(),false);
      assert.equal(calls.filter(c=>c.path.endsWith('/import')).length,1);assert.equal(calls.at(-1).body,draft.replace(/\r\n/g,'\n')+' ');
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
      await page.screenshot({path:`build/recipe-import-${scenario.width}.png`,fullPage:true});
      await page.locator('#recipe-import-clear').click();assert.equal(await input.inputValue(),'');assert.deepEqual(errors,[]);
    } finally {await browser.close();}
  });
}
