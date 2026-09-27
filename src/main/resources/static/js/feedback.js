function createFeedback({document, request, showToast, isAdmin}) {
  const find = id => document.querySelector(`#${id}`);
  const dialog = find('feedback-dialog'), form = find('feedback-form');
  const list = find('feedback-admin-list'), message = find('feedback-admin-message');
  let submitting = false, epoch = 0, loadEpoch = 0;
  const types = {FEEDBACK: 'feedback.typeFeedback', BUG: 'feedback.typeBug'};
  const statuses = {OPEN: 'feedback.statusOpen', IN_PROGRESS: 'feedback.statusInProgress', DONE: 'feedback.statusDone'};
  function text(element, value) { element.textContent = value; element.hidden = !value; }
  function submissionBusy(value) {
    submitting = value;
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
  function render(items) {
    list.replaceChildren(...items.map(item => {
      const row = document.createElement('article'); row.className = 'feedback-card';
      const title = document.createElement('h3'); title.textContent = item.title;
      const type = document.createElement('p'); type.textContent = t('feedback.typeValue', {type: t(types[item.type])});
      const description = document.createElement('p'); description.className = 'feedback-description'; description.textContent = item.description;
      const author = document.createElement('small'); author.textContent = t('feedback.createdBy', {user: item.createdBy});
      const date = document.createElement('p'); date.textContent = t('feedback.createdAt', {date: new Intl.DateTimeFormat(currentLocale, {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(item.createdAt))});
      const label = document.createElement('label'); label.textContent = t('feedback.status');
      const select = document.createElement('select');
      Object.entries(statuses).forEach(([value, key]) => { const option = document.createElement('option'); option.value = value; option.textContent = t(key); select.append(option); });
      select.value = item.status; label.append(select);
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'secondary-button danger-button'; remove.textContent = t('feedback.delete');
      const error = document.createElement('p'); error.className = 'error-message'; error.setAttribute('role', 'alert'); error.hidden = true;
      async function mutate(method, body) {
        if (!isAdmin()) return;
        const token = epoch;
        select.disabled = remove.disabled = true; text(error, '');
        try {
          const result = await request(`/v1/admin/feedback/${item.id}`, {method, ...(body ? {headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)} : {})});
          if (token !== epoch) return;
          if (method === 'DELETE') { row.remove(); if (!list.children.length) text(message, t('feedback.empty')); }
          else { item.status = result.status; select.value = result.status; }
          showToast(t(method === 'DELETE' ? 'feedback.deleted' : 'feedback.updated'));
        } catch (failure) { if (token === epoch) { select.value = item.status; text(error, t('feedback.changeFailed')); } }
        finally { if (token === epoch) select.disabled = remove.disabled = false; }
      }
      select.addEventListener('change', () => mutate('PATCH', {status: select.value}));
      remove.addEventListener('click', () => mutate('DELETE'));
      row.append(title, type, description, author, date, label, remove, error);
      return row;
    }));
  }
  async function load() {
    if (!isAdmin()) return;
    const token = epoch, loading = ++loadEpoch;
    list.replaceChildren(); text(message, t('feedback.loading'));
    try {
      const items = await request('/v1/admin/feedback');
      if (token !== epoch || loading !== loadEpoch || !isAdmin()) return;
      render(items); text(message, items.length ? '' : t('feedback.empty'));
    } catch (error) { if (token === epoch && loading === loadEpoch) text(message, t('feedback.loadFailed')); }
  }
  find('reload-feedback').addEventListener('click', load);
  return {load, reset() { epoch++; loadEpoch++; dialog.close(); form.reset(); submissionBusy(false); list.replaceChildren(); text(message, ''); text(find('feedback-error'), ''); }};
}
if (typeof module !== 'undefined') module.exports = {createFeedback};
