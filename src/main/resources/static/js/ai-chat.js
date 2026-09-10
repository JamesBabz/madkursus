// CommonJS is used by the focused Node tests; browsers use the shared global helper.
/* In-memory chat UI. Transport is supplied by the application's session/CSRF helper. */
function createAiChat(root, request, openKnownRecipe = () => {}) {
  const t = typeof module !== "undefined" && module.exports ? require("./i18n.js").t : globalThis.t;
  const find = id => root.querySelector(`#chat-${id}`);
  const form = find('form'), input = find('input'), send = find('send');
  const log = find('messages'), empty = find('empty'), loading = find('loading'), error = find('error');
  const modelLabel = find('model');
  const maxExtra = find('max-extra');
  const examples = [...root.querySelectorAll('[data-chat-prompt]')];
  let busy = false;
  let generation = 0;
  let controller;
  let modelLoading = false;
  const isVisible = () => root.checkVisibility ? root.checkVisibility() : !root.hidden;

  async function refreshModel() {
    if (modelLoading) return;
    const currentGeneration = generation;
    modelLoading = true;
    modelLabel.textContent = t("chat.model.loading");
    try {
      const result = await request('/v1/ai/chat/model', { cache: 'no-store' });
      if (currentGeneration !== generation) return;
      if (typeof result?.model !== 'string' || !result.model.trim()) throw new Error('Missing model');
      modelLabel.textContent = t("chat.model.current", {model: result.model});
    } catch {
      if (currentGeneration === generation) modelLabel.textContent = t("chat.model.loadFailed");
    } finally {
      if (currentGeneration === generation) modelLoading = false;
    }
  }

  function scrollToLatest() {
    log.scrollTop = log.scrollHeight;
    if (isVisible()) log.lastElementChild?.scrollIntoView({ block: 'nearest' });
  }

  function setBusy(value) {
    busy = value;
    input.disabled = value;
    maxExtra.disabled = value;
    send.disabled = value || !input.value.trim();
    send.textContent = value ? t("chat.waiting") : t("chat.send");
    loading.hidden = !value;
    examples.forEach(button => { button.disabled = value; });
  }

  function append(role, text, recipes = []) {
    const message = root.ownerDocument.createElement('article');
    message.className = `chat-message chat-message-${role}`;
    const label = root.ownerDocument.createElement('strong');
    label.textContent = role === 'user' ? t("chat.userName") : t("chat.assistantName");
    const content = root.ownerDocument.createElement('p');
    content.textContent = text;
    message.append(label, content);
    for (const recipe of recipes) {
      if (!recipe || typeof recipe.id !== 'string' || typeof recipe.name !== 'string' || !['RECIPE', 'TEMPLATE'].includes(recipe.source)) continue;
      const card = root.ownerDocument.createElement('div');
      card.className = 'chat-recipe-card';
      const name = root.ownerDocument.createElement('strong'); name.textContent = recipe.name;
      const summary = root.ownerDocument.createElement('p');
      const missing = Array.isArray(recipe.missingIngredients) ? recipe.missingIngredients : [];
      const uncertain = Array.isArray(recipe.uncertainIngredients) ? recipe.uncertainIngredients : [];
      summary.textContent = missing.length ? t("chat.missingIngredients", {value1: missing.join(', ')}) : uncertain.length || recipe.state === 'CHECK_QUANTITIES' ? t("chat.quantityUnknown") : t("chat.stockSufficient");
      if (missing.length && uncertain.length) summary.textContent += ` ${t("chat.quantityUnknown")}`;
      const open = root.ownerDocument.createElement('button'); open.type = 'button'; open.className = 'text-button';
      open.textContent = t("recipes.open"); open.addEventListener('click', () => openKnownRecipe(recipe));
      card.append(name, summary, open); message.append(card);
    }
    log.append(message);
    empty.hidden = true;
    log.hidden = false;
    scrollToLatest();
  }

  async function submit(text = input.value) {
    const message = text.trim();
    if (busy || !message || message.length > 4000) return;
    const restoreFocus = root.ownerDocument.activeElement === input;
    const turn = generation;
    controller = new AbortController();
    error.hidden = true;
    error.textContent = '';
    append('user', message);
    input.value = '';
    setBusy(true);
    try {
      const result = await request('/v1/ai/chat', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, ...(maxExtra.value === '' ? {} : { maxAdditionalIngredients: Number(maxExtra.value) }) }), signal: controller.signal
      });
      if (turn !== generation) return;
      if (typeof result?.answer !== 'string' || !result.answer.trim()) throw new Error('Empty answer');
      const candidates = result.mealPlanProposal?.candidates ?? result.knownRecipes;
      append('assistant', result.answer, Array.isArray(candidates) ? candidates : []);
    } catch (failure) {
      if (turn !== generation) return;
      error.textContent = failure.status === 401 ? t("chat.sessionExpired")
        : failure.status === 403 ? t("chat.sendFailed")
        : failure.status === 503 ? t("chat.unavailable")
        : t("chat.requestFailed");
      error.hidden = false;
      input.value = message;
    } finally {
      if (turn === generation) {
        setBusy(false);
        scrollToLatest();
        if (restoreFocus && isVisible()) input.focus({ preventScroll: true });
      }
    }
  }

  form.addEventListener('submit', event => { event.preventDefault(); submit(); });
  input.addEventListener('input', () => { send.disabled = busy || !input.value.trim(); });
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      submit();
    }
  });
  examples.forEach(button => button.addEventListener('click', () => {
    if (busy) return;
    if (button.dataset.maxExtra !== undefined) maxExtra.value = button.dataset.maxExtra;
    submit(button.dataset.chatPrompt || button.textContent);
  }));
  setBusy(false);

  return {
    refreshModel,
    scrollToLatest,
    reset() {
      generation++;
      modelLoading = false;
      modelLabel.textContent = t("chat.model.loading");
      controller?.abort();
      log.replaceChildren();
      log.hidden = true;
      empty.hidden = false;
      input.value = '';
      maxExtra.value = '';
      error.textContent = '';
      error.hidden = true;
      setBusy(false);
    }
  };
}

