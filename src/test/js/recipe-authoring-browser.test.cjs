const {test}=require('node:test');
const assert=require('node:assert/strict');
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const egg={id:'egg-template',name:'Æg',defaultUnit:'PIECE'},milk={id:'milk-template',name:'Mælk',defaultUnit:'MILLILITER'};
const structured={parts:[{text:'Brug '},{recipeIngredientId:'egg',quantity:0.1,unit:'PIECE'},{text:' i '},{preparedComponentId:'mix'}]};
const process={id:'fry',name:'Steg blandingen',description:'Steg forsigtigt på panden.',parameters:[
  {key:'EGG',label:'Æg',type:'INGREDIENT_QUANTITY',required:true,sortOrder:1},
  {key:'MIX',label:'Æggemasse',type:'INGREDIENT_QUANTITY',required:false,sortOrder:2},
  {key:'TIME',label:'Stegetid',type:'DURATION',source:'OVERRIDEABLE_DEFAULT',defaultValue:{durationSeconds:60},sortOrder:3},
  {key:'ACTIVE',label:'Arbejdstid',type:'DURATION',source:'OVERRIDEABLE_DEFAULT',defaultValue:{durationSeconds:30},sortOrder:4},
  {key:'HEAT',label:'Varme',type:'HEAT_LEVEL',required:true,defaultValue:{heatLevel:'MEDIUM'},sortOrder:5},
  {key:'EXTRA',label:'Andre ingredienser',type:'INGREDIENT_LIST',required:false,sortOrder:6},
  ...['TEXT','NUMBER','TEMPERATURE','QUANTITY'].map((type,i)=>({key:type,label:type==='TEXT'?'Bemærkning':type==='NUMBER'?'Antal':type==='TEMPERATURE'?'Temperatur':'Vand',type,required:false,unit:type==='QUANTITY'?'MILLILITER':null,sortOrder:i+7}))]};
const rendered={processName:process.name,instructions:[process.description],activeDurationSeconds:30,passiveDurationSeconds:90,durationSummary:'1 minut og 30 sekunder',completionCriterion:'Gylden',warnings:['Pas på varmen']};
const base={id:'recipe',name:'Æggeret',description:'En lille ret',ingredients:[
  {id:'egg',productTemplate:egg,quantity:0.5,unit:'PIECE',sortOrder:1},
  {id:'milk',productTemplate:milk,quantity:90,unit:'MILLILITER',sortOrder:2}],
  preparedComponents:[{id:'mix',key:'MIX',name:'Æggemasse',sortOrder:1,ingredients:[
    {id:'allocation',recipeIngredientId:'egg',productTemplate:egg,quantity:0.25,unit:'PIECE',sortOrder:1}],
    preparationSteps:[{id:'prep1',instruction:'Pisk sammen',sortOrder:1},{id:'prep2',instruction:'Brug æg i æggemasse',sortOrder:2,structuredInstruction:structured}]}],
  preparationSteps:[{id:'prep',instruction:'Brug æg i æggemasse',sortOrder:1,structuredInstruction:structured}],
  equipmentRequirements:[{id:'pan',equipmentType:'PAN',label:'Lille pande',sortOrder:1}],equipment:['Lille pande'],
  steps:[{id:'process-step',type:'PROCESS',cookingProcessId:'fry',sortOrder:1,renderedProcess:rendered,parameterBindings:[
    {parameterKey:'EGG',recipeIngredientId:'egg',productTemplateId:egg.id,quantity:0.25,unit:'PIECE'},
    {parameterKey:'MIX',preparedComponentId:'mix'},{parameterKey:'TIME',durationSeconds:90},
    {parameterKey:'ACTIVE',durationSeconds:45},{parameterKey:'HEAT',heatLevel:'MEDIUM_HIGH'},
    {parameterKey:'EXTRA:milk',recipeIngredientId:'milk',productTemplateId:milk.id,quantity:20,unit:'MILLILITER'},
    {parameterKey:'TEXT',text:'Vend én gang'},{parameterKey:'NUMBER',number:2},
    {parameterKey:'TEMPERATURE',temperatureCelsius:75},{parameterKey:'QUANTITY',quantity:10,unit:'MILLILITER'},
    {parameterKey:'LEGACY',text:'Bevar mig'}]},
    {id:'text-step',type:'TEXT',instruction:'Brug æg i æggemasse',structuredInstruction:structured,parameterBindings:[],sortOrder:2}]};
