const {test} = require('node:test');
const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const recipe = {id:'dinner', name:'Ris med grønt', ingredients:[{id:'rice', productTemplate:{id:'template',name:'Ris'},quantity:6,unit:'GRAM',sortOrder:1}],
  preparationSteps:[{instruction:'Find grøntsagerne frem',sortOrder:1}], equipment:['Gryde'], steps:[
    ...['Kog ris','Steg grønt'].map((name,i) => ({id:`step-${i}`,type:'PROCESS',sortOrder:i+1,parameterBindings:[],renderedProcess:{processName:name,passiveDurationSeconds:60,instructions:['Tilbered forsigtigt'],completionCriterion:'Mørt',warnings:['Pas på dampen']}}))]};

async function fixture(width=390) {
  const browser = await chromium.launch({headless:true,channel:process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge'});
  const page = await browser.newPage({viewport:{width,height:844},serviceWorkers:'block',reducedMotion:'reduce'});
  const state = {mutations:[], completions:new Set(), deductions:0, failNext:false, failMissingNext:false, shoppingAdds:0, shoppingAddedNames:[], loseResponse:false, errors:[],
    requirements:[{productTemplate:{name:'Ris'},trackingMode:'QUANTITY',requiredQuantity:18,physicalQuantity:2,availableQuantity:2,reservedQuantity:0,missingQuantity:16,unit:'GRAM',satisfied:false}]};
  const plan = {id:'plan', name:'Aftensmad', completed:false, recipes:[{id:'occurrence',recipe,recipeName:recipe.name,portions:3,sortOrder:1,status:'PLANNED'},
    {id:'history',recipe:null,recipeName:'Slettet opskrift',portions:2,sortOrder:2,status:'COOKED'},
    {id:'skipped',recipe,recipeName:recipe.name,portions:2,sortOrder:3,status:'SKIPPED'}]};
  page.on('pageerror', error => state.errors.push(error.message));
  await page.route('http://madkursus.test/**', async route => {
    const request=route.request(), url=new URL(request.url());
    if (url.pathname.startsWith('/v1/')) {
      if (request.method()!=='GET') state.mutations.push({url:url.pathname,method:request.method(),body:request.postDataJSON()});
      let body=[];
      if(url.pathname.endsWith('/me')) body={id:'user',username:'Test',admin:false};
      if(url.pathname.endsWith('/csrf')) body={token:'csrf'};
      if(url.pathname.endsWith('/registration-status')) body={enabled:false};
      if(url.pathname==='/v1/recipes') body=[recipe];
      if(url.pathname==='/v1/recipes/dinner') body={...recipe,ingredients:recipe.ingredients.map(i=>({...i,quantity:i.quantity*Number(url.searchParams.get('portions')||1)}))};
      if(url.pathname==='/v1/meal-plans') body=request.method()==='POST' ? plan : [plan];
      if(url.pathname==='/v1/meal-plans/plan') body=plan;
      if(url.pathname.endsWith('/requirements') || url.pathname.endsWith('/calculate-requirements')) body={requirements:state.requirements};
      if(url.pathname.endsWith('/add-missing-to-shopping-list')) {
        if(state.failMissingNext) {state.failMissingNext=false;return route.fulfill({status:500,json:{message:'Shopping service unavailable'}});}
        state.shoppingAdds++;
        state.shoppingAddedNames=state.requirements.filter(r=>!r.satisfied&&!r.warning&&r.trackingMode!=='UNTRACKED').map(r=>r.productTemplate.name);
      }
      if(url.pathname.endsWith('/skip')) {const item=plan.recipes.find(i=>url.pathname.includes(i.id));item.status=item.status==='SKIPPED'?'PLANNED':'SKIPPED';body=plan;}
      if(url.pathname.endsWith('/cook')) {
        if(state.failNext) {state.failNext=false;return route.fulfill({status:500,json:{message:'Transaction rolled back'}});}
        const planned=url.pathname.includes('/meal-plans/');
        const identity=planned?'occurrence':request.postDataJSON().completionId;
        if(!state.completions.has(identity)){state.completions.add(identity);state.deductions++;if(planned)plan.recipes[0].status='COOKED';}
        body={warnings:['Recorded inventory was insufficient']};
        if(state.loseResponse){state.loseResponse=false;return route.fulfill({status:500,json:{message:'Response unavailable'}});}
      }
      return route.fulfill({json:body});
    }
    try {return route.fulfill({body:await fs.readFile(path.join(__dirname,'../../main/resources/static',url.pathname==='/'?'index.html':url.pathname)),contentType:url.pathname.endsWith('.js')?'text/javascript':url.pathname.endsWith('.css')?'text/css':'text/html'});}
    catch {return route.fulfill({status:404,body:''});}
  });
  await page.goto('http://madkursus.test/');await page.locator('#application').waitFor();
  return {browser,page,state,plan};
}

for(const width of [320,390,1280]) {
  test(`one cooking experience: start/cancel, shared timers, direct completion at ${width}px`,async()=>{
    const {browser,page,state}=await fixture(width);
    try {
      await page.evaluate(()=>openRecipe('dinner'));
      await page.locator('#start-cooking').click();
      assert.equal(await page.locator('#recipe-detail-dialog').evaluate(n=>n.classList.contains('is-cooking')),true);
      assert.equal(state.mutations.length,0);
      await page.locator('#cancel-cooking').click();
      assert.equal(state.mutations.length,0);assert.equal(await page.locator('#start-cooking').isVisible(),true);
      await page.locator('#start-cooking').click();
      const timers=page.locator('#recipe-detail-dialog .process-timer');
      await timers.first().getByRole('button',{name:'Start timeren Kog ris',exact:true}).click();
      await timers.nth(1).getByRole('button',{name:'Start timeren Steg grønt',exact:true}).click();
      await timers.first().getByRole('button',{name:'Sæt timeren Kog ris på pause'}).click();
      assert.equal(await page.locator('.process-details').first().evaluate(n=>n.open),true);
      if (width < 600) await page.locator('#recipe-timer-bar > summary').click();
      await page.evaluate(()=>renderRecipeDetail());
      assert.equal(await page.locator('.process-details').first().evaluate(n=>n.open),true);
      assert.equal(await timers.nth(1).getByRole('button',{name:'Sæt timeren Steg grønt på pause'}).isVisible(),true);
      const bounds=await page.locator('#recipe-detail-dialog').boundingBox();
      for(const id of ['cancel-cooking','cook-recipe']) {
        const box=await page.locator(`#${id}`).boundingBox();
        assert.ok(box.height>=48 && box.x>=bounds.x && box.x+box.width<=bounds.x+bounds.width && box.y+box.height<=bounds.y+bounds.height);
      }
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase3-cooking-${width}.png`});
      await page.locator('#cancel-cooking').click();await page.locator('#leave-cooking-dialog').waitFor();
      await page.locator('#keep-cooking').click();
      assert.equal(await timers.nth(1).getByRole('button',{name:'Sæt timeren Steg grønt på pause'}).isVisible(),true);
      await page.locator('#cancel-cooking').click();await page.locator('#confirm-leave-cooking').click();
      assert.equal(state.mutations.length,0);
      assert.equal(await page.evaluate(()=>[...cookingTimers.get('recipe').timers.values()].some(t=>t.started)),false);
      await page.locator('#start-cooking').click();
      await page.evaluate(()=>Promise.all([cookCurrentRecipe(),cookCurrentRecipe()]));
      assert.equal(state.deductions,1);assert.equal(state.mutations.filter(r=>r.url.endsWith('/cook')).length,1);
      assert.equal(state.mutations.at(-1).url,'/v1/recipes/dinner/cook');
      assert.match(state.mutations.at(-1).body.completionId,/^[0-9a-f-]{36}$/);
      assert.equal(await page.locator('#cook-recipe').isDisabled(),true);
      assert.equal(await page.evaluate(()=>cookingTimers.has('recipe')),false);
      await page.evaluate(()=>cookCurrentRecipe());assert.equal(state.deductions,1);
      assert.ok(await page.locator('#recipe-detail-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth));
      assert.ok(await page.locator('#cook-recipe').evaluate(n=>n.getBoundingClientRect().height>=48));
      assert.deepEqual(state.errors,[]);
    } finally {await browser.close();}
  });

  test(`planned occurrence, history and shortages retain context at ${width}px`,async()=>{
    const {browser,page,state,plan}=await fixture(width);
    try {
      await page.evaluate(()=>openMealPlan('plan'));
      assert.match(await page.locator('#meal-plan-recipes').textContent(),/Slettet opskrift/);
      await page.locator('#meal-plan-requirements').click();
      await page.locator('#meal-plan-results').waitFor();
      assert.match(await page.locator('#meal-plan-requirement-list').textContent(),/16 g/);
      await page.locator('.planned-recipe-main').first().click();
      await page.locator('#recipe-detail-dialog').waitFor();
      assert.match(await page.locator('#recipe-cooking-context').textContent(),/Fra Madplan · Aftensmad/);
      assert.match(await page.locator('#recipe-portions').textContent(),/3/);
      assert.equal(await page.locator('#recipe-portions-up').isDisabled(),true);
      await page.locator('#start-cooking').click(); assert.equal(state.mutations.length,0);
      // Global plan selection can change elsewhere; completion must retain the entry occurrence.
      await page.evaluate(()=>{currentMealPlan={id:'another-plan',name:'Another'};});
      await page.locator('#cook-recipe').click();
      await page.waitForFunction(()=>recipeCooking.completed);
      assert.equal(state.mutations[0].url,'/v1/meal-plans/plan/recipes/occurrence/cook');
      assert.equal(state.deductions,1); assert.equal(plan.recipes[0].status,'COOKED');
      await page.locator('#close-recipe-detail').click();await page.locator('#meal-plan-detail-dialog').waitFor();
      assert.match(await page.locator('.planned-recipe-row').first().textContent(),/Lavet/);
      assert.equal(await page.locator('.planned-recipe-row').first().getByRole('button',{name:'Tilbered'}).count(),0);
      assert.ok(await page.locator('#meal-plan-detail-dialog').evaluate(n=>n.scrollWidth<=n.clientWidth));
      await fs.mkdir('build',{recursive:true});await page.screenshot({path:`build/phase3-plan-${width}.png`});
      assert.deepEqual(state.errors,[]);
    } finally {await browser.close();}
  });
}

test('completion failure preserves cooking/timers; lost direct response retries with one identity',async()=>{
  const {browser,page,state}=await fixture();
  try {
    await page.evaluate(()=>openRecipe('dinner'));await page.locator('#start-cooking').click();
    await page.locator('.process-timer').first().getByRole('button',{name:'Start timeren Kog ris',exact:true}).click();
    state.failNext=true;await page.locator('#cook-recipe').click();await page.locator('#recipe-cooking-error').waitFor();
    assert.equal(state.deductions,0);assert.equal(await page.evaluate(()=>recipeCooking.active),true);
    assert.equal(await page.locator('.process-timer').first().getByRole('button',{name:'Sæt timeren Kog ris på pause'}).isVisible(),true);
    state.loseResponse=true;await page.locator('#cook-recipe').click();await page.waitForFunction(()=>!recipeCooking.pending);
    assert.equal(state.deductions,1);assert.equal(await page.evaluate(()=>recipeCooking.active),true);
    await page.locator('#cook-recipe').click();await page.waitForFunction(()=>recipeCooking.completed);
    assert.equal(state.deductions,1);assert.equal(new Set(state.mutations.map(r=>r.body.completionId)).size,1);
    assert.deepEqual(state.errors,[]);
  } finally {await browser.close();}
});

test('planned cancel restores context; contextual skip/replan preserves historical entries without parent navigation',async()=>{
  const {browser,page,state,plan}=await fixture(320);
  try {
    await page.evaluate(()=>openMealPlan('plan'));
    const rows=page.locator('.planned-recipe-row');
    assert.equal(await rows.nth(1).locator('button.planned-recipe-main').count(),0);
    await rows.first().locator('.planned-recipe-tools > summary').press('Enter');
    assert.equal(await page.locator('#recipe-detail-dialog').isVisible(),false);
    await rows.first().getByRole('button',{name:'Spring over',exact:true}).click();
    await page.waitForFunction(()=>currentMealPlan.recipes[0].status==='SKIPPED');
    assert.equal(plan.recipes[0].status,'SKIPPED');assert.equal(state.deductions,0);
    await rows.first().locator('.planned-recipe-tools > summary').click();
    await rows.first().getByRole('button',{name:'Planlæg igen',exact:true}).click();
    await page.waitForFunction(()=>currentMealPlan.recipes[0].status==='PLANNED');
    await rows.first().locator('.planned-recipe-main').press('Enter');
    await page.locator('#start-cooking').click();
    const mutations=state.mutations.length;
    await page.locator('#cancel-cooking').click();
    assert.equal(await page.evaluate(()=>recipeCooking.context.plannedRecipeId),'occurrence');
    await page.locator('#close-recipe-detail').click();await page.locator('#meal-plan-detail-dialog').waitFor();
    assert.equal(state.mutations.length,mutations);assert.equal(state.deductions,0);
    assert.match(await page.locator('#meal-plan-recipes').textContent(),/Slettet opskrift/);
    assert.deepEqual(state.errors,[]);
  } finally {await browser.close();}
});

test('normal detail can complete directly without entering cooking mode',async()=>{
  const {browser,page,state}=await fixture();
  try {
    await page.evaluate(()=>openRecipe('dinner'));assert.equal(await page.evaluate(()=>recipeCooking.active),false);
    await page.locator('#cook-recipe').click();await page.waitForFunction(()=>recipeCooking.completed);
    assert.equal(state.deductions,1);assert.equal(state.mutations[0].url,'/v1/recipes/dinner/cook');
    assert.deepEqual(state.errors,[]);
  } finally {await browser.close();}
});

test('lost planned completion response is confirmed through occurrence status without generic consumption',async()=>{
  const {browser,page,state}=await fixture();
  try {
    await page.evaluate(()=>openMealPlan('plan'));await page.locator('.planned-recipe-main').first().click();
    await page.locator('#start-cooking').click();state.loseResponse=true;await page.locator('#cook-recipe').click();
    await page.waitForFunction(()=>recipeCooking.completed);assert.equal(state.deductions,1);
    assert.equal(state.mutations.filter(r=>r.url==='/v1/recipes/dinner/cook').length,0);
    assert.equal(state.mutations.length,1);assert.deepEqual(state.errors,[]);
  } finally {await browser.close();}
});

test('Escape with active cooking timers confirms before closing and performs no mutation',async()=>{
  const {browser,page,state}=await fixture(320);
  try {
    await page.evaluate(()=>openRecipe('dinner'));await page.locator('#start-cooking').click();
    await page.locator('.process-timer').first().getByRole('button',{name:'Start timeren Kog ris',exact:true}).click();
    await page.keyboard.press('Escape');await page.locator('#leave-cooking-dialog').waitFor();
    assert.equal(await page.locator('#recipe-detail-dialog').isVisible(),true);
    await page.locator('#confirm-leave-cooking').click();await page.waitForFunction(()=>!document.querySelector('#recipe-detail-dialog').open);
    assert.equal(await page.evaluate(()=>cookingTimers.has('recipe')),false);assert.equal(state.mutations.length,0);
  } finally {await browser.close();}
});

test('select portions, review named plan, inspect shortages, and save without cooking',async()=>{
  const {browser,page,state}=await fixture(320);
  try {
    await page.evaluate(async()=>{await loadRecipes();openRecipePlan();});
    await page.locator('.recipe-plan-row input[type="checkbox"]').check();
    assert.equal(await page.locator('.recipe-plan-preview details').evaluate(n=>n.open),false);
    await page.locator('.mini-portions button').last().click();
    await page.locator('#request-save-meal-plan').click();
    assert.match(await page.locator('#recipe-plan-review-list').textContent(),/3 portioner/);
    await page.locator('#calculate-recipe-plan').click();await page.locator('#recipe-plan-results').waitFor();
    assert.match(await page.locator('#recipe-plan-requirements').textContent(),/16 g/);
    await page.locator('#meal-plan-name').fill('Aftensmad');await page.locator('#save-meal-plan-form button[type="submit"]').click();
    await page.locator('#meal-plan-detail-dialog').waitFor();
    const saved=state.mutations.find(r=>r.url==='/v1/meal-plans');assert.equal(saved.body.recipes[0].portions,3);
    assert.equal(state.deductions,0);assert.deepEqual(state.errors,[]);
  } finally {await browser.close();}
});

for (const context of ['saved','draft']) {
  test(`Madplan ${context} requirements show stable shortages first without changing the shopping request`,async()=>{
    const {browser,page,state}=await fixture(320);
    try {
      const quantity=(name,satisfied,missing)=>({productTemplate:{name},trackingMode:'QUANTITY',requiredQuantity:6,
        physicalQuantity:satisfied?10:2,availableQuantity:satisfied?10:2,missingQuantity:missing,unit:'GRAM',satisfied});
      state.requirements=[quantity('Mel',true,0),quantity('Ris',false,4),
        {...quantity('Ukendt',false,null),warning:'Mængden kan ikke beregnes sikkert.'},quantity('Salt',true,0),
        quantity('Æg',false,6),{productTemplate:{name:'Olie'},trackingMode:'PRESENCE',satisfied:false},
        {...quantity('Anden ukendt',false,null),warning:'Enheden kan ikke sammenlignes.'}];
      const original=structuredClone(state.requirements);
      const expectedShortages=['Ris','Æg','Olie'];
      let list,add;
      if(context==='saved') {
        await page.evaluate(()=>openMealPlan('plan'));
        await page.locator('#meal-plan-requirements').click();
        await page.locator('#meal-plan-results').waitFor();
        list=page.locator('#meal-plan-requirement-list');add=page.locator('#meal-plan-add-missing');
      } else {
        await page.evaluate(async()=>{await loadRecipes();openRecipePlan();});
        await page.locator('.recipe-plan-row input[type="checkbox"]').check();
        await page.locator('#request-save-meal-plan').click();
        await page.locator('#meal-plan-name').fill('Madplan');
        await page.locator('#calculate-recipe-plan').click();
        await page.locator('#recipe-plan-results').waitFor();
        list=page.locator('#recipe-plan-requirements');add=page.locator('#add-recipe-missing');
      }
      assert.deepEqual(await list.locator('.requirement-row > strong').allTextContents(),
        ['Ris','Æg','Olie','Mel','Salt','Ukendt','Anden ukendt']);
      assert.equal(await list.locator('.requirement-row').count(),original.length);
      // Sorting uses server satisfaction, including PRESENCE, and never mutates the source calculation.
      assert.deepEqual(state.requirements,original);
      const before=state.mutations.length;
      await add.click();await page.waitForFunction(selector=>!document.querySelector(selector).disabled,
        context==='saved'?'#meal-plan-add-missing':'#add-recipe-missing');
      assert.equal(state.shoppingAdds,1);assert.deepEqual(state.shoppingAddedNames,expectedShortages);
      assert.equal(state.mutations.length,before+1);
      const request=state.mutations.at(-1);
      assert.equal(request.url,context==='saved'?'/v1/meal-plans/plan/add-missing-to-shopping-list':'/v1/recipes/add-missing-to-shopping-list');
      assert.equal(request.method,'POST');
      assert.deepEqual(request.body,context==='saved'?null:{recipes:[{recipeId:'dinner',portions:2}]});
      assert.deepEqual(state.requirements,original);assert.deepEqual(state.errors,[]);
    } finally {await browser.close();}
  });
}

for (const empty of [false,true]) {
  test(`Madplan ${empty?'empty':'sufficient-only'} requirements remain visible without an add-shortages action`,async()=>{
    const {browser,page,state}=await fixture(390);
    try {
      state.requirements=empty?[]:['Mel','Ris'].map(name=>({productTemplate:{name},trackingMode:'QUANTITY',
        requiredQuantity:6,physicalQuantity:10,availableQuantity:10,missingQuantity:0,unit:'GRAM',satisfied:true}));
      await page.evaluate(()=>openMealPlan('plan'));await page.locator('#meal-plan-requirements').click();
      await page.locator('#meal-plan-results').waitFor();
      assert.deepEqual(await page.locator('#meal-plan-requirement-list .requirement-row > strong').allTextContents(),empty?[]:['Mel','Ris']);
      assert.equal(await page.locator('#meal-plan-add-missing').isVisible(),false);
      assert.equal(state.shoppingAdds,0);assert.deepEqual(state.mutations,[]);assert.deepEqual(state.errors,[]);
    } finally {await browser.close();}
  });
}

for (const width of [320,390]) {
  for (const failFirst of [false,true]) {
    test(`adding shortages retains the same named plan draft${failFirst?' after failure and retry':''} at ${width}px`,async()=>{
      const {browser,page,state}=await fixture(width);
      try {
        await page.evaluate(async()=>{await loadRecipes();openRecipePlan();});
        await page.locator('.recipe-plan-row input[type="checkbox"]').check();
        await page.locator('.mini-portions button').last().click();
        await page.locator('#request-save-meal-plan').click();
        await page.locator('#meal-plan-name').fill('Fredagens aftensmad');
        await page.locator('#calculate-recipe-plan').click();
        await page.locator('#recipe-plan-results').waitFor();
        const add=page.locator('#add-recipe-missing');
        await add.scrollIntoViewIfNeeded();await add.focus();
        const before=await page.evaluate(()=>{
          const dialog=document.querySelector('#recipe-plan-dialog');
          window.draftRegression={dialog,selections:recipePlanSelections,review:document.querySelector('#recipe-plan-review-list').firstChild,
            requirement:document.querySelector('#recipe-plan-requirements').firstChild,closeEvents:0};
          dialog.addEventListener('close',()=>window.draftRegression.closeEvents++);
          return {payload:selectedRecipePayload(),scroll:dialog.scrollTop};
        });
        const assertDraft=async()=>{
          const draft=await page.evaluate(()=>({open:document.querySelector('#recipe-plan-dialog').open,
            sameDialog:window.draftRegression.dialog===document.querySelector('#recipe-plan-dialog'),
            sameSelections:window.draftRegression.selections===recipePlanSelections,
            sameReview:window.draftRegression.review===document.querySelector('#recipe-plan-review-list').firstChild,
            sameRequirement:window.draftRegression.requirement===document.querySelector('#recipe-plan-requirements').firstChild,
            closes:window.draftRegression.closeEvents,payload:selectedRecipePayload(),name:document.querySelector('#meal-plan-name').value,
            reviewVisible:!document.querySelector('#recipe-plan-review').hidden,resultsVisible:!document.querySelector('#recipe-plan-results').hidden}));
          assert.equal(draft.open,true);assert.equal(draft.sameDialog,true);assert.equal(draft.sameSelections,true);
          assert.equal(draft.sameReview,true);assert.equal(draft.sameRequirement,true);assert.equal(draft.closes,0);
          assert.deepEqual(draft.payload,before.payload);assert.equal(draft.payload.recipes[0].portions,3);
          assert.equal(draft.name,'Fredagens aftensmad');assert.equal(draft.reviewVisible,true);assert.equal(draft.resultsVisible,true);
          assert.equal(await page.locator('.recipe-plan-row input[type="checkbox"]').isChecked(),true);
          assert.equal(state.mutations.filter(r=>r.url==='/v1/meal-plans').length,0);
          assert.equal(await add.isEnabled(),true);
        };
        if(failFirst) {
          state.failMissingNext=true;await add.click();await page.locator('#recipe-plan-error').waitFor();
          await page.waitForFunction(()=>!document.querySelector('#add-recipe-missing').disabled);
          await assertDraft();assert.equal(state.shoppingAdds,0);
          assert.equal(await page.locator('#recipe-plan-error').textContent(),'Manglerne kunne ikke tilføjes til indkøb. Prøv igen.');
        }
        await add.click();await page.waitForFunction(()=>!document.querySelector('#add-recipe-missing').disabled);
        await assertDraft();assert.equal(state.shoppingAdds,1);
        const requests=state.mutations.filter(r=>r.url==='/v1/recipes/add-missing-to-shopping-list');
        assert.equal(requests.length,failFirst?2:1);
        requests.forEach(request=>assert.deepEqual(request.body,before.payload));
        assert.equal(await page.locator('#recipe-plan-error').isVisible(),false);
        assert.match(await page.locator('#toast').textContent(),/Manglerne er tilføjet til indkøb/);
        assert.equal(await add.evaluate(n=>n===document.activeElement),true);
        if(!failFirst) assert.ok(Math.abs(await page.locator('#recipe-plan-dialog').evaluate(n=>n.scrollTop)-before.scroll)<=2);
        assert.deepEqual(state.errors,[]);
      } finally {await browser.close();}
    });
  }
}