// Move one live component; outside interactions remain available and are never cancelled.
function createAiChatLauncher(document, chat) {
  const launcher = document.querySelector('#chat-launcher');
  const drawer = document.querySelector('#chat-drawer');
  const minimize = document.querySelector('#chat-minimize');
  const component = document.querySelector('#chat-component');
  const page = document.querySelector('#ai-view');
  const host = document.querySelector('#chat-drawer-host');
  function close(restoreFocus = false) {
    drawer.hidden = true;
    page.append(component);
    launcher.setAttribute('aria-expanded', 'false');
    if (restoreFocus) launcher.focus({ preventScroll: true });
  }
  launcher.addEventListener('click', () => {
    if (!drawer.hidden) { close(true); return; }
    host.append(component);
    drawer.hidden = false;
    launcher.setAttribute('aria-expanded', 'true');
    minimize.focus({ preventScroll: true });
    chat.scrollToLatest();
    chat.refreshModel();
  });
  minimize.addEventListener('click', () => close(true));
  document.addEventListener('pointerdown', event => {
    if (!drawer.hidden && !drawer.contains(event.target) && !launcher.contains(event.target)) close();
  }, true);
  document.addEventListener('focusin', event => {
    if (!drawer.hidden && !drawer.contains(event.target) && !launcher.contains(event.target)) close();
  });
  document.addEventListener('keydown', event => {
    if (!drawer.hidden && event.key === 'Escape') { event.preventDefault(); close(true); }
  });
  return { close };
}

if (typeof module !== 'undefined') module.exports = { createAiChat, createAiChatLauncher };
