const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {t} = require('../../main/resources/static/js/i18n.js');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/app.js'), 'utf8');
function element() {
  const classes = new Set();
  return {children: [], textContent: '', hidden: false, className: '',
    classList: {toggle(name, enabled) { enabled ? classes.add(name) : classes.delete(name); }, contains(name) {return classes.has(name);}},
    attributes: {}, contains() {return false;}, focus(options) {this.focusOptions = options;}, select() {this.selected = true;},
    setAttribute(name, value) {this.attributes[name] = value;}, append(...children) {this.children.push(...children);},
    replaceChildren(...children) {this.children = children;}};
}
function setup() {
  let now = 1000, next = 0;
  const intervals = new Map(), nodes = new Map();
  const context = vm.createContext({t, window: {}, Date: {now: () => now},
    setInterval(callback) {intervals.set(++next, callback); return next;},
    clearInterval(id) {intervals.delete(id);},
    document: {createElement: element, querySelector(selector) {
      if (!nodes.has(selector)) {
        const node = element(); if (selector.endsWith('timer-bar')) {node.hidden = true; node.append(element(), element());}
        nodes.set(selector, node);
      } return nodes.get(selector);
    }}, renderCarbohydrates() {}, renderUnknownCarbohydrates() {},
    recipePortions: 2, recipeTemplatePortions: 2, recipeCooking: null});
  vm.runInContext('const cookingTimers = new Map();\n' + source.slice(source.indexOf('function clearCookingTimers('), source.indexOf('function renderProcessDetails(')), context);
  for (const name of ['renderProcessDetails', 'renderRecipeDetail', 'renderRecipeTemplateDetail', 'revealProcessEditor']) {
    const lines = source.split(/\r?\n/), start = lines.findIndex(line => line.startsWith(`function ${name}(`));
    const end = lines[start].endsWith('}') ? start : lines.findIndex((line, i) => i > start && line === '}');
    vm.runInContext(lines.slice(start, end + 1).join('\n'), context);
  }
  return {context, nodes, intervals, render: (step, scope = 'recipe', recipe = 'one') => context.renderCookingTimer(step, scope, recipe),
    elapse(ms, update = true) {now += ms; if (update) [...intervals.values()].forEach(fn => fn());}};
}
const process = (id = 'a', passive = 120, active = 60) => ({id, type: 'PROCESS', sortOrder: 1,
  renderedProcess: {processName: 'Simrer', passiveDurationSeconds: passive, activeDurationSeconds: active, durationSummary: '999 minutter'}});
const controls = host => ({name: host.children[0].children[0], display: host.children[1],
  start: host.children[2].children[0], reset: host.children[2].children[1], status: host.children[3]});

