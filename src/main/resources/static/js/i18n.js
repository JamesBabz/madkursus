/* Frontend-only localization. Add a dictionary to locales/ and change currentLocale here. */
(function (root) {
  const currentLocale = 'da';
  const locales = typeof module !== 'undefined' && module.exports
    ? { da: require('./locales/da.js') } : root.MadkursusLocales;
  const warned = new Set();
  function t(key, values = {}) {
    let message = locales?.[currentLocale]?.[key];
    if (message && typeof message === 'object') {
      message = message[new Intl.PluralRules(currentLocale).select(Number(values.count))] || message.other;
    }
    if (typeof message !== 'string') {
      if (!warned.has(key)) { console.warn(`Missing translation: ${currentLocale}.${key}`); warned.add(key); }
      return `[${key}]`;
    }
    return message.replace(/\{(\w+)\}/g, (placeholder, name) =>
      Object.prototype.hasOwnProperty.call(values, name) ? String(values[name]) : placeholder);
  }
  // The import API has no stable code for this validation. Keep this exact compatibility
  // mapping here; never pattern-match or translate unknown technical exception messages.
  function localizeError(error) {
    const message = typeof error === 'string' ? error : error?.message;
    const known = {
      'Quantity must be a whole number': 'validation.quantityWholeNumber',
      'Quantity must be greater than zero': 'validation.quantityPositive',
      'Quantity must be zero or greater': 'validation.quantityNonnegative',
      'Piece quantity must use increments of 0.5': 'validation.quantityHalfSteps'
    };
    if (Object.prototype.hasOwnProperty.call(known, message)) return t(known[message]);
    return message || '';
  }
  function localizeHtml(scope) {
    const attributes = ['placeholder', 'title', 'aria-label', 'content', 'data-chat-prompt'];
    for (const element of scope.querySelectorAll('[data-i18n]')) element.textContent = t(element.dataset.i18n);
    for (const attribute of attributes) {
      for (const element of scope.querySelectorAll(`[data-i18n-${attribute}]`)) {
        element.setAttribute(attribute, t(element.getAttribute(`data-i18n-${attribute}`)));
      }
    }
    if (scope.documentElement) scope.documentElement.lang = currentLocale;
  }
  // Preserve HTML constraints and submission behavior, translating only their feedback.
  function localizeFormValidation(document) {
    const localized = new WeakSet();
    document.addEventListener('invalid', event => {
      const input = event.target;
      if (localized.has(input)) input.setCustomValidity('');
      const validity = input.validity;
      const rule = ['valueMissing', 'typeMismatch', 'badInput', 'rangeUnderflow',
        'rangeOverflow', 'stepMismatch', 'tooShort', 'tooLong', 'patternMismatch'].find(key => validity[key]);
      if (!rule) return;
      input.setCustomValidity(t('validation.' + rule, {
        min: input.min, max: input.max, step: input.step, minLength: input.minLength, maxLength: input.maxLength
      }));
      localized.add(input);
    }, true);
    const clear = event => {
      if (localized.has(event.target)) { event.target.setCustomValidity(''); localized.delete(event.target); }
    };
    document.addEventListener('input', clear, true);
    document.addEventListener('change', clear, true);
    const clearWithin = event => event.target.querySelectorAll('input, select, textarea').forEach(target => clear({target}));
    document.addEventListener('reset', clearWithin, true);
    document.addEventListener('close', clearWithin, true);
  }
  const api = { t, localizeError, localizeHtml, currentLocale };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else { Object.assign(root, api); localizeHtml(root.document); localizeFormValidation(root.document); }
})(globalThis);