const stove={id:'stove',name:'Mit komfur',equipmentType:'STOVE',heatSource:'INDUCTION',minimumLevel:1,maximumLevel:9,heatMappings:{LOW:'2',MEDIUM:'5',HIGH:'8'},preferred:true};

async function fixture(width=390){
  const browser=await chromium.launch({headless:true,channel:require('node:process').env.CHAT_TEST_BROWSER_CHANNEL||'msedge'});
  const page=await browser.newPage({viewport:{width,height:844},serviceWorkers:'block',reducedMotion:'reduce'});
  const state={recipe:structuredClone(base),writes:[],reads:[],fail:false,errors:[]};page.on('pageerror',e=>state.errors.push(e.message));
  await page.route('http://madkursus.test/**',async route=>{
    const request=route.request(),url=new URL(request.url());
    if(url.pathname.startsWith('/v1/')){
      let body=[];if(request.method()==='GET')state.reads.push(url.pathname+url.search);
      else state.writes.push({url:url.pathname,method:request.method(),body:request.postDataJSON()});
      if(url.pathname.endsWith('/me'))body={id:'user',username:'Test',admin:false};
      if(url.pathname.endsWith('/csrf'))body={token:'csrf'};
      if(url.pathname.endsWith('/registration-status'))body={enabled:false};
      if(url.pathname==='/v1/product-templates')body=[egg,milk];
      if(url.pathname==='/v1/cooking-processes')body=[process];
      if(url.pathname==='/v1/kitchen-equipment')body=[stove];
      if(url.pathname==='/v1/kitchen-equipment/stove')body={...stove,...request.postDataJSON()};
      if(url.pathname==='/v1/recipes')body=[state.recipe];
      if(['/v1/recipes','/v1/recipes/recipe'].includes(url.pathname)&&request.method()!=='GET'){
        if(state.fail){state.fail=false;return route.fulfill({status:500,json:{message:'Save failed'}});}
        const payload=request.postDataJSON();state.recipe={...structuredClone(payload),id:'recipe',equipment:payload.equipmentRequirements.map(e=>e.label||e.equipmentType),
          ingredients:payload.ingredients.map(i=>({...i,productTemplate:[egg,milk].find(t=>t.id===i.productTemplateId)})),
          steps:payload.steps.map(s=>({...s,renderedProcess:s.type==='PROCESS'?rendered:null}))};body=state.recipe;
      }
      if(url.pathname==='/v1/recipes/recipe'&&request.method()==='GET'){
        const portions=Number(url.searchParams.get('portions')||1);body=structuredClone(state.recipe);
        body.ingredients.forEach(i=>i.quantity=Number(i.quantity)*portions);
        body.preparedComponents.forEach(c=>c.ingredients.forEach(a=>a.quantity=Number(a.quantity)*portions));
      }
      return route.fulfill({json:body});
    }
    try{return route.fulfill({body:await fs.readFile(path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname)),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});}catch{return route.fulfill({status:404,body:''});}
  });await page.goto('http://madkursus.test/');await page.locator('#application').waitFor();
  return {browser,page,state};
}
async function section(page,name){const details=page.locator(`#editor-${name}`);if(!await details.evaluate(n=>n.open))await details.locator(':scope > summary').click();}
async function edit(page){await page.evaluate(()=>openRecipe('recipe',4));await page.locator('#recipe-portions-down').click();await page.waitForFunction(()=>recipePortions===3);await page.locator('#recipe-portions-up').click();await page.waitForFunction(()=>recipePortions===4);await page.locator('#recipe-management > summary').click();await page.locator('#edit-recipe').click();await page.locator('#recipe-editor-dialog').waitFor();}