test('structured passive duration is preferred; positive active duration is the fallback', () => {
  for (const [passive, active, expected] of [[120, 60, '02:00'], [0, 65, '01:05'], [-1, 60, '01:00'], [null, 60, '01:00']]) {
    const env = setup(), host = env.render(process('a', passive, active));
    assert.equal(controls(host).display.textContent, expected);
    assert.equal(controls(host).name.value, 'Simrer');
    assert.equal(controls(host).start.textContent, 'Start');
    assert.equal(env.intervals.size, 0);
  }
});
test('no timer for nonpositive structured duration, missing rendering, or ordinary text', () => {
  const env = setup();
  for (const step of [process('a', 0, 0), process('a', -1, -4), process('a', null, null), process('a', NaN, Infinity),
    {id: 'b', type: 'PROCESS', instruction: '10 minutter'}, {...process(), type: 'TEXT', instruction: '10 minutter'}]) {
    assert.equal(env.render(step), null);
  }
});
test('start, throttled updates, pause with exact remainder, resume and reset', () => {
  const env = setup(), ui = controls(env.render(process()));
  ui.start.onclick(); assert.equal(ui.start.textContent, 'Pause');
  env.elapse(30500, false); ui.start.onclick();
  assert.equal(ui.display.textContent, '01:30'); assert.equal(env.intervals.size, 0);
  env.elapse(60000); assert.equal(ui.display.textContent, '01:30');
  ui.start.onclick(); env.elapse(500); assert.equal(ui.display.textContent, '01:29');
  ui.reset.onclick(); assert.equal(ui.display.textContent, '02:00');
  assert.equal(ui.start.textContent, 'Start'); assert.equal(env.intervals.size, 0);
});
test('same-name steps run independently and edited names never mutate recipe data', () => {
  const env = setup(), step = process(), before = JSON.stringify(step);
  const a = controls(env.render(step)), b = controls(env.render(process('b', 30)));
  a.name.value = 'Min gryde'; a.name.oninput(); a.start.onclick(); b.start.onclick();
  env.elapse(10000); a.start.onclick(); env.elapse(10000);
  assert.equal(a.display.textContent, '01:50'); assert.equal(b.display.textContent, '00:10');
  a.reset.onclick(); assert.equal(b.display.textContent, '00:10'); assert.equal(JSON.stringify(step), before);
  assert.equal(a.name.value, 'Min gryde');
});
test('finished state identifies edited timer and reset allows starting again', () => {
  const env = setup(), host = env.render(process('a', 1)), ui = controls(host);
  ui.name.value = 'Pasta'; ui.name.oninput(); ui.start.onclick(); env.elapse(5000);
  assert.equal(ui.display.textContent, '00:00'); assert.equal(ui.status.textContent, 'Timeren Pasta er færdig!');
  assert.equal(ui.status.hidden, false); assert.equal(host.classList.contains('timer-finished'), true);
  assert.equal(ui.start.disabled, true); assert.equal(env.intervals.size, 0);
  ui.reset.onclick(); assert.equal(ui.start.disabled, false); assert.equal(ui.status.hidden, true);
  ui.start.onclick(); assert.equal(env.intervals.size, 1);
});
test('recipe and template detail renders preserve timers through new API objects and DOM replacement', () => {
  for (const template of [false, true]) {
    const env = setup(), scope = template ? 'template' : 'recipe';
    const property = template ? 'currentRecipeTemplate' : 'currentRecipe';
    const render = template ? 'renderRecipeTemplateDetail' : 'renderRecipeDetail';
    const selector = template ? '#recipe-template-detail-steps' : '#recipe-detail-steps';
    env.context[property] = {id: 'one', ingredients: [], steps: [process(), {id: 'text', type: 'TEXT', sortOrder: 2, instruction: 'Vent 5 minutter'}]};
    env.context[render]();
    let rows = env.nodes.get(selector).children;
    assert.equal(rows[1].children.length, 0);
    const a = controls(rows[0].children[1]); a.start.onclick(); a.name.value = 'Gryde'; a.name.oninput();
    env.elapse(10000);
    env.context[property] = JSON.parse(JSON.stringify(env.context[property])); env.context[render]();
    rows = env.nodes.get(selector).children;
    const b = controls(rows[0].children[1]); assert.equal(b.name.value, 'Gryde'); assert.equal(b.display.textContent, '01:50');
    env.elapse(10000); assert.equal(b.display.textContent, '01:40'); assert.equal(env.intervals.size, 1);
    env.context.clearCookingTimers(scope); assert.equal(env.intervals.size, 0);
  }
});
test('dialog scopes and recipe IDs isolate sessions, and cleanup releases updates and audio', () => {
  const env = setup(); let closed = 0;
  env.context.window.AudioContext = class {state = 'running'; resume() {} close() {closed++;}};
  const a = controls(env.render(process())), b = controls(env.render(process(), 'template'));
  a.start.onclick(); b.start.onclick(); assert.equal(env.intervals.size, 2);
  const c = controls(env.render(process(), 'recipe', 'two'));
  assert.equal(c.display.textContent, '02:00'); assert.equal(env.intervals.size, 1); assert.equal(closed, 1);
  env.context.clearCookingTimers('template'); assert.equal(env.intervals.size, 0); assert.equal(closed, 2);
  env.context.clearCookingTimers('recipe'); assert.equal(env.render(process()).children[0].children[0].value, 'Simrer');
  assert.match(source, /recipe-detail-dialog'\)\.addEventListener\('close', \(\) => clearCookingTimers\('recipe'\)/);
  assert.match(source, /recipe-template-detail-dialog'\)\.addEventListener\('close', \(\) => clearCookingTimers\('template'\)/);
});
test('audio failures do not break completion or cleanup', () => {
  const env = setup();
  env.context.window.AudioContext = class {state = 'running'; resume() {throw Error('blocked');} createOscillator() {throw Error('unavailable');} close() {throw Error('closed');}};
  const ui = controls(env.render(process('a', 1))); ui.start.onclick(); env.elapse(1000);
  assert.equal(ui.status.hidden, false); assert.equal(env.intervals.size, 0);
  assert.doesNotThrow(() => env.context.clearCookingTimers('recipe'));
});
test('completion starts one three-tone pattern and blank names retain an identifiable finish message', () => {
  const env = setup(); let sounds = 0;
  env.context.window.AudioContext = class {
    state = 'running'; currentTime = 0; destination = {};
    resume() {} close() {}
    createOscillator() {return {frequency: {}, connect() {}, disconnect() {}, start() {sounds++;}, stop() {this.onended();}};}
    createGain() {return {gain: {setValueAtTime() {}, exponentialRampToValueAtTime() {}}, connect() {}, disconnect() {}};}
  };
  const ui = controls(env.render(process('a', 1))); ui.name.value = ' '; ui.name.oninput();
  ui.start.onclick(); env.elapse(1000); env.elapse(1000);
  assert.equal(sounds, 3); assert.equal(ui.status.textContent, 'Timeren Simrer er færdig!');
  env.render(process('a', 1)); assert.equal(sounds, 3);
});

function alertAudio(env) {
  const tones = []; let closed = 0;
  env.context.window.AudioContext = class {
    state = 'running'; destination = {};
    get currentTime() {return env.context.Date.now() / 1000;}
    resume() {} close() {closed++;}
    createOscillator() {
      const tone = {frequency: {}, connect() {}, disconnect() {this.disconnected = true;},
        start(at) {this.startAt = at;}, stop(at) {if (at === undefined) {this.cancelled = true; this.onended?.();} else this.stopAt = at;}};
      tones.push(tone); return tone;
    }
    createGain() {return {gain: {setValueAtTime() {}, exponentialRampToValueAtTime() {}}, connect() {}, disconnect() {}};}
  };
  return {tones, get closed() {return closed;}};
}

test('kitchen alert repeats spaced three-tone patterns without replaying completion on rerender', () => {
  const env = setup(), audio = alertAudio(env), ui = controls(env.render(process('a', 1)));
  ui.start.onclick(); env.elapse(1000);
  assert.equal(audio.tones.length, 3); assert.equal(env.intervals.size, 1);
  assert.deepEqual(audio.tones.map(tone => Math.round((tone.startAt - audio.tones[0].startAt) * 1000)), [0, 240, 480]);
  env.elapse(1800); assert.equal(audio.tones.length, 6);
  env.render(process('a', 1)); assert.equal(audio.tones.length, 6);
  env.elapse(1800); assert.equal(audio.tones.length, 9);
});

test('alert automatically expires at 30 seconds, including throttled callbacks, without clearing finished state', () => {
  for (const jump of [30000, 60000]) {
    const env = setup(), audio = alertAudio(env), ui = controls(env.render(process('a', 1)));
    ui.start.onclick(); env.elapse(1000); const count = audio.tones.length;
    env.elapse(jump);
    assert.equal(audio.tones.length, count); assert.equal(env.intervals.size, 0);
    assert.ok(audio.tones.every(tone => tone.cancelled && tone.disconnected));
    assert.equal(ui.status.hidden, false); assert.equal(ui.start.disabled, true);
  }
});

test('inline and overview reset acknowledge alerts immediately and a restarted timer can sound again', () => {
  for (const overview of [false, true]) {
    const env = setup(), audio = alertAudio(env), ui = controls(env.render(process('a', 1)));
    ui.start.onclick(); env.elapse(1000);
    const reset = overview ? env.nodes.get('#recipe-timer-bar').children[1].children[0].children[3].children[1] : ui.reset;
    reset.onclick(); assert.equal(env.intervals.size, 0);
    assert.ok(audio.tones.every(tone => tone.cancelled && tone.disconnected));
    env.elapse(5000); assert.equal(audio.tones.length, 3);
    ui.start.onclick(); env.elapse(1000); assert.equal(audio.tones.length, 6);
  }
});

test('detail lifecycle cleanup cancels pending tones, alert loop and audio in both scopes', () => {
  for (const scope of ['recipe', 'template']) {
    const env = setup(), audio = alertAudio(env), ui = controls(env.render(process('a', 1), scope));
    ui.start.onclick(); env.elapse(1000); env.context.clearCookingTimers(scope);
    assert.equal(env.intervals.size, 0); assert.equal(audio.closed, 1);
    assert.ok(audio.tones.every(tone => tone.cancelled && tone.disconnected));
    env.elapse(5000); assert.equal(audio.tones.length, 3);
  }
});

test('nearby completions share one loop; acknowledgement and expiry are tracked per finished timer', () => {
  const env = setup(), audio = alertAudio(env);
  const a = controls(env.render(process('a', 1))), b = controls(env.render(process('b', 1.1)));
  a.start.onclick(); b.start.onclick(); env.elapse(1000); env.elapse(100);
  assert.equal(audio.tones.length, 3); assert.equal(env.intervals.size, 1);
  a.reset.onclick(); assert.equal(env.intervals.size, 1);
  env.elapse(1800); assert.equal(audio.tones.length, 6);
  b.reset.onclick(); assert.equal(env.intervals.size, 0);
  assert.ok(audio.tones.every(tone => tone.cancelled));
});

test('separate dialog scopes never stack alert loops', () => {
  const env = setup(), audio = alertAudio(env);
  const a = controls(env.render(process('a', 1))), b = controls(env.render(process('b', 1.1), 'template'));
  a.start.onclick(); b.start.onclick(); env.elapse(1000); env.elapse(100);
  assert.equal(env.intervals.size, 1);
  assert.ok(audio.tones.slice(0, 3).every(tone => tone.cancelled));
  assert.equal(a.status.hidden, false); assert.equal(b.status.hidden, false);
});

test('selecting or renaming a finished timer acknowledges sound while preserving its finished state', () => {
  for (const select of [false, true]) {
    const env = setup(), audio = alertAudio(env), host = env.render(process('a', 1)), ui = controls(host);
    ui.start.onclick(); env.elapse(1000);
    const action = select ? env.nodes.get('#recipe-timer-bar').children[1].children[0].children[4] : host.children[5];
    action.onclick(); assert.equal(env.intervals.size, 0);
    assert.ok(audio.tones.every(tone => tone.cancelled));
    assert.equal(ui.status.hidden, false); assert.equal(ui.start.disabled, true);
  }
});

test('compact rename affordance reveals input only while editing and supports keyboard dismissal', () => {
  const env = setup(), host = env.render(process()), ui = controls(host), edit = host.children[5];
  assert.equal(host.children[0].hidden, true); assert.equal(host.children[4].textContent, 'Simrer');
  edit.onclick(); assert.equal(host.children[0].hidden, false); assert.equal(ui.name.selected, true);
  ui.name.value = 'Min pasta'; ui.name.oninput();
  let prevented = false; ui.name.onkeydown({key: 'Enter', preventDefault() {prevented = true;}});
  assert.equal(prevented, true); assert.equal(host.children[0].hidden, true);
  assert.equal(host.children[4].textContent, 'Min pasta'); assert.equal(edit.attributes['aria-label'], 'Omdøb timeren Min pasta');
  edit.onclick(); ui.name.onblur(); assert.equal(host.children[0].hidden, true);
});
test('sticky bar shows running/paused/finished timers, shares controls, and disappears on reset or close', () => {
  const env = setup(), inline = controls(env.render(process())), bar = env.nodes.get('#recipe-timer-bar');
  assert.equal(bar.hidden, true); inline.start.onclick(); assert.equal(bar.hidden, false);
  let row = bar.children[1].children[0];
  assert.equal(row.children[2].textContent, 'Kører');
  row.children[3].children[0].onclick(); assert.equal(inline.start.textContent, 'Start');
  assert.equal(row.children[2].textContent, 'På pause'); assert.equal(bar.hidden, false);
  inline.name.value = 'Kog pasta'; inline.name.oninput(); assert.equal(row.children[0].textContent, 'Kog pasta');
  bar.open = true; row.children[3].children[0].onclick(); env.elapse(1000);
  assert.equal(row.children[1].textContent, inline.display.textContent); assert.equal(bar.open, true);
  const second = controls(env.render(process('b', 30))); second.start.onclick();
  const rows = bar.children[1].children;
  assert.equal(rows.length, 2); assert.match(bar.children[0].textContent, /2 timere/);
  rows[0].children[3].children[0].onclick(); env.elapse(10000);
  assert.equal(rows[0].children[1].textContent, '01:59'); assert.equal(rows[1].children[1].textContent, '00:20');
  second.reset.onclick(); assert.equal(bar.children[1].children.length, 1);
  row = bar.children[1].children[0]; row.children[3].children[0].onclick(); env.elapse(120000);
  assert.equal(bar.hidden, false); assert.equal(row.children[2].textContent, 'Timeren Kog pasta er færdig!');
  assert.equal(row.children[3].children[0].disabled, true);
  row.children[3].children[1].onclick(); assert.equal(bar.hidden, true); assert.equal(bar.open, false);
  inline.start.onclick(); env.context.clearCookingTimers('recipe'); assert.equal(bar.hidden, true);
});
test('process editor scroll waits for rendering, respects reduced motion, focuses without scrolling, and skips cancelled edits', () => {
  for (const reduced of [false, true]) {
    const env = setup(), picker = element(), select = element(); let frame, scroll;
    env.context.requestAnimationFrame = callback => {frame = callback;};
    env.context.window.matchMedia = () => ({matches: reduced});
    env.context.editingProcessStepIndex = 2;
    picker.scrollIntoView = options => {scroll = options;};
    env.context.revealProcessEditor(picker, select, 2); assert.equal(scroll, undefined);
    frame(); assert.equal(scroll.behavior, reduced ? 'instant' : 'smooth'); assert.equal(scroll.block, 'start');
    assert.equal(select.focusOptions.preventScroll, true);
    scroll = undefined; picker.hidden = true; frame(); assert.equal(scroll, undefined);
    picker.hidden = false; env.context.editingProcessStepIndex = null; frame(); assert.equal(scroll, undefined);
  }
});
test('primary selection uses step identity, survives renaming and rendering, and never changes timer state or list order', () => {
  const env = setup(), a = controls(env.render(process('a', 120))), b = controls(env.render(process('b', 60)));
  a.start.onclick(); b.start.onclick();
  const bar = env.nodes.get('#recipe-timer-bar'), rows = bar.children[1].children;
  const snapshot = () => vm.runInContext('JSON.stringify([...cookingTimers.get("recipe").timers.values()].map(({stepId, end, remaining, started, finished}) => ({stepId, end, remaining, started, finished})))', env.context);
  const before = snapshot(); rows[1].children[4].onclick();
  assert.equal(snapshot(), before); assert.equal(env.intervals.size, 2);
  assert.equal(bar.children[1].children, rows); assert.equal(rows[1].classList.contains('timer-primary'), true);
  assert.equal(rows[0].children[4].attributes['aria-pressed'], 'false');
  assert.equal(rows[1].children[4].attributes['aria-pressed'], 'true');
  bar.open = false; assert.equal(bar.children[0].textContent, '⏱ 2 timere · Simrer 01:00');
  b.name.value = 'Tomatsovs'; b.name.oninput(); assert.match(bar.children[0].textContent, /Tomatsovs 01:00/);
  env.render(process('b', 60)); env.elapse(1000);
  assert.match(bar.children[0].textContent, /Tomatsovs 00:59/);
  rows[0].children[3].children[0].onclick();
  assert.equal(a.start.textContent, 'Start'); assert.equal(rows[1].classList.contains('timer-primary'), true);
  assert.match(bar.children[0].textContent, /Tomatsovs 00:59/);
  rows[0].children[3].children[0].onclick(); rows[0].children[3].children[1].onclick();
  assert.equal(bar.children[1].children.length, 1); assert.match(bar.children[0].textContent, /Tomatsovs/);
});
test('primary reset/removal falls back gracefully; finished selection remains eligible; selection is session-only', () => {
  const env = setup(), a = controls(env.render(process('a', 120))), b = controls(env.render(process('b', 1)));
  a.start.onclick(); b.start.onclick(); const bar = env.nodes.get('#recipe-timer-bar');
  bar.children[1].children[1].children[4].onclick(); env.elapse(1000);
  assert.match(bar.children[0].textContent, /Timeren Simrer er færdig!/);
  b.reset.onclick(); assert.equal(bar.children[1].children[0].children[4].attributes['aria-pressed'], 'true');
  assert.equal(bar.children[0].textContent, '⏱ Simrer · 01:59');
  b.start.onclick(); bar.children[1].children[1].children[4].onclick();
  vm.runInContext('cookingTimers.get("recipe").timers.delete("b"); updateCookingTimerBar(cookingTimers.get("recipe"));', env.context);
  assert.equal(bar.children[0].textContent, '⏱ Simrer · 01:59');
  b.reset.onclick(); a.reset.onclick(); assert.equal(bar.hidden, true);
  env.context.clearCookingTimers('recipe');
  const freshA = controls(env.render(process('a', 120))), freshB = controls(env.render(process('b', 60)));
  freshA.start.onclick(); freshB.start.onclick(); assert.equal(bar.children[0].textContent, '⏱ 2 timere · Simrer 02:00');
});
