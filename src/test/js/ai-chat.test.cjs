const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createAiChat, createAiChatLauncher } = require('../../main/resources/static/js/ai-chat.js');

// Small DOM double: test the same event handlers and safe rendering used in the browser.
class Element {
  constructor() { this.children = []; this.listeners = {}; this.dataset = {}; this.value = ''; this.textContent = ''; this.hidden = false; this.scrollHeight = 100; }
  addEventListener(type, listener) { this.listeners[type] = listener; }
  append(...children) {
    children.forEach(child => {
      if (child.parentNode) child.parentNode.children = child.parentNode.children.filter(item => item !== child);
      child.parentNode = this;
      this.children.push(child);
    });
  }
  contains(target) { return target === this || this.children.some(child => child === target || child.contains?.(target)); }
  setAttribute(name, value) { this[name] = value; }
  showModal() { this.open = true; }
  close() { this.open = false; this.fire('close'); }
  replaceChildren() { this.children = []; }
  focus() { this.focused = true; }
  fire(type, values = {}) {
    const event = { preventDefault() { this.prevented = true; }, ...values };
    this.listeners[type]?.(event);
    return event;
  }
}
function setup(modelRequest = () => Promise.resolve({ model: 'test-model:4b' })) {
  const elements = Object.fromEntries(['max-extra', 'model', 'form', 'input', 'send', 'messages', 'empty', 'loading', 'error'].map(id => [id, new Element()]));
  const example = new Element(); example.textContent = 'Hvad kan jeg lave?';
  const root = { hidden: false, ownerDocument: { createElement: () => new Element() },
    querySelector: selector => elements[selector.replace('#chat-', '')], querySelectorAll: () => [example] };
  const calls = [], opened = [];
  let resolve, reject;
  const ui = createAiChat(root, (url, options) => {
    if (url === '/v1/ai/chat/model') return modelRequest(url, options);
    calls.push({ url, ...options });
    return new Promise((yes, no) => { resolve = yes; reject = no; });
  }, recipe => opened.push(recipe));
  return { ...elements, root, example, ui, calls, opened, resolve: answer => resolve(answer), reject: error => reject(error) };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test('model label uses backend metadata as safe text and refreshes on demand', async () => {
  let model = 'configured-model:4b';
  const s = setup(async (url, options) => {
    assert.equal(url, '/v1/ai/chat/model'); assert.equal(options.cache, 'no-store');
    return { model };
  });
  await s.ui.refreshModel(); assert.equal(s.model.textContent, 'Bruger model: configured-model:4b');
  model = '<b>another-model</b>';
  await s.ui.refreshModel(); assert.equal(s.model.textContent, 'Bruger model: <b>another-model</b>');
  assert.equal(s.calls.length, 0);
});

test('model metadata failure never blocks sending a question', async () => {
  const s = setup(async () => { throw new Error('Unavailable'); });
  await s.ui.refreshModel(); assert.equal(s.model.textContent, 'Modelnavn kunne ikke hentes');
  s.example.fire('click'); assert.equal(s.calls.length, 1);
  s.resolve({ answer: 'En idé' }); await tick();
});

test('model refresh deduplicates pending loads and ignores results after logout', async () => {
  let resolve, loads = 0;
  const s = setup(() => { loads++; return new Promise(done => { resolve = done; }); });
  const pending = s.ui.refreshModel(); s.ui.refreshModel(); assert.equal(loads, 1);
  s.ui.reset(); resolve({ model: 'old-session-model' }); await pending;
  assert.equal(s.model.textContent, 'Henter modelnavn…');
});

function withLauncher(s) {
  const launcher = new Element(), drawer = new Element(), close = new Element(), page = new Element(), host = new Element();
  page.hidden = true; drawer.hidden = true; drawer.append(close, host);
  page.append(s.root);
  s.root.checkVisibility = () => s.root.parentNode === host ? !drawer.hidden : !page.hidden;
  const elements = { 'chat-launcher': launcher, 'chat-drawer': drawer, 'chat-minimize': close,
    'ai-view': page, 'chat-drawer-host': host, 'chat-component': s.root };
  const document = new Element(); document.querySelector = selector => elements[selector.slice(1)];
  const controls = createAiChatLauncher(document, s.ui);
  return { ...s, launcher, drawer, close, page, host, controls, document };
}

test('launcher stays closed initially and moves the same component on open and close', () => {
  const s = withLauncher(setup());
  assert.equal(!s.drawer.hidden, false);
  s.launcher.fire('click');
  assert.equal(s.drawer.hidden, false); assert.equal(s.launcher['aria-expanded'], 'true');
  assert.equal(s.root.parentNode, s.host);
  s.close.fire('click');
  assert.equal(s.drawer.hidden, true); assert.equal(s.launcher['aria-expanded'], 'false');
  assert.equal(s.root.parentNode, s.page);
});

test('outside pointer and keyboard focus minimize without cancelling the underlying interaction', () => {
  const s = withLauncher(setup()); s.launcher.fire('click'); s.input.value = 'Min kladde';
  const outside = new Element();
  const event = s.document.fire('pointerdown', { target: outside });
  assert.equal(event.prevented, undefined); assert.equal(s.drawer.hidden, true);
  s.launcher.fire('click'); assert.equal(s.input.value, 'Min kladde');
  s.document.fire('focusin', { target: outside }); assert.equal(s.drawer.hidden, true);
});

test('explicit maximum is submitted separately from chat text', async () => {
  const s = setup(); s['max-extra'].value = '2'; s.input.value = 'Aftensmad?'; s.form.fire('submit');
  assert.deepEqual(JSON.parse(s.calls[0].body), { message: 'Aftensmad?', maxAdditionalIngredients: 2 });
  assert.equal(s['max-extra'].disabled, true);
  s.resolve({ answer: 'Et kontrolleret forslag' }); await tick();
});

test('the limited-purchase example supplies an explicit constraint without parsing prose', async () => {
  const s = setup(); s.example.dataset.maxExtra = '2'; s.example.fire('click');
  assert.equal(JSON.parse(s.calls[0].body).maxAdditionalIngredients, 2);
  s.resolve({ answer: 'Et forslag' }); await tick();
});

test('panel submissions share history, pending state and draft with the dedicated page', async () => {
  const s = withLauncher(setup());
  s.launcher.fire('click'); s.example.fire('click');
  assert.equal(s.calls.length, 1); assert.equal(s.send.disabled, true);
  s.close.fire('click'); s.launcher.fire('click'); s.example.fire('click');
  assert.equal(s.calls.length, 1);
  s.close.fire('click'); s.resolve({ answer: 'Lav en suppe.' }); await tick();
  s.page.hidden = false;
  assert.equal(s.messages.children[1].children[1].textContent, 'Lav en suppe.');
  s.input.value = 'En ny idé?';
  s.launcher.fire('click'); assert.equal(s.input.value, 'En ny idé?');
  s.close.fire('click'); s.form.fire('submit');
  assert.deepEqual(JSON.parse(s.calls[1].body), { message: 'En ny idé?' });
  s.resolve({ answer: 'Ovnbagte grøntsager.' }); await tick();
  assert.equal(s.messages.children.length, 4);
});

test('native Escape close restores the component and keeps provider errors for reopening', async () => {
  const s = withLauncher(setup()); s.launcher.fire('click'); s.example.fire('click');
  s.document.fire('keydown', { key: 'Escape' });
  s.reject({ status: 503 }); await tick();
  assert.equal(s.root.parentNode, s.page);
  s.launcher.fire('click');
  assert.equal(s.error.hidden, false); assert.equal(s.input.value, s.example.textContent);
});

test('sends only the latest message and displays both roles safely with line breaks', async () => {
  const s = setup();
  s.input.value = 'Kartofler uden æg?'; s.form.fire('submit');
  assert.equal(s.calls[0].url, '/v1/ai/chat');
  assert.equal(s.calls[0].method, 'POST');
  assert.deepEqual(JSON.parse(s.calls[0].body), { message: 'Kartofler uden æg?' });
  assert.equal(s.messages.children[0].children[0].textContent, 'Dig');
  assert.equal(s.messages.children[0].children[1].textContent, 'Kartofler uden æg?');
  s.resolve({ answer: '<img src=x onerror=alert(1)>\n- Kartofler\n- Køb olie' }); await tick();
  assert.equal(s.messages.children[1].children[0].textContent, 'Madhjælp');
  assert.equal(s.messages.children[1].children[1].textContent, '<img src=x onerror=alert(1)>\n- Kartofler\n- Køb olie');
  assert.equal(s.messages.scrollTop, s.messages.scrollHeight);
  s.input.value = 'Noget andet'; s.form.fire('submit');
  assert.deepEqual(JSON.parse(s.calls[1].body), { message: 'Noget andet' });
  s.resolve({ answer: 'En anden idé' }); await tick();
});

test('shows loading and prevents duplicates from submit, Enter and examples', async () => {
  const s = setup(); s.input.value = 'Aftensmad?'; s.form.fire('submit');
  assert.equal(s.loading.hidden, false); assert.equal(s.send.disabled, true);
  assert.equal(s.input.disabled, true); assert.equal(s.example.disabled, true);
  s.input.value = 'Dobbelt'; s.form.fire('submit'); s.input.fire('keydown', { key: 'Enter' }); s.example.fire('click');
  assert.equal(s.calls.length, 1);
  s.resolve({ answer: 'Kartofler' }); await tick();
  assert.equal(s.loading.hidden, true); assert.equal(s.input.disabled, false);
  assert.equal(s.example.disabled, false);
});

test('example submits a normal message and dismisses empty state', async () => {
  const s = setup(); s.example.fire('click');
  assert.deepEqual(JSON.parse(s.calls[0].body), { message: s.example.textContent });
  assert.equal(s.empty.hidden, true); assert.equal(s.messages.hidden, false);
  s.resolve({ answer: 'Prøv en suppe' }); await tick();
});

test('provider error is friendly and restores draft for retry', async () => {
  const s = setup(); s.input.value = 'Aftensmad?'; s.form.fire('submit');
  s.reject({ status: 503, message: 'Internal provider details' }); await tick();
  assert.equal(s.error.hidden, false); assert.match(s.error.textContent, /ikke tilgængelig/);
  assert.equal(s.input.value, 'Aftensmad?'); assert.equal(s.send.disabled, false);
  assert.equal(s.messages.children.length, 1);
  s.form.fire('submit'); assert.equal(s.error.hidden, true);
  s.resolve({ answer: 'Nu virker det' }); await tick();
});

test('Enter sends; Shift+Enter and IME composition do not send', async () => {
  const s = setup(); s.input.value = 'Hej\nmed dig';
  assert.equal(s.input.fire('keydown', { key: 'Enter', shiftKey: true }).prevented, undefined);
  s.input.fire('keydown', { key: 'Enter', isComposing: true }); assert.equal(s.calls.length, 0);
  assert.equal(s.input.fire('keydown', { key: 'Enter' }).prevented, true);
  assert.equal(s.calls.length, 1); s.resolve({ answer: 'Hej' }); await tick();
});

test('empty and oversized input cannot send', () => {
  const s = setup(); assert.equal(s.send.disabled, true);
  for (const text of ['', '  \n', 'a'.repeat(4001)]) { s.input.value = text; s.form.fire('submit'); }
  assert.equal(s.calls.length, 0);
});

test('reset on logout clears state and discards late responses', async () => {
  const s = setup(); s.example.fire('click'); s.ui.reset();
  assert.equal(s.calls[0].signal.aborted, true);
  s.resolve({ answer: 'Private answer' }); await tick();
  assert.equal(s.messages.children.length, 0); assert.equal(s.empty.hidden, false);
  assert.equal(s.input.value, ''); assert.equal(s.loading.hidden, true);
});

test('network failures and malformed answers leave a recoverable error', async () => {
  for (const answer of [null, { answer: '' }]) {
    const s = setup(); s.example.fire('click'); s.resolve(answer); await tick();
    assert.equal(s.error.hidden, false); assert.equal(s.send.disabled, false);
  }
  const s = setup(); s.example.fire('click'); s.reject(new TypeError('Failed to fetch')); await tick();
  assert.match(s.error.textContent, /forbindelse/);
});

test('known recipe cards render safe names and navigation without tracking details without losing shared history', async () => {
  const s = withLauncher(setup()); s.launcher.fire('click'); s.example.fire('click');
  const recipes = [
    { id: 'one', source: 'RECIPE', name: '<img src=x>', state: 'COOKABLE', missingIngredients: [], uncertainIngredients: [] },
    { id: 'two', source: 'TEMPLATE', name: 'Chili', state: 'CHECK_QUANTITIES', missingIngredients: ['Bønner'], uncertainIngredients: ['Salt'] }
  ];
  s.resolve({ answer: 'Kendte opskrifter', knownRecipes: recipes }); await tick();
  const message = s.messages.children[1];
  assert.equal(message.children[2].children[0].textContent, '<img src=x>');
  assert.equal(message.children[2].children[1].textContent, 'Du har alt, du skal bruge.');
  assert.match(message.children[3].children[1].textContent, /^Mangler: Bønner\. Tjek mængderne/);
  message.children[2].children[2].fire('click'); message.children[3].children[2].fire('click');
  assert.deepEqual(s.opened, recipes);
  s.close.fire('click'); s.launcher.fire('click');
  assert.equal(s.messages.children[1], message); assert.equal(s.empty.hidden, true);
});
test('compact quick action submits its full prompt and explicit one-extra limit', async () => {
  const s = setup(); s.example.textContent = 'Højst 1 ekstra vare';
  s.example.dataset.chatPrompt = 'Hvad kan jeg lave, hvis jeg køber højst 1 ekstra ingrediens?';
  s.example.dataset.maxExtra = '1'; s.example.fire('click');
  assert.deepEqual(JSON.parse(s.calls[0].body), { message: s.example.dataset.chatPrompt, maxAdditionalIngredients: 1 });
  assert.equal(s.empty.hidden, true); s.resolve({ answer: 'Et svar' }); await tick();
});

test('presence-only known match summary preserves quantity uncertainty', async () => {
  const s = setup(); s.example.fire('click');
  const recipe = { id: 'presence', source: 'RECIPE', name: 'Ret', state: 'CHECK_QUANTITIES', missingIngredients: [], uncertainIngredients: ['Salt', 'Sort peber', 'Rapsolie'] };
  s.resolve({ answer: 'Kendte opskrifter', knownRecipes: [recipe] }); await tick();
  assert.match(s.messages.children[1].children[2].children[1].textContent, /Tjek mængderne/);
  assert.equal(recipe.state, 'CHECK_QUANTITIES'); assert.deepEqual(recipe.uncertainIngredients, ['Salt', 'Sort peber', 'Rapsolie']);
});

for (const value of ['', '0', '1', '2', '3']) {
  test(`ingredient selector preserves hard limit mapping: ${value || 'no limit'}`, async () => {
    const s = setup(); s['max-extra'].value = value; s.example.fire('click');
    const body = JSON.parse(s.calls[0].body);
    if (value === '') assert.equal(Object.hasOwn(body, 'maxAdditionalIngredients'), false);
    else assert.equal(body.maxAdditionalIngredients, Number(value));
    s.resolve({ answer: 'Svar' }); await tick();
  });
}

test('uncertain known recipes never claim there is enough stock', async () => {
  const s = setup(); s.example.fire('click');
  s.resolve({answer:'Kendte opskrifter',knownRecipes:[{id:'known',source:'RECIPE',name:'Ret',state:'CHECK_QUANTITIES',missingIngredients:[],uncertainIngredients:['Salt']}]});
  await tick();
  const card=s.messages.children.at(-1).children.at(-1);
  assert.match(card.children[1].textContent,/Tjek mængderne/);
  assert.doesNotMatch(card.children[1].textContent,/Du har alt/);
});

test('plan candidates render all eight cards with only the existing open action', async () => {
  const s = setup();
  s.example.fire('click');
  const candidates = Array.from({length: 8}, (_, i) => ({id: `recipe-${i}`, name: `Ret ${i}`, source: 'RECIPE', state: 'COOKABLE'}));
  s.resolve({answer: '8 kandidater til 5 måltider, 2 portioner', knownRecipes: [], mealPlanProposal: {requestedMealCount: 5, defaultPortions: 2, candidates}});
  await tick();
  const message = s.messages.children.at(-1);
  const cards = message.children.filter(c => c.className === 'chat-recipe-card');
  assert.equal(cards.length, 8);
  for (const card of cards) {
    assert.equal(card.children.length, 3);
    assert.equal(card.children[2].textContent, 'Åbn opskrift');
    assert.equal(card.children[2].type, 'button');
  }
  cards[7].children[2].fire('click');
  assert.deepEqual(s.opened, [candidates[7]]);
});
