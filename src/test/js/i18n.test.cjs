const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {t,localizeError,currentLocale}=require('../../main/resources/static/js/i18n.js');
const dictionary=require('../../main/resources/static/js/locales/da.js');

test('Danish lookup and named interpolation preserve arbitrary values as text',()=>{
  assert.equal(currentLocale,'da');assert.equal(t('common.cancel'),'Annuller');assert.equal(t('common.save'),'Gem');
  assert.equal(t('shoppingList.import.invalidLine',{line:4,value:'Hvedemel: 1000,5',error:'<b>fejl</b>'}),
    'Linje 4: Hvedemel: 1000,5 — <b>fejl</b>');
  assert.equal(t('common.portions',{count:1}),'1 portion');assert.equal(t('common.portions',{count:3}),'3 portioner');
});
test('missing keys are visible, warn once and do not break later lookups',()=>{
  const warn=console.warn,messages=[];console.warn=message=>messages.push(message);
  try {assert.equal(t('missing.test'),'[missing.test]');assert.equal(t('missing.test'),'[missing.test]');
    assert.equal(messages.length,1);assert.equal(t('common.save'),'Gem');}
  finally {console.warn=warn;}
  assert.equal(t('common.portions'),'{count} portioner');
});
test('known validation is localized and unknown technical details survive the API boundary',()=>{
  const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/app.js'),'utf8');
  const fn=source.match(/function apiError\(response, body\) \{[\s\S]*?\n\}/)[0];
  const context=vm.createContext({t,localizeError});vm.runInContext(fn,context);
  const known=context.apiError({status:400},{message:'Quantity must be a whole number'});
  assert.equal(known.message,'Mængden skal være et helt tal.');assert.equal(known.status,400);
  const unknown=context.apiError({status:500},{message:'Unexpected database connection failure'});
  assert.equal(unknown.message,'Unexpected database connection failure');assert.equal(unknown.status,500);
  assert.equal(localizeError('Quantity must be a whole number'),'Mængden skal være et helt tal.');
});
test('all static and literal JS keys exist and translations contain no HTML entities',()=>{
  const base=path.join(__dirname,'../../main/resources/static');
  for(const file of ['index.html','js/app.js','js/ai-chat.js','js/recipe-import.js','js/i18n.js']){
    const source=fs.readFileSync(path.join(base,file),'utf8');
    const keys=[...source.matchAll(/\bt\(["']([^"']+)["']\s*(?=[,)])/g),...source.matchAll(/data-i18n(?:-[\w-]+)?="([^"]+)"/g)];
    for(const [,key]of keys)assert.ok(Object.hasOwn(dictionary,key),file+': '+key);
  }
  for(const value of Object.values(dictionary))assert.doesNotMatch(JSON.stringify(value),/&#\d+;|&(?:amp|lt|gt|quot);/);
});
