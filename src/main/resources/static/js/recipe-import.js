/* Shared request helper supplies session credentials and CSRF; no local storage. */
function createRecipeImport({document, request}) {
  const input=document.querySelector('#recipe-import-json'), result=document.querySelector('#recipe-import-result');
  const validate=document.querySelector('#recipe-import-validate'), submit=document.querySelector('#recipe-import-submit');
  const format=document.querySelector('#recipe-import-format'), clear=document.querySelector('#recipe-import-clear');
  let validated=null, busy=false, epoch=0;
  function line(text,tag='p') {const node=document.createElement(tag);node.textContent=text;result.append(node);}
  function invalidate() {validated=null;submit.hidden=true;result.replaceChildren();}
  function controls(value) {busy=value;[validate,submit,format,clear,input].forEach(node=>node.disabled=value);}
  function render(value) {
    result.replaceChildren();
    if (!value.valid) {line('Kladden kunne ikke valideres.','strong');(value.errors||[]).forEach(error=>{
      const item=document.createElement('div');item.className='recipe-import-error';
      const path=document.createElement('code');path.textContent=error.path||'$';
      const message=document.createElement('p');message.textContent=typeof error==='string'?error:error.message;
      item.append(path,message);result.append(item);
    });result.scrollIntoView({block:'center'});return;}
    line(value.imported?'Importeret lokalt':'Kladden er gyldig','strong');
    line(`${value.name} · ${value.key}`);
    line(`Handling: ${value.action}`);
    line(`${value.ingredients} ingredienser · ${value.preparationSteps} forberedelsestrin · ${value.preparedComponents} PreparedComponents · ${value.cookingProcesses} CookingProcesses · ${value.textSteps} TEXT / ${value.processSteps} PROCESS trin`);
    (value.warnings||[]).forEach(warning=>line(`Bemærk: ${warning}`));
    if(value.imported) {
      line(`Genereret: ${value.canonicalFile} og ${value.migrationFile}`);
      line('Næste: Kør clean build, start appen og gennemgå opskriften. Commit og deploy, når du er klar.');
    }
  }
  async function run(write) {
    if(busy || (write && validated!==input.value))return;
    const draft=input.value, token=++epoch;controls(true);submit.hidden=true;result.replaceChildren();line(write?'Importerer lokalt…':'Validerer…');
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
  format.addEventListener('click',()=>{try{input.value=JSON.stringify(JSON.parse(input.value),null,2);invalidate();}catch(error){invalidate();line(`Ugyldig JSON: ${error.message}`);}});
  return {reset(){epoch++;input.value='';invalidate();controls(false);}};
}
if(typeof module!=='undefined')module.exports={createRecipeImport};
