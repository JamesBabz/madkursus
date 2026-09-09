// CommonJS is used by the focused Node tests; browsers use the shared global helper.
/* Shared request helper supplies session credentials and CSRF; no local storage. */
function createRecipeImport({document, request}) {
  const t = typeof module !== "undefined" && module.exports ? require("./i18n.js").t : globalThis.t;
  const input=document.querySelector('#recipe-import-json'), result=document.querySelector('#recipe-import-result');
  const validate=document.querySelector('#recipe-import-validate'), submit=document.querySelector('#recipe-import-submit');
  const format=document.querySelector('#recipe-import-format'), clear=document.querySelector('#recipe-import-clear');
  let validated=null, busy=false, epoch=0;
  function line(text,tag='p') {const node=document.createElement(tag);node.textContent=text;result.append(node);}
  function invalidate() {validated=null;submit.hidden=true;result.replaceChildren();}
  function controls(value) {busy=value;[validate,submit,format,clear,input].forEach(node=>node.disabled=value);}
  function render(value) {
    result.replaceChildren();
    if (!value.valid) {line(t("render.kladdenKunneIkkeValideres"),'strong');(value.errors||[]).forEach(error=>{
      const item=document.createElement('div');item.className='recipe-import-error';
      const path=document.createElement('code');path.textContent=error.path||'$';
      const message=document.createElement('p');message.textContent=typeof error==='string'?error:error.message;
      item.append(path,message);result.append(item);
    });result.scrollIntoView({block:'center'});return;}
    line(value.imported?t("render.importeretLokalt"):t("render.kladdenErGyldig"),'strong');
    line(`${value.name} · ${value.key}`);
    line(t("render.handlingAction", {action: value.action}));
    line(t("render.ingredientsIngredienserPreparationStepsForberedelsestrinPreparedComponentsPreparedComponentsCookingProcesses", {ingredients: value.ingredients, preparationSteps: value.preparationSteps, preparedComponents: value.preparedComponents, cookingProcesses: value.cookingProcesses, textSteps: value.textSteps, processSteps: value.processSteps}));
    (value.warnings||[]).forEach(warning=>line(t("render.bemaerkWarning", {warning: warning})));
    if(value.imported) {
      line(t("render.genereretCanonicalFileOgMigrationFile", {canonicalFile: value.canonicalFile, migrationFile: value.migrationFile}));
      line(t("render.naesteKorCleanBuildStartAppenOg"));
    }
  }
  async function run(write) {
    if(busy || (write && validated!==input.value))return;
    const draft=input.value, token=++epoch;controls(true);submit.hidden=true;result.replaceChildren();line(write?t("run.importererLokalt"):t("run.validerer"));
    try {
      const value=await request(`/v1/admin/recipe-template-import/${write?'import':'validate'}`,{method:'POST',headers:{'Content-Type':'text/plain; charset=UTF-8'},body:draft});
      if(token!==epoch)return;
      render(value);validated=value.valid&&!value.imported?draft:null;submit.hidden=validated===null;
    } catch(error) {if(token===epoch){validated=null;result.replaceChildren();line(error.message);}}
    finally {if(token===epoch)controls(false);}
  }
  input.addEventListener('input',invalidate);
  validate.addEventListener('click',()=>run(false));submit.addEventListener('click',()=>run(true));
  clear.addEventListener('click',()=>{input.value='';invalidate();input.focus();});
  format.addEventListener('click',()=>{try{input.value=JSON.stringify(JSON.parse(input.value),null,2);invalidate();}catch(error){invalidate();line(t("createRecipeImport.ugyldigJSONMessage", {message: error.message}));}});
  return {reset(){epoch++;input.value='';invalidate();controls(false);}};
}
if(typeof module!=='undefined')module.exports={createRecipeImport};
