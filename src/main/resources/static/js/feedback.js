function createFeedback({document, request, showToast, isAdmin}) {
  const find = id => document.querySelector(`#${id}`);
  const dialog = find('feedback-dialog'), form = find('feedback-form');
  const list = find('feedback-admin-list'), message = find('feedback-admin-message');
  const sort = find('feedback-sort');
  let submitting = false, epoch = 0, loadEpoch = 0;
  let entries = [];
  const types = {FEEDBACK: 'feedback.typeFeedback', BUG: 'feedback.typeBug'};
  const statuses = {OPEN: 'feedback.statusOpen', IN_PROGRESS: 'feedback.statusInProgress', DONE: 'feedback.statusDone'};
  function text(element, value) { element.textContent = value; element.hidden = !value; }
  function submissionBusy(value) {
    submitting = value;
    form.setAttribute('aria-busy', String(value));
    form.querySelectorAll('input, select, textarea, button').forEach(control => control.disabled = value);
  }
  function close() { if (!submitting) dialog.close(); }
  find('more-send-feedback').addEventListener('click', () => {
    form.reset(); text(find('feedback-error'), ''); dialog.showModal();
  });
  find('close-feedback').addEventListener('click', close);
  find('cancel-feedback').addEventListener('click', close);
  dialog.addEventListener('cancel', event => { if (submitting) event.preventDefault(); });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submitting) return;
    const payload = {type: find('feedback-type').value, title: find('feedback-title').value.trim(), description: find('feedback-description').value.trim()};
    if (!payload.title || !payload.description) { text(find('feedback-error'), t('feedback.required')); return; }
    const token = epoch;
    submissionBusy(true); text(find('feedback-error'), '');
    try {
      await request('/v1/feedback', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)});
      if (token !== epoch) return;
      dialog.close(); form.reset(); showToast(t('feedback.sent'));
    } catch (error) {
      if (token === epoch) text(find('feedback-error'), t('feedback.sendFailed'));
    } finally { if (token === epoch) submissionBusy(false); }
  });
  function reorder() {
    const userOrder = new Intl.Collator(currentLocale, {sensitivity: 'base', numeric: true});
    const statusOrder = {IN_PROGRESS: 0, OPEN: 1, DONE: 2};
    const newest = (a, b) => Date.parse(b.item.createdAt) - Date.parse(a.item.createdAt) || a.item.id.localeCompare(b.item.id);
    const ordered = [...entries].sort((a, b) => {
      if (sort.value === 'oldest') return -newest(a, b);
      if (sort.value === 'user-asc' || sort.value === 'user-desc') {
        const byUser = userOrder.compare(a.item.createdByUsername, b.item.createdByUsername);
        return (sort.value === 'user-desc' ? -byUser : byUser) || newest(a, b);
      }
      if (sort.value === 'status') return (statusOrder[a.item.status] ?? 3) - (statusOrder[b.item.status] ?? 3) || newest(a, b);
      return newest(a, b);
    });
    // Move the existing rows: selection, confirmations and pending actions keep their identity.
    const focused = document.activeElement, left = window.scrollX, top = window.scrollY;
    list.replaceChildren(...ordered.map(entry => entry.row));
    if (list.contains(focused)) focused.focus({preventScroll: true});
    window.scrollTo(left, top);
  }
  sort.addEventListener('change', reorder);
  function render(items) {
    entries = items.map(item => {
      const row = document.createElement('article'); row.className = 'feedback-card';
      row.dataset.feedbackId = item.id;
      const title = document.createElement('h3'); title.textContent = item.title;
      title.id = `feedback-title-${item.id}`; row.setAttribute('aria-labelledby', title.id);
      const heading = document.createElement('div'); heading.className = 'feedback-heading';
      const type = document.createElement('span'); type.className = `status-badge feedback-type ${item.type === 'BUG' ? 'bug' : ''}`; type.textContent = t(types[item.type]);
      heading.append(type, title);
      const description = document.createElement('details'); description.className = 'feedback-description';
      const summary = document.createElement('summary'); summary.textContent = t('feedback.description');
      const content = document.createElement('p'); content.textContent = item.description; description.append(summary, content);
      const metadata = document.createElement('div'); metadata.className = 'feedback-metadata';
      const author = document.createElement('small'); author.textContent = t('feedback.createdBy', {user: item.createdByUsername});
      const date = document.createElement('time'); date.dateTime = item.createdAt; date.textContent = t('feedback.createdAt', {date: new Intl.DateTimeFormat(currentLocale, {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(item.createdAt))});
      metadata.append(author, date);
      const actions = document.createElement('div'); actions.className = 'feedback-actions';
      const label = document.createElement('label'); const statusLabel = document.createElement('span'); statusLabel.textContent = t('feedback.status'); label.append(statusLabel);
      const select = document.createElement('select');
      Object.entries(statuses).forEach(([value, key]) => { const option = document.createElement('option'); option.value = value; option.textContent = t(key); select.append(option); });
      select.value = item.status; label.append(select);
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'text-button feedback-delete'; remove.textContent = t('feedback.delete');
      actions.append(label, remove);
      const confirmation = document.createElement('section'); confirmation.className = 'inline-confirmation'; confirmation.hidden = true;
      confirmation.setAttribute('aria-live', 'polite');
      const question = document.createElement('h3'); question.textContent = t('feedback.confirmDeletion');
      const hint = document.createElement('p'); hint.textContent = t('feedback.deletionHint');
      const confirmActions = document.createElement('div'); confirmActions.className = 'dialog-actions';
      const keep = document.createElement('button'); keep.type = 'button'; keep.className = 'secondary-button'; keep.textContent = t('common.cancel');
      const confirm = document.createElement('button'); confirm.type = 'button'; confirm.className = 'danger-button'; confirm.textContent = t('feedback.delete');
      confirmActions.append(keep, confirm); confirmation.append(question, hint, confirmActions);
      const error = document.createElement('p'); error.className = 'notice error'; error.setAttribute('role', 'alert'); error.hidden = true;
      async function mutate(method, body) {
        if (!isAdmin() || select.disabled) return;
        const token = epoch;
        select.disabled = remove.disabled = keep.disabled = confirm.disabled = true; row.setAttribute('aria-busy', 'true'); text(error, '');
        try {
          const result = await request(`/v1/admin/feedback/${item.id}`, {method, ...(body ? {headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)} : {})});
          if (token !== epoch) return;
          if (method === 'DELETE') { entries = entries.filter(entry => entry.item.id !== item.id); reorder(); if (!entries.length) text(message, t('feedback.empty')); }
          else { item.status = result.status; select.value = result.status; if (sort.value === 'status') reorder(); }
          showToast(t(method === 'DELETE' ? 'feedback.deleted' : 'feedback.updated'));
        } catch (failure) { if (token === epoch) { select.value = item.status; text(error, t('feedback.changeFailed')); } }
        finally { if (token === epoch) { select.disabled = remove.disabled = keep.disabled = confirm.disabled = false; row.setAttribute('aria-busy', 'false'); } }
      }
      select.addEventListener('change', () => mutate('PATCH', {status: select.value}));
      remove.addEventListener('click', () => { confirmation.hidden = false; keep.focus(); });
      keep.addEventListener('click', () => { confirmation.hidden = true; remove.focus({preventScroll: true}); });
      confirm.addEventListener('click', () => mutate('DELETE'));
      row.append(heading, metadata, description, actions, confirmation, error);
      return {item, row};
    });
    reorder();
  }
  async function load() {
    if (!isAdmin()) return;
    const token = epoch, loading = ++loadEpoch;
    entries = []; list.replaceChildren(); list.setAttribute('aria-busy', 'true'); text(message, t('feedback.loading')); message.classList.remove('error');
    try {
      const items = await request('/v1/admin/feedback');
      if (token !== epoch || loading !== loadEpoch || !isAdmin()) return;
      render(items); text(message, items.length ? '' : t('feedback.empty'));
    } catch (error) { if (token === epoch && loading === loadEpoch) { text(message, t('feedback.loadFailed')); message.classList.add('error'); } }
    finally { if (token === epoch && loading === loadEpoch) list.setAttribute('aria-busy', 'false'); }
  }
  find('reload-feedback').addEventListener('click', load);
  return {load, reset() { epoch++; loadEpoch++; entries = []; sort.value = 'newest'; dialog.close(); form.reset(); submissionBusy(false); list.replaceChildren(); list.setAttribute('aria-busy', 'false'); message.classList.remove('error'); text(message, ''); text(find('feedback-error'), ''); }};
}
if (typeof module !== 'undefined') module.exports = {createFeedback};
