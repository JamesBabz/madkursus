const PRODUCT_API = '/v1/products';
const AUTH_API = '/v1/auth';

const categoryLabels = {
  MEAT: 'Kød', VEGETABLE: 'Grøntsag', FRUIT: 'Frugt', DAIRY: 'Mejeri',
  DRY_GOODS: 'Tørvarer', SPICE: 'Krydderi', OTHER: 'Andet'
};
const unitLabels = { GRAM: 'Gram', MILLILITER: 'Milliliter', PIECE: 'Styk' };
const categoryIcons = {
  MEAT: '🍗', VEGETABLE: '🥕', FRUIT: '🍎', DAIRY: '🥛',
  DRY_GOODS: '🌾', SPICE: '🌿', OTHER: '🍽️'
};

const authScreen = document.querySelector('#auth-screen');
const application = document.querySelector('#application');
const loginForm = document.querySelector('#login-form');
const registerForm = document.querySelector('#register-form');
const authError = document.querySelector('#auth-error');
const authSuccess = document.querySelector('#auth-success');
const formPanel = document.querySelector('#product-form-panel');
const productForm = document.querySelector('#product-form');
const openFormButton = document.querySelector('#open-form');
const saveButton = document.querySelector('#save-product');
const list = document.querySelector('#product-list');
const loading = document.querySelector('#loading-message');
const emptyState = document.querySelector('#empty-state');
const errorMessage = document.querySelector('#error-message');
const successMessage = document.querySelector('#success-message');

let csrfToken = '';
let registrationEnabled = false;

function showMessage(element, message) {
  element.textContent = message;
  element.hidden = !message;
}

function apiError(response, body) {
  const detail = body?.message || body?.errors?.[0];
  return new Error(detail || `Serveren svarede med status ${response.status}.`);
}

async function jsonRequest(url, options = {}) {
  const headers = { Accept: 'application/json', ...options.headers };
  if (options.method && options.method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken;
  const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw apiError(response, body);
  return body;
}

async function refreshCsrfToken() {
  const body = await jsonRequest(`${AUTH_API}/csrf`);
  csrfToken = body.token;
}

function showLogin() {
  loginForm.hidden = false;
  registerForm.hidden = !registrationEnabled;
  showMessage(authError, '');
  showMessage(authSuccess, '');
}

function showAuthenticatedApp() {
  authScreen.hidden = true;
  application.hidden = false;
  loadProducts();
}

function showUnauthenticatedApp() {
  application.hidden = true;
  authScreen.hidden = false;
  list.replaceChildren();
  showLogin();
}

async function initialize() {
  try {
    const status = await jsonRequest(`${AUTH_API}/registration-status`);
    registrationEnabled = status.enabled;
    await refreshCsrfToken();
    await jsonRequest(`${AUTH_API}/me`);
    showAuthenticatedApp();
  } catch (error) {
    showUnauthenticatedApp();
  }
}

async function authenticate(event) {
  event.preventDefault();
  showMessage(authError, '');
  showMessage(authSuccess, '');
  const button = loginForm.querySelector('button[type="submit"]');
  button.disabled = true;
  const data = new FormData(loginForm);
  try {
    await jsonRequest(`${AUTH_API}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: data.get('username').trim(), password: data.get('password') })
    });
    loginForm.reset();
    await refreshCsrfToken();
    showAuthenticatedApp();
  } catch (error) {
    showMessage(authError, `Login mislykkedes. ${error.message}`);
  } finally {
    button.disabled = false;
  }
}

async function register(event) {
  event.preventDefault();
  showMessage(authError, '');
  showMessage(authSuccess, '');
  const data = new FormData(registerForm);
  if (data.get('password') !== data.get('passwordConfirmation')) {
    showMessage(authError, 'Adgangskoderne er ikke ens.');
    return;
  }
  const button = registerForm.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    await jsonRequest(`${AUTH_API}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: data.get('username').trim(), password: data.get('password') })
    });
    registerForm.reset();
    showMessage(authSuccess, 'Kontoen er oprettet. Du kan nu logge ind.');
    document.querySelector('#login-username').value = data.get('username').trim();
    document.querySelector('#login-password').focus();
  } catch (error) {
    showMessage(authError, `Kontoen kunne ikke oprettes. ${error.message}`);
  } finally {
    button.disabled = false;
  }
}

async function logout() {
  try { await jsonRequest(`${AUTH_API}/logout`, { method: 'POST' }); } finally {
    csrfToken = '';
    await refreshCsrfToken();
    showUnauthenticatedApp();
  }
}

function setFormOpen(open) {
  formPanel.hidden = !open;
  openFormButton.setAttribute('aria-expanded', String(open));
  if (open) document.querySelector('#name').focus();
}

function createProductCard(product) {
  const card = document.createElement('article');
  card.className = 'product-card';
  const icon = document.createElement('span');
  icon.className = 'product-icon';
  icon.setAttribute('aria-hidden', 'true');
  icon.textContent = categoryIcons[product.category] || categoryIcons.OTHER;
  const content = document.createElement('div');
  const name = document.createElement('h3');
  name.className = 'product-name';
  name.textContent = product.name;
  const meta = document.createElement('p');
  meta.className = 'product-meta';
  meta.textContent = `${categoryLabels[product.category] || product.category} · ${unitLabels[product.defaultUnit] || product.defaultUnit}`;
  content.append(name, meta);
  card.append(icon, content);
  return card;
}

async function loadProducts() {
  loading.hidden = false;
  emptyState.hidden = true;
  showMessage(errorMessage, '');
  try {
    const products = await jsonRequest(PRODUCT_API);
    list.replaceChildren(...products.map(createProductCard));
    emptyState.hidden = products.length !== 0;
  } catch (error) {
    list.replaceChildren();
    showMessage(errorMessage, `Produkterne kunne ikke hentes. ${error.message}`);
  } finally {
    loading.hidden = true;
  }
}

async function createProduct(event) {
  event.preventDefault();
  showMessage(errorMessage, '');
  showMessage(successMessage, '');
  saveButton.disabled = true;
  saveButton.textContent = 'Gemmer…';
  const data = new FormData(productForm);
  const payload = {
    name: data.get('name').trim(), category: data.get('category'), defaultUnit: data.get('defaultUnit')
  };
  try {
    const created = await jsonRequest(PRODUCT_API, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    });
    productForm.reset();
    setFormOpen(false);
    await loadProducts();
    showMessage(successMessage, `${created.name} blev tilføjet.`);
  } catch (error) {
    showMessage(errorMessage, `Produktet kunne ikke gemmes. ${error.message}`);
  } finally {
    saveButton.disabled = false;
    saveButton.textContent = 'Gem produkt';
  }
}

loginForm.addEventListener('submit', authenticate);
registerForm.addEventListener('submit', register);
document.querySelector('#logout').addEventListener('click', logout);
openFormButton.addEventListener('click', () => setFormOpen(formPanel.hidden));
document.querySelector('#close-form').addEventListener('click', () => setFormOpen(false));
document.querySelector('#empty-add').addEventListener('click', () => setFormOpen(true));
document.querySelector('#refresh-products').addEventListener('click', loadProducts);
productForm.addEventListener('submit', createProduct);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js')
    .catch(() => showMessage(authError, 'Appen virker, men offline-understøttelse kunne ikke startes.')));
}

initialize();