for(const width of [320,390,1280]){
  test(`simple authoring, ingredient editing, draft cancellation and keyboard viewport at ${width}px`,async()=>{
    const {browser,page,state}=await fixture(width);try{
      await page.evaluate(()=>openRecipeEditor());
      assert.equal(await page.locator('#editor-basics').evaluate(n=>n.open),true);
      assert.equal(await page.locator('#editor-instructions').evaluate(n=>n.open),true);
      await page.locator('#recipe-name').fill('Nem æggeret');
      await page.locator('#add-recipe-ingredient').click();await page.locator('#recipe-template-results button').first().click();
      await page.locator('#recipe-ingredient-quantity').fill('0,5');await page.locator('#save-recipe-ingredient').click();
      const ingredients=page.locator('#recipe-editor-ingredients .editor-item');
      assert.match(await ingredients.first().textContent(),/½/);
      await ingredients.first().getByRole('button',{name:'Rediger',exact:true}).click();
      const id=await page.evaluate(()=>recipeIngredients[0].id);
      await page.locator('#recipe-ingredient-quantity').fill('0,75');await page.locator('#save-recipe-ingredient').click();
      assert.equal(await page.evaluate(()=>recipeIngredients[0].id),id);
      await page.locator('#add-recipe-ingredient').click();await page.locator('#recipe-template-results button').nth(1).click();
      await page.locator('#recipe-ingredient-quantity').fill('90');await page.locator('#save-recipe-ingredient').click();
      await ingredients.nth(1).getByRole('button',{name:'Fjern',exact:true}).click();assert.equal(await ingredients.count(),1);
      await page.locator('#add-recipe-step').click();await page.locator('#text-step-name').fill('Steg kødet');await page.locator('#text-step-instruction').fill('Brun kødet på panden ved høj varme.');
      assert.equal(await page.locator('#process-picker').isVisible(),false);
      await page.locator('#save-text-step').click();
      await section(page,'equipment');await page.locator('#add-recipe-equipment').click();await page.locator('#recipe-equipment-type').selectOption('PAN');await page.locator('#save-recipe-equipment').click();
      assert.equal(await page.locator('#kitchen-equipment-dialog').isVisible(),false);
      assert.equal(state.writes.length,0);
      // Software keyboard viewport integration keeps the one action footer reachable.
      await page.evaluate(()=>{document.documentElement.style.setProperty('--dialog-viewport-height','540px');document.documentElement.style.setProperty('--dialog-viewport-bottom','304px');});
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase4-editor-new-${width}.png`});
      const dialog=page.locator('#recipe-editor-dialog');assert.ok(await dialog.evaluate(n=>n.scrollWidth<=n.clientWidth));
      for(const button of await page.locator('#recipe-form > .dialog-footer button').all())assert.ok((await button.boundingBox()).height>=48);
      await page.locator('#recipe-form button[type="submit"]').click();await page.waitForFunction(()=>!document.querySelector('#recipe-editor-dialog').open);
      assert.equal(state.writes.length,1);assert.equal(state.writes[0].method,'POST');
      assert.equal(Number(state.writes[0].body.ingredients[0].quantity),0.75);
      assert.equal(state.writes[0].body.steps[0].type,'TEXT');assert.equal(state.writes[0].body.steps[0].instruction,'Steg kødet\nBrun kødet på panden ved høj varme.');
      await page.evaluate(()=>openRecipeEditor());await page.locator('#cancel-recipe').click();assert.equal(await page.locator('#discard-recipe-dialog').isVisible(),false);
      await page.evaluate(()=>openRecipeEditor());await page.locator('#recipe-name').fill('Ugemt');await page.locator('#close-recipe-editor').click();await page.locator('#keep-editing-recipe').click();assert.equal(await page.locator('#recipe-name').inputValue(),'Ugemt');
      await page.keyboard.press('Escape');await page.locator('#discard-recipe').click();assert.equal(state.writes.length,1);assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });

  test(`canonical advanced editing, components, references, failed save/retry and round trip at ${width}px`,async()=>{
    const {browser,page,state}=await fixture(width);try{
      await edit(page);
      assert.ok(state.reads.includes('/v1/recipes/recipe?portions=1'));
      assert.equal(await page.locator('#editor-instructions').evaluate(n=>n.open),false);
      assert.equal(await page.evaluate(()=>recipeIngredients[0].quantity),'0.5');
      assert.equal(await page.evaluate(()=>recipePreparedComponents[0].ingredients[0].quantity),0.25);
      const ingredients=page.locator('#recipe-editor-ingredients .editor-item');
      await ingredients.first().getByRole('button',{name:'Fjern',exact:true}).click();assert.equal(await ingredients.count(),2);assert.match(await ingredients.first().locator('.editor-reference-error').textContent(),/bruges/);
      await ingredients.first().getByRole('button',{name:'Rediger',exact:true}).click();await page.locator('#recipe-ingredient-quantity').fill('0.1');await page.locator('#save-recipe-ingredient').click();assert.match(await page.locator('#ingredient-editor-error').textContent(),/mere/);await page.locator('#cancel-recipe-ingredient').click();
      await section(page,'preparation');await page.locator('#recipe-editor-components button').first().click();
      await page.locator('#prepared-component-name').fill('Min æggemasse');
      await page.locator('[data-component-quantity]').first().fill('1');await page.locator('#save-prepared-component').click();assert.match(await page.locator('#component-editor-error').textContent(),/mere/);
      await page.locator('[data-component-quantity]').first().fill('0.25');await page.locator('#save-prepared-component').click();
      assert.equal(await page.evaluate(()=>recipePreparedComponents[0].id),'mix');assert.equal(await page.evaluate(()=>recipePreparedComponents[0].ingredients[0].id),'allocation');
      await section(page,'instructions');const steps=page.locator('#recipe-editor-steps .editor-item');await steps.first().getByRole('button',{name:'Rediger',exact:true}).click();
      assert.equal(await page.locator('.process-advanced').evaluate(n=>n.open),false);
      await page.locator('.process-advanced > summary').click();
      const input=page.locator('[data-parameter-key="MIX"] [data-ingredient-select]');await input.selectOption('milk');
      const allocation=page.locator('[data-parameter-key="MIX"] [data-allocation-quantity]'),unit=page.locator('[data-parameter-key="MIX"] [data-allocation-unit]');
      await allocation.fill('20');await unit.selectOption('DECILITER');assert.equal(Number(await allocation.inputValue()),0.2);
      await unit.selectOption('MILLILITER');assert.equal(Number(await allocation.inputValue()),20);await input.selectOption('component:mix');
      await page.locator('[data-parameter-key="TIME"] [data-duration-minutes]').fill('2');
      await page.locator('[data-parameter-key="TIME"] [data-duration-seconds]').fill('15');
      await page.locator('[data-parameter-key="TEXT"] input').fill('Vend forsigtigt');
      await page.locator('#save-process-step').click();await page.locator('#process-picker').waitFor({state:'hidden'});
      assert.equal(await page.evaluate(()=>recipeSteps[0].parameterBindings.find(b=>b.parameterKey==='LEGACY').text),'Bevar mig');
      await steps.nth(1).getByRole('button',{name:'Flyt op',exact:true}).click();assert.equal(await page.evaluate(()=>recipeSteps[0].type),'TEXT');
      await section(page,'equipment');await page.locator('#recipe-editor-equipment button').first().click();await page.locator('#recipe-equipment-label').fill('Min lille pande');await page.locator('#save-recipe-equipment').click();
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase4-editor-edit-${width}.png`});
      assert.ok(await page.locator('#recipe-editor-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth));
      state.fail=true;await page.locator('#recipe-form button[type="submit"]').click();await page.locator('#recipe-error').waitFor();await page.waitForFunction(()=>!editorSaving);
      assert.equal(await page.locator('#recipe-editor-dialog').isVisible(),true);assert.match(await page.locator('#recipe-error').textContent(),/ændringer er bevaret/);
      const failed=state.writes.at(-1).body;
      await page.locator('#recipe-form button[type="submit"]').click();await page.waitForFunction(()=>!document.querySelector('#recipe-editor-dialog').open);
      assert.deepEqual(state.writes.at(-1).body,failed);
      assert.deepEqual(failed.steps[0].structuredInstruction,structured);assert.equal(failed.steps[1].parameterBindings.find(b=>b.parameterKey==='TIME').durationSeconds,135);
      assert.equal(failed.steps[1].parameterBindings.find(b=>b.parameterKey==='MIX').preparedComponentId,'mix');
      assert.equal(Number(failed.preparedComponents[0].ingredients[0].quantity),0.25);assert.deepEqual(failed.preparedComponents[0].preparationSteps[1].structuredInstruction,structured);
      await page.waitForFunction(()=>currentRecipes[0]?.steps?.length===2);await page.evaluate(()=>openRecipeEditor(structuredClone(currentRecipes[0])));
      assert.deepEqual(await page.evaluate(()=>recipeSteps[1].parameterBindings),failed.steps[1].parameterBindings);
      assert.equal(await page.evaluate(()=>recipeEquipmentRequirements[0].label),'Min lille pande');
      assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });

  test(`owned equipment keeps its configuration separate at ${width}px`,async()=>{
    const {browser,page,state}=await fixture(width);try{
      await page.evaluate(e=>openKitchenEquipment(e),stove);
      await page.locator('#stove-maximum-level').fill('12');await page.locator('[data-heat="HIGH"]').fill('10');
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase4-equipment-${width}.png`});
      assert.ok(await page.locator('#kitchen-equipment-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth));
      await page.locator('#kitchen-equipment-form button[type="submit"]').click();await page.locator('#kitchen-equipment-dialog').waitFor({state:'hidden'});
      assert.equal(state.writes[0].url,'/v1/kitchen-equipment/stove');assert.equal(state.writes[0].body.maximumLevel,12);assert.equal(state.writes[0].body.heatMappings.HIGH,'10');assert.equal(state.writes[0].body.heatSource,'INDUCTION');assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });
}

test('add preparation/component/process, retain references and reject discard only when an active item changes',async()=>{
  const {browser,page,state}=await fixture(320);try{
    await edit(page);await page.locator('#recipe-editor-ingredients button').first().click();await page.locator('#cancel-recipe').click();
    assert.equal(await page.locator('#discard-recipe-dialog').isVisible(),false);assert.equal(state.writes.length,0);
    await page.evaluate(recipe=>openRecipeEditor(recipe),base);
    await section(page,'preparation');await page.locator('#add-recipe-preparation').click();await page.locator('#text-step-instruction').fill('Vask hænderne');await page.locator('#save-text-step').click();
    await page.locator('#add-prepared-component').click();await page.locator('#prepared-component-name').fill('Lidt mælk');
    const milkRow=page.locator('.component-allocation-row').nth(1);await milkRow.locator('input[type="checkbox"]').check();await milkRow.locator('[data-component-quantity]').fill('10');
    await page.locator('#prepared-component-preparation').fill('Mål mælken op');await page.locator('#save-prepared-component').click();
    const componentId=await page.evaluate(()=>recipePreparedComponents[1].id);assert.ok(componentId);
    await section(page,'instructions');await page.locator('#add-process-step').click();await page.locator('#cooking-process-select').selectOption('fry');
    await page.locator('[data-parameter-key="EGG"] [data-ingredient-select]').selectOption(`component:${componentId}`);
    await page.locator('#save-process-step').click();assert.equal(await page.evaluate(()=>recipeSteps.length),3);
    assert.equal(await page.evaluate(()=>recipeSteps[2].parameterBindings.find(b=>b.parameterKey==='EGG').preparedComponentId),componentId);
    await section(page,'preparation');await page.locator('#recipe-editor-components .editor-item').nth(1).getByRole('button',{name:'Fjern',exact:true}).click();
    assert.equal(await page.evaluate(()=>recipePreparedComponents.length),2);
    assert.match(await page.locator('#recipe-editor-components .editor-item').nth(1).locator('.editor-reference-error').textContent(),/bruges/);
    await page.locator('#recipe-form button[type="submit"]').click();await page.waitForFunction(()=>!document.querySelector('#recipe-editor-dialog').open);
    assert.equal(state.writes.length,1);assert.equal(state.writes[0].body.preparationSteps.length,2);assert.equal(state.writes[0].body.preparedComponents.length,2);
    await page.evaluate(()=>openRecipeEditor());await page.locator('#add-recipe-step').click();await page.locator('#text-step-instruction').fill('Ugemt trin');
    await page.locator('#cancel-recipe').click();await page.locator('#discard-recipe-dialog').waitFor();await page.locator('#keep-editing-recipe').click();assert.equal(await page.locator('#text-step-instruction').inputValue(),'Ugemt trin');
    await page.locator('#cancel-text-step').click();await page.locator('#cancel-recipe').click();assert.equal(await page.locator('#discard-recipe-dialog').isVisible(),false);assert.equal(state.writes.length,1);assert.deepEqual(state.errors,[]);
  }finally{await browser.close();}
});

test('all dedicated equipment capability fields remain editable',async()=>{
  const {browser,page,state}=await fixture(390);try{
    const examples=[
      ['OVEN',{ovenModes:['FAN'],minimumTemperatureCelsius:50,maximumTemperatureCelsius:250},'#oven-max-temperature','260','maximumTemperatureCelsius',260],
      ['POT',{capacityMl:2000},'#pot-capacity','3000','capacityMl',3000],
      ['PAN',{diameterMm:240,nonStick:true},'#pan-diameter','280','diameterMm',280],
      ['AIR_FRYER',{capacityMl:4000,minimumTemperatureCelsius:80,maximumTemperatureCelsius:200},'#air-fryer-capacity','5000','capacityMl',5000],
      ['THERMOMETER',{thermometerType:'PROBE'},'#thermometer-type','INSTANT_READ','thermometerType','INSTANT_READ'],
      ['MICROWAVE',{maxPowerWatts:800},'#microwave-power','900','maxPowerWatts',900]];
    for(const [equipmentType,properties,selector,value,key,expected] of examples){
      await page.evaluate(e=>openKitchenEquipment(e),{id:'device',name:'Mit udstyr',equipmentType,preferred:false,...properties});
      const input=page.locator(selector);if(equipmentType==='THERMOMETER')await input.selectOption(value);else await input.fill(value);
      await page.locator('#kitchen-equipment-form button[type="submit"]').click();await page.locator('#kitchen-equipment-dialog').waitFor({state:'hidden'});
      const saved=state.writes.at(-1).body;assert.equal(saved[key],expected);assert.equal(saved.equipmentType,equipmentType);
      if(equipmentType==='OVEN')assert.deepEqual(saved.ovenModes,['FAN']);if(equipmentType==='PAN')assert.equal(saved.nonStick,true);
    }assert.deepEqual(state.errors,[]);
  }finally{await browser.close();}
});

for(const width of [320,390])test(`active ingredient form stays above the simulated keyboard and action footer at ${width}px`,async()=>{
  const {browser,page,state}=await fixture(width);try{
    await page.evaluate(()=>openRecipeEditor());await page.locator('#add-recipe-ingredient').click();await page.locator('#recipe-template-results button').first().click();
    await page.locator('#recipe-ingredient-quantity').fill('0,5');
    await page.evaluate(()=>{
      document.documentElement.style.setProperty('--dialog-viewport-height','500px');document.documentElement.style.setProperty('--dialog-viewport-bottom','344px');
      keepFocusedDialogControlVisible({offsetTop:0,height:500});
    });
    await page.waitForFunction(()=>{const input=document.querySelector('#recipe-ingredient-quantity').getBoundingClientRect(),footer=document.querySelector('#recipe-form > .dialog-footer').getBoundingClientRect();return input.bottom<=footer.top&&input.top>=document.querySelector('#recipe-form > .dialog-header').getBoundingClientRect().bottom;});
    assert.equal(await page.locator('#recipe-ingredient-quantity').evaluate(n=>n===document.activeElement),true);
    assert.equal(await page.locator('#recipe-ingredient-quantity').inputValue(),'0,5');
    assert.ok(await page.locator('#recipe-editor-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth));
    await page.screenshot({path:`build/phase4-editor-keyboard-${width}.png`});assert.deepEqual(state.errors,[]);
  }finally{await browser.close();}
});

for (const width of [320,390,1280]) for (const creating of [false,true]) {
  test(`compact authoring selections ${creating?'new':'existing'} at ${width}px`,async()=>{
    const {browser,page,state}=await fixture(width);
    try {
      const recipe=structuredClone(base);
      recipe.ingredients[0].productTemplate.name='Hakket oksekød med et meget langt ingrediensnavn som skal ombrydes naturligt';
      if(creating){
        await page.evaluate(()=>openRecipeEditor());
        for(const index of [0,1]){
          await page.locator('#add-recipe-ingredient').click();
          await page.locator('#recipe-template-results button').nth(index).click();
          await page.locator('#recipe-ingredient-quantity').fill(index?'90':'0.5');
          await page.locator('#save-recipe-ingredient').click();
        }
        // Use the same long-label fixture for newly selected ingredients.
        await page.evaluate(name=>{recipeIngredients[0].template.name=name;renderRecipeEditor();},recipe.ingredients[0].productTemplate.name);
      }else await page.evaluate(r=>openRecipeEditor(r),recipe);
      async function checkRows(selector){
        const geometry=await page.locator(selector).evaluateAll(rows=>rows.map(row=>{
          const check=row.querySelector('input[type=checkbox]'),text=row.querySelector('span');
          const c=check.getBoundingClientRect(),t=text?.getBoundingClientRect(),r=row.getBoundingClientRect();
          return {checkWidth:c.width,checkHeight:c.height,gap:t?t.left-c.right:Infinity,height:r.height,textHeight:t?.height,right:r.right,width:innerWidth};
        }));
        assert.equal(geometry.length,2);
        for(const g of geometry){assert.ok(g.checkWidth>=16&&g.checkWidth<=24);assert.ok(g.checkHeight<=24);assert.ok(g.gap>=0&&g.gap<=12);assert.ok(g.height>=44&&g.height<=120);assert.ok(g.right<=g.width);}
        if(width<400)assert.ok(geometry[0].textHeight>30,'long text wraps alongside the checkbox');
        assert.ok(await page.locator('#recipe-editor-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth),JSON.stringify(await page.locator('#recipe-editor-dialog').evaluate(n=>({width:n.clientWidth,scroll:n.scrollWidth,offenders:[...n.querySelectorAll('*')].filter(e=>e.getBoundingClientRect().right>n.getBoundingClientRect().right).map(e=>[e.tagName,e.className,e.id,e.getBoundingClientRect().width])}))));
      }
      await section(page,'preparation');
      if(creating)await page.locator('#add-prepared-component').click();
      else await page.locator('#recipe-editor-components button').first().click();
      await checkRows('#prepared-component-ingredients label');
      const componentCheck=page.locator('[data-component-ingredient]').nth(1);
      assert.equal(await componentCheck.isChecked(),false);
      await page.locator('#prepared-component-ingredients label').nth(1).click();
      assert.equal(await componentCheck.isChecked(),true);
      assert.equal(await page.locator('[data-component-quantity]').nth(1).isEnabled(),true);
      await page.evaluate(()=>renderRecipeEditor());assert.equal(await componentCheck.isChecked(),true);
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/selection-component-${creating}-${width}.png`});
      await page.locator('#cancel-prepared-component').click();
      await section(page,'instructions');
      if(creating){await page.locator('#add-process-step').click();await page.locator('#cooking-process-select').selectOption('fry');}
      else await page.locator('#recipe-editor-steps .editor-item').first().getByRole('button',{name:'Rediger',exact:true}).click();
      await page.locator('.process-advanced > summary').click();
      await checkRows('.ingredient-set-member label');
      const checked=page.locator('[data-set-ingredient]').first();
      assert.equal(await checked.isChecked(),false);
      await page.locator('.ingredient-set-member label').first().click();assert.equal(await checked.isChecked(),true);
      await checked.focus();await page.keyboard.press('Space');assert.equal(await checked.isChecked(),false);
      await page.keyboard.press('Space');assert.equal(await checked.isChecked(),true);
      await page.evaluate(()=>renderRecipeEditor());assert.equal(await checked.isChecked(),true);
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/selection-process-${creating}-${width}.png`});
      // Existing bound selections also remain checked when reopening an edit form.
      if(!creating){assert.equal(await page.locator('[data-set-ingredient]').nth(1).isChecked(),true);await page.locator('#cancel-process-step').click();await page.locator('#recipe-editor-steps .editor-item').first().getByRole('button',{name:'Rediger',exact:true}).click();await page.locator('.process-advanced > summary').click();assert.equal(await page.locator('[data-set-ingredient]').nth(1).isChecked(),true);}
      assert.equal(state.writes.length,0);assert.deepEqual(state.errors,[]);
    }finally{await browser.close();}
  });
}
