const PRODUCT_API = '/v1/products';
const AUTH_API = '/v1/auth';
const INVENTORY_API = '/v1/inventory';
const SHOPPING_API = '/v1/shopping-list';
const RECIPE_API = '/v1/recipes';

const categoryLabels = {
  BAKING:'Bagning', BREAD:'Brød', DAIRY:'Mejeri', EGG:'Æg', FISH:'Fisk', FROZEN:'Frostvarer',
  FRUIT:'Frugt', GRAIN_PASTA:'Korn, ris og pasta', HERB:'Urter', LEGUME:'Bælgfrugter', MEAT:'Kød',
  NUT_SEED:'Nødder og frø', OIL_FAT:'Olie og fedt', OTHER:'Andet', PRESERVED:'Konserves',
  SAUCE_CONDIMENT:'Saucer og tilbehør', SPICE:'Krydderier', STOCK:'Fond og bouillon',
  SWEETENER:'Sødemidler', VEGETABLE:'Grøntsager', VINEGAR_ACID:'Eddike og syre', DRY_GOODS:'Tørvarer'
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
const templateSearch = document.querySelector('#template-search');
const templateResults = document.querySelector('#template-results');
const toast = document.querySelector('#toast');
const editDialog = document.querySelector('#edit-product-dialog');
const editForm = document.querySelector('#edit-product-form');
const editError = document.querySelector('#edit-product-error');
let searchTimer;
let searchRequestId = 0;
let toastTimer;
let currentProducts = [];
let inventorySearchTimer;
let inventorySearchRequestId = 0;
let selectedInventoryCandidate = null;
let shoppingSearchTimer;
let shoppingSearchRequestId = 0;
let selectedShoppingCandidate = null;
let currentRecipes = [];
let currentRecipe = null;
let recipePortions = 2;
let editingRecipeId = null;
let recipeIngredients = [];
let recipeSteps = [];
let selectedRecipeTemplate = null;
let recipeSearchTimer;
let recipeSearchRequestId = 0;

let csrfToken = '';
let registrationEnabled = false;

function showMessage(element, message) {
  element.textContent = message;
  element.hidden = !message;
}

function showToast(message, variant = 'success', action = null) {
  clearTimeout(toastTimer);
  document.querySelector('#toast-message').textContent = `${variant === 'success' ? '✓ ' : ''}${message}`;
  const actionButton = document.querySelector('#toast-action');
  actionButton.hidden = !action;
  actionButton.textContent = action?.label || '';
  actionButton.onclick = action ? async () => {
    clearTimeout(toastTimer); toast.hidden = true; actionButton.disabled = true;
    try { await action.run(); } finally { actionButton.disabled = false; }
  } : null;
  toast.classList.toggle('error-toast', variant === 'error');
  toast.setAttribute('role', variant === 'error' ? 'alert' : 'status');
  toast.hidden = false;
  toastTimer = setTimeout(() => { toast.hidden = true; }, action ? 5000 : 2600);
}

function apiError(response, body) {
  const detail = body?.message || body?.errors?.[0];
  const error = new Error(detail || `Serveren svarede med status ${response.status}.`);
  error.status = response.status;
  return error;
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
  showView('inventory');
  loadProducts();
}

function showUnauthenticatedApp() {
  application.hidden = true;
  authScreen.hidden = false;
  list.replaceChildren();
  document.querySelector('#inventory-list').replaceChildren();
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
  clearTimeout(searchTimer);
  searchRequestId++;
  templateSearch.value = '';
  templateResults.replaceChildren();
  productForm.hidden = true;
  productForm.reset();
  if (open) { templateSearch.focus(); searchTemplates(''); }
}

function templateRow(template) {
  const row = document.createElement('div'); row.className = 'template-row';
  const text = document.createElement('div');
  const name = document.createElement('p'); name.textContent = template.name;
  const meta = document.createElement('small'); meta.textContent = `${categoryLabels[template.category]} · ${unitLabels[template.defaultUnit]}`;
  const add = document.createElement('button'); add.type = 'button'; add.className = 'template-add'; add.textContent = 'Tilføj';
  add.addEventListener('click', () => addTemplate(template.id, add));
  text.append(name, meta); row.append(text, add); return row;
}

async function searchTemplates(search) {
  const requestId = ++searchRequestId;
  try {
    const templates = await jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`);
    if (requestId !== searchRequestId || templateSearch.value !== search) return;
    templateResults.replaceChildren(...templates.slice(0, 12).map(templateRow));
  } catch (error) { if (requestId === searchRequestId) showToast(`Kataloget kunne ikke hentes. ${error.message}`, 'error'); }
}

async function addTemplate(id, button) {
  button.disabled = true;
  try {
    const created = await jsonRequest(`/v1/products/from-template/${id}`, { method: 'POST' });
    await loadProducts(); showToast(`${created.name} er tilføjet`);
  } catch (error) { showToast(error.message, 'error'); }
  finally { button.disabled = false; }
}

function createProductCard(product) {
  const card = document.createElement('article');
  card.className = 'product-card';
  card.tabIndex = 0;
  card.setAttribute('role', 'button');
  card.setAttribute('aria-label', `Rediger ${product.name}`);
  card.addEventListener('click', () => openProductEditor(product));
  card.addEventListener('keydown', event => {
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openProductEditor(product); }
  });
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

function openProductEditor(product) {
  showMessage(editError, '');
  document.querySelector('#edit-product-id').value = product.id;
  document.querySelector('#edit-product-name').value = product.name;
  document.querySelector('#edit-product-category').value = product.category;
  document.querySelector('#edit-product-unit').value = product.defaultUnit;
  document.querySelector('#edit-product-tracking-mode').value = product.inventoryTrackingMode || 'QUANTITY';
  document.querySelector('#delete-product-confirmation').hidden = true;
  document.querySelector('#request-delete-product').hidden = false;
  editDialog.showModal();
  document.querySelector('#edit-product-name').focus();
}

function closeProductEditor() {
  if (editDialog.open) editDialog.close();
  editForm.reset();
  showMessage(editError, '');
  document.querySelector('#delete-product-confirmation').hidden = true;
}

async function updateProduct(event) {
  event.preventDefault();
  showMessage(editError, '');
  const data = new FormData(editForm);
  const button = document.querySelector('#save-edit-product');
  button.disabled = true;
  try {
    const updated = await jsonRequest(`${PRODUCT_API}/${data.get('id')}`, {
      method: 'PATCH', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: data.get('name').trim(), category: data.get('category'),
        defaultUnit: data.get('defaultUnit'), inventoryTrackingMode: data.get('inventoryTrackingMode') })
    });
    closeProductEditor();
    await loadProducts();
    showToast(`${updated.name} er gemt`);
  } catch (error) { showMessage(editError, `Produktet kunne ikke gemmes. ${error.message}`); }
  finally { button.disabled = false; }
}

async function loadProducts() {
  loading.hidden = false;
  emptyState.hidden = true;
  showMessage(errorMessage, '');
  try {
    const products = await jsonRequest(PRODUCT_API);
    currentProducts = products;
    list.replaceChildren(...products.map(createProductCard));
    emptyState.hidden = products.length !== 0;
  } catch (error) {
    list.replaceChildren();
    showMessage(errorMessage, `Produkterne kunne ikke hentes. ${error.message}`);
  } finally {
    loading.hidden = true;
  }
}

function showView(view) {
  const inventoryActive = view === 'inventory';
  const shoppingActive = view === 'shopping';
  const productsActive = view === 'products';
  const recipesActive = view === 'recipes';
  document.querySelector('#products-view').hidden = !productsActive;
  document.querySelector('#inventory-view').hidden = !inventoryActive;
  document.querySelector('#shopping-view').hidden = !shoppingActive;
  document.querySelector('#recipes-view').hidden = !recipesActive;
  document.querySelector('#show-products').classList.toggle('active', productsActive);
  document.querySelector('#show-inventory').classList.toggle('active', inventoryActive);
  document.querySelector('#show-shopping').classList.toggle('active', shoppingActive);
  document.querySelector('#show-recipes').classList.toggle('active', recipesActive);
  openFormButton.hidden = !productsActive;
  if (inventoryActive) { setFormOpen(false); loadInventory(); }
  if (shoppingActive) { setFormOpen(false); loadShoppingList(); }
  if (recipesActive) { setFormOpen(false); loadRecipes(); }
}

function displayUnit(unit) {
  return { GRAM: 'g', MILLILITER: 'ml', PIECE: 'stk' }[unit] || unit;
}

function formatQuantity(quantity) {
  return new Intl.NumberFormat('da-DK', { maximumFractionDigits: 1 }).format(Number(quantity));
}

function numericValue(input) { return Number(String(input.value).replace(',', '.')); }

function configureQuantityInput(input, unit, allowZero = false) {
  input.step = unit === 'PIECE' ? '0.5' : '1';
  input.min = allowZero ? '0' : (unit === 'PIECE' ? '0.5' : '1');
  input.inputMode = unit === 'PIECE' ? 'decimal' : 'numeric';
  input.dataset.unit = unit;
}

function candidateTrackingMode(candidate) {
  return candidate.source === 'template' ? candidate.defaultTrackingMode : candidate.inventoryTrackingMode;
}

function inventoryConversion(quantity, unit) {
  const value = Number(quantity);
  if (!Number.isInteger(value) || value < 1000 || (unit !== 'GRAM' && unit !== 'MILLILITER')) return '';
  const converted = new Intl.NumberFormat('da-DK', { maximumFractionDigits: 3 }).format(value / 1000);
  return `= ${converted} ${unit === 'GRAM' ? 'kg' : 'liter'}`;
}

function updateConversion(input, unit, output) {
  output.textContent = inventoryConversion(input.value, unit);
}

function createInventoryCard(item) {
  const card = document.createElement('article');
  card.className = 'product-card inventory-card'; card.tabIndex = 0; card.setAttribute('role', 'button');
  card.setAttribute('aria-label', `Rediger lagerbeholdning for ${item.product.name}`);
  const open = () => openInventoryEditor(item);
  card.addEventListener('click', open);
  card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); } });
  const content = document.createElement('div');
  const name = document.createElement('h3'); name.className = 'product-name'; name.textContent = item.product.name;
  const amount = document.createElement('p'); amount.className = 'product-meta inventory-amount';
  amount.textContent = item.product.inventoryTrackingMode === 'PRESENCE' ? 'På lager'
    : `${formatQuantity(item.quantity)} ${displayUnit(item.unit)}`;
  content.append(name, amount); card.append(content); return card;
}

async function loadInventory() {
  const loadingElement = document.querySelector('#inventory-loading');
  const empty = document.querySelector('#inventory-empty');
  loadingElement.hidden = false; empty.hidden = true;
  try {
    const items = await jsonRequest(INVENTORY_API);
    document.querySelector('#inventory-list').replaceChildren(...items.map(createInventoryCard));
    empty.hidden = items.length !== 0;
  } catch (error) {
    document.querySelector('#inventory-list').replaceChildren();
    showToast(`Lageret kunne ikke hentes. ${error.message}`, 'error');
  } finally { loadingElement.hidden = true; }
}

function normalizeName(name) { return name.trim().toLocaleLowerCase('da-DK'); }

function inventoryCandidateRow(candidate) {
  const row = document.createElement('button'); row.type = 'button'; row.className = 'template-row inventory-result';
  const text = document.createElement('span');
  const name = document.createElement('strong'); name.textContent = candidate.name;
  const meta = document.createElement('small'); meta.textContent = `${categoryLabels[candidate.category] || candidate.category} · ${displayUnit(candidate.defaultUnit)}`;
  text.append(name, meta); row.append(text); row.addEventListener('click', () => selectInventoryCandidate(candidate)); return row;
}

async function searchInventoryCandidates(search) {
  const requestId = ++inventorySearchRequestId;
  try {
    const [products, templates] = await Promise.all([
      jsonRequest(PRODUCT_API),
      jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`)
    ]);
    const input = document.querySelector('#inventory-search');
    if (requestId !== inventorySearchRequestId || input.value !== search) return;
    currentProducts = products;
    const query = normalizeName(search);
    const matchingProducts = products.filter(product => !query || normalizeName(product.name).includes(query))
      .map(product => ({ ...product, source: 'product' }));
    const ownedNames = new Set(products.map(product => normalizeName(product.name)));
    const catalog = templates.filter(template => !ownedNames.has(normalizeName(template.name)))
      .map(template => ({ ...template, source: 'template' }));
    document.querySelector('#inventory-search-results').replaceChildren(
      ...[...matchingProducts, ...catalog].slice(0, 20).map(inventoryCandidateRow)
    );
  } catch (error) { if (requestId === inventorySearchRequestId) showToast(`Søgningen mislykkedes. ${error.message}`, 'error'); }
}

function resetInventoryAdd() {
  clearTimeout(inventorySearchTimer); inventorySearchRequestId++; selectedInventoryCandidate = null;
  document.querySelector('#inventory-search').value = '';
  document.querySelector('#inventory-search-results').replaceChildren();
  document.querySelector('#inventory-search-step').hidden = false;
  document.querySelector('#inventory-amount-form').hidden = true;
  document.querySelector('#inventory-amount-form').reset();
  document.querySelector('#inventory-add-quantity-controls').hidden = false;
  document.querySelector('#inventory-add-quantity').required = true;
  document.querySelector('#inventory-add-conversion').textContent = '';
  showMessage(document.querySelector('#inventory-add-error'), '');
}

function openInventoryAdd() {
  resetInventoryAdd(); document.querySelector('#inventory-add-dialog').showModal();
  document.querySelector('#inventory-search').focus(); searchInventoryCandidates('');
}

function closeInventoryAdd() { const dialog = document.querySelector('#inventory-add-dialog'); if (dialog.open) dialog.close(); resetInventoryAdd(); }

function selectInventoryCandidate(candidate) {
  selectedInventoryCandidate = candidate;
  const presence = candidateTrackingMode(candidate) === 'PRESENCE';
  document.querySelector('#inventory-search-step').hidden = true;
  document.querySelector('#inventory-amount-form').hidden = false;
  document.querySelector('#inventory-selected-name').textContent = candidate.name;
  document.querySelector('#inventory-selected-unit').textContent = displayUnit(candidate.defaultUnit);
  document.querySelector('#inventory-add-quantity-controls').hidden = presence;
  const quantityInput = document.querySelector('#inventory-add-quantity');
  quantityInput.required = !presence;
  if (presence) { document.querySelector('#inventory-selected-unit').textContent = ''; return; }
  configureQuantityInput(quantityInput, candidate.defaultUnit);
  updateConversion(quantityInput, candidate.defaultUnit, document.querySelector('#inventory-add-conversion'));
  quantityInput.focus();
}

async function addInventory(event) {
  event.preventDefault(); if (!selectedInventoryCandidate) return;
  const quantity = candidateTrackingMode(selectedInventoryCandidate) === 'PRESENCE'
    ? null : numericValue(document.querySelector('#inventory-add-quantity'));
  await submitInventoryCandidate(quantity);
}

async function submitInventoryCandidate(quantity) {
  const url = selectedInventoryCandidate.source === 'product' ? INVENTORY_API : `${INVENTORY_API}/from-template/${selectedInventoryCandidate.id}`;
  const payload = selectedInventoryCandidate.source === 'product'
    ? { productId: selectedInventoryCandidate.id, ...(quantity == null ? {} : { quantity }) }
    : (quantity == null ? {} : { quantity });
  try {
    await jsonRequest(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    const name = selectedInventoryCandidate.name; closeInventoryAdd(); await Promise.all([loadInventory(), loadProducts()]);
    showToast(`${name} er tilføjet til lageret`);
  } catch (error) { showMessage(document.querySelector('#inventory-add-error'), error.message); }
}

function openInventoryEditor(item) {
  document.querySelector('#edit-inventory-id').value = item.id;
  document.querySelector('#edit-inventory-name').textContent = item.product.name;
  const presence = item.product.inventoryTrackingMode === 'PRESENCE';
  document.querySelector('#edit-inventory-quantity-controls').hidden = presence;
  document.querySelector('#edit-inventory-presence').hidden = !presence;
  document.querySelector('#edit-inventory-form').querySelector('button[type="submit"]').hidden = presence;
  document.querySelector('#edit-inventory-quantity').required = !presence;
  document.querySelector('#edit-inventory-quantity').value = item.quantity ?? '';
  document.querySelector('#edit-inventory-unit').textContent = displayUnit(item.unit);
  configureQuantityInput(document.querySelector('#edit-inventory-quantity'), item.unit, true);
  updateConversion(document.querySelector('#edit-inventory-quantity'), item.unit,
    document.querySelector('#edit-inventory-conversion'));
  document.querySelector('#edit-inventory-dialog').showModal(); document.querySelector('#edit-inventory-quantity').focus();
}

function closeInventoryEditor() { const dialog = document.querySelector('#edit-inventory-dialog'); if (dialog.open) dialog.close(); showMessage(document.querySelector('#edit-inventory-error'), ''); }

async function saveInventory(event) {
  event.preventDefault(); const id = document.querySelector('#edit-inventory-id').value;
  try {
    await jsonRequest(`${INVENTORY_API}/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity: numericValue(document.querySelector('#edit-inventory-quantity')) }) });
    closeInventoryEditor(); await loadInventory(); showToast('Lagerbeholdningen er gemt');
  } catch (error) { showMessage(document.querySelector('#edit-inventory-error'), error.message); }
}

async function deleteInventory() {
  const id = document.querySelector('#edit-inventory-id').value;
  try { await jsonRequest(`${INVENTORY_API}/${id}`, { method: 'DELETE' }); closeInventoryEditor(); await loadInventory(); showToast('Varen er fjernet fra lageret'); }
  catch (error) { showMessage(document.querySelector('#edit-inventory-error'), error.message); }
}

async function createProduct(event) {
  event.preventDefault();
  showMessage(errorMessage, '');
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
    showToast(`${created.name} er tilføjet`);
  } catch (error) {
    showMessage(errorMessage, `Produktet kunne ikke gemmes. ${error.message}`);
  } finally {
    saveButton.disabled = false;
    saveButton.textContent = 'Gem produkt';
  }
}

function requestProductDeletion() {
  const name = document.querySelector('#edit-product-name').value.trim();
  document.querySelector('#delete-product-question').textContent = `Er du sikker på, at du vil fjerne "${name}"?`;
  document.querySelector('#request-delete-product').hidden = true;
  document.querySelector('#delete-product-confirmation').hidden = false;
  document.querySelector('#keep-product').focus();
}

function cancelProductDeletion() {
  document.querySelector('#delete-product-confirmation').hidden = true;
  document.querySelector('#request-delete-product').hidden = false;
  document.querySelector('#request-delete-product').focus();
}

async function deleteProduct() {
  const id = document.querySelector('#edit-product-id').value;
  const name = document.querySelector('#edit-product-name').value.trim();
  const button = document.querySelector('#confirm-delete-product');
  button.disabled = true; showMessage(editError, '');
  try {
    await jsonRequest(`${PRODUCT_API}/${id}`, { method: 'DELETE' });
    closeProductEditor(); await loadProducts(); showToast(`${name} er fjernet`);
  } catch (error) {
    const message = error.status === 409
      ? 'Produktet findes stadig på lager. Fjern det fra lageret først.'
      : `Produktet kunne ikke fjernes. ${error.message}`;
    showMessage(editError, message);
  } finally { button.disabled = false; }
}

function shoppingRow(item) {
  const row = document.createElement('article');
  row.className = `shopping-row${item.purchased ? ' purchased' : ''}`;
  row.tabIndex = 0; row.setAttribute('role', 'button');
  row.setAttribute('aria-label', item.purchased ? `${item.product.name}, købt` : `Markér ${item.product.name} som købt`);
  const check = document.createElement('span'); check.className = 'shopping-check'; check.textContent = item.purchased ? '✓' : '';
  const name = document.createElement('span'); name.className = 'shopping-row-name'; name.textContent = item.product.name;
  const quantity = document.createElement('span'); quantity.className = 'shopping-row-quantity';
  quantity.textContent = item.product.inventoryTrackingMode === 'PRESENCE' ? 'Køb'
    : `${formatQuantity(item.quantity)} ${displayUnit(item.unit)}`;
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'shopping-edit-button'; edit.textContent = '⋯';
  edit.setAttribute('aria-label', `Rediger ${item.product.name}`); edit.hidden = item.purchased;
  edit.addEventListener('click', event => { event.stopPropagation(); openShoppingEditor(item); });
  row.append(check, name, quantity, edit);
  if (!item.purchased) attachShoppingGestures(row, item);
  else {
    row.setAttribute('aria-label', `Fortryd køb af ${item.product.name}`);
    row.addEventListener('click', () => undoShoppingItem(item));
    row.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); undoShoppingItem(item); } });
  }
  return row;
}

function attachShoppingGestures(row, item) {
  let timer = null; let startX = 0; let startY = 0; let longPressed = false;
  const cancel = () => { clearTimeout(timer); timer = null; };
  row.addEventListener('pointerdown', event => {
    if (event.target.closest('button')) return;
    startX = event.clientX; startY = event.clientY; longPressed = false;
    timer = setTimeout(() => { longPressed = true; openShoppingEditor(item); }, 600);
  });
  row.addEventListener('pointermove', event => {
    if (Math.hypot(event.clientX - startX, event.clientY - startY) > 10) cancel();
  });
  row.addEventListener('pointerup', cancel); row.addEventListener('pointercancel', cancel);
  row.addEventListener('click', () => { if (longPressed) { longPressed = false; return; } purchaseShoppingItem(item); });
  row.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); purchaseShoppingItem(item); } });
}

async function loadShoppingList() {
  const loadingElement = document.querySelector('#shopping-loading'); loadingElement.hidden = false;
  try {
    const items = await jsonRequest(SHOPPING_API);
    const active = items.filter(item => !item.purchased); const purchased = items.filter(item => item.purchased);
    document.querySelector('#shopping-active-list').replaceChildren(...active.map(shoppingRow));
    document.querySelector('#shopping-purchased-list').replaceChildren(...purchased.map(shoppingRow));
    document.querySelector('#shopping-purchased-section').hidden = purchased.length === 0;
    document.querySelector('#shopping-empty').hidden = items.length !== 0;
  } catch (error) { showToast(`Indkøbslisten kunne ikke hentes. ${error.message}`, 'error'); }
  finally { loadingElement.hidden = true; }
}

async function purchaseShoppingItem(item) {
  try {
    await jsonRequest(`${SHOPPING_API}/items/${item.id}/purchase`, { method: 'POST' });
    await loadShoppingList();
    showToast(`${item.product.name} er tilføjet til lageret`, 'success', {
      label: 'Fortryd', run: () => undoShoppingItem(item)
    });
  } catch (error) { showToast(`Varen kunne ikke markeres som købt. ${error.message}`, 'error'); }
}

async function undoShoppingItem(item) {
  try {
    await jsonRequest(`${SHOPPING_API}/items/${item.id}/undo-purchase`, { method: 'POST' });
    await Promise.all([loadShoppingList(), loadInventory()]); showToast(`${item.product.name} er tilbage på listen`);
  } catch (error) { showToast(`Købet kunne ikke fortrydes. ${error.message}`, 'error'); }
}

function shoppingCandidateRow(candidate) {
  const row = inventoryCandidateRow(candidate);
  const replacement = row.cloneNode(true);
  replacement.addEventListener('click', () => selectShoppingCandidate(candidate));
  return replacement;
}

async function searchShoppingCandidates(search) {
  const requestId = ++shoppingSearchRequestId;
  try {
    const [products, templates] = await Promise.all([jsonRequest(PRODUCT_API), jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`)]);
    const input = document.querySelector('#shopping-search');
    if (requestId !== shoppingSearchRequestId || input.value !== search) return;
    const query = normalizeName(search);
    const owned = products.filter(product => !query || normalizeName(product.name).includes(query)).map(product => ({ ...product, source: 'product' }));
    const names = new Set(products.map(product => normalizeName(product.name)));
    const catalog = templates.filter(template => !names.has(normalizeName(template.name))).map(template => ({ ...template, source: 'template' }));
    document.querySelector('#shopping-search-results').replaceChildren(...[...owned, ...catalog].slice(0, 20).map(shoppingCandidateRow));
  } catch (error) { if (requestId === shoppingSearchRequestId) showToast(`Søgningen mislykkedes. ${error.message}`, 'error'); }
}

function resetShoppingAdd() {
  clearTimeout(shoppingSearchTimer); shoppingSearchRequestId++; selectedShoppingCandidate = null;
  document.querySelector('#shopping-search').value = ''; document.querySelector('#shopping-search-results').replaceChildren();
  document.querySelector('#shopping-search-step').hidden = false; document.querySelector('#shopping-amount-form').hidden = true;
  document.querySelector('#shopping-amount-form').reset(); document.querySelector('#shopping-add-conversion').textContent = '';
  document.querySelector('#shopping-add-quantity-controls').hidden = false;
  document.querySelector('#shopping-add-quantity').required = true;
  showMessage(document.querySelector('#shopping-add-error'), '');
}

function openShoppingAdd() { resetShoppingAdd(); document.querySelector('#shopping-add-dialog').showModal(); document.querySelector('#shopping-search').focus(); searchShoppingCandidates(''); }
function closeShoppingAdd() { const dialog = document.querySelector('#shopping-add-dialog'); if (dialog.open) dialog.close(); resetShoppingAdd(); }

function selectShoppingCandidate(candidate) {
  selectedShoppingCandidate = candidate;
  const presence = candidateTrackingMode(candidate) === 'PRESENCE';
  document.querySelector('#shopping-search-step').hidden = true; document.querySelector('#shopping-amount-form').hidden = false;
  document.querySelector('#shopping-selected-name').textContent = candidate.name; document.querySelector('#shopping-selected-unit').textContent = displayUnit(candidate.defaultUnit);
  document.querySelector('#shopping-add-quantity-controls').hidden = presence;
  const input = document.querySelector('#shopping-add-quantity'); input.required = !presence;
  if (presence) { document.querySelector('#shopping-selected-unit').textContent = ''; return; }
  configureQuantityInput(input, candidate.defaultUnit); input.focus();
}

async function addShoppingItem(event) {
  event.preventDefault(); if (!selectedShoppingCandidate) return;
  const quantity = candidateTrackingMode(selectedShoppingCandidate) === 'PRESENCE'
    ? null : numericValue(document.querySelector('#shopping-add-quantity'));
  await submitShoppingCandidate(quantity);
}

async function submitShoppingCandidate(quantity) {
  const url = selectedShoppingCandidate.source === 'product' ? `${SHOPPING_API}/items` : `${SHOPPING_API}/items/from-template/${selectedShoppingCandidate.id}`;
  const payload = selectedShoppingCandidate.source === 'product'
    ? { productId: selectedShoppingCandidate.id, ...(quantity == null ? {} : { quantity }) }
    : (quantity == null ? {} : { quantity });
  try { await jsonRequest(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }); const name = selectedShoppingCandidate.name; closeShoppingAdd(); await Promise.all([loadShoppingList(), loadProducts()]); showToast(`${name} er tilføjet til indkøb`); }
  catch (error) { showMessage(document.querySelector('#shopping-add-error'), error.message); }
}

function openShoppingEditor(item) {
  document.querySelector('#edit-shopping-id').value = item.id; document.querySelector('#edit-shopping-name').textContent = item.product.name;
  const presence = item.product.inventoryTrackingMode === 'PRESENCE';
  document.querySelector('#edit-shopping-quantity-controls').hidden = presence;
  document.querySelector('#edit-shopping-presence').hidden = !presence;
  document.querySelector('#edit-shopping-form').querySelector('button[type="submit"]').hidden = presence;
  const input = document.querySelector('#edit-shopping-quantity'); input.required = !presence;
  input.value = item.quantity ?? ''; configureQuantityInput(input, item.unit);
  document.querySelector('#edit-shopping-unit').textContent = displayUnit(item.unit); updateConversion(input, item.unit, document.querySelector('#edit-shopping-conversion'));
  document.querySelector('#edit-shopping-dialog').showModal(); input.focus();
}
function closeShoppingEditor() { const dialog = document.querySelector('#edit-shopping-dialog'); if (dialog.open) dialog.close(); showMessage(document.querySelector('#edit-shopping-error'), ''); }

async function saveShoppingItem(event) {
  event.preventDefault(); const id = document.querySelector('#edit-shopping-id').value;
  try { await jsonRequest(`${SHOPPING_API}/items/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity: numericValue(document.querySelector('#edit-shopping-quantity')) }) }); closeShoppingEditor(); await loadShoppingList(); showToast('Varen er gemt'); }
  catch (error) { showMessage(document.querySelector('#edit-shopping-error'), error.message); }
}

async function deleteShoppingItem() {
  const id = document.querySelector('#edit-shopping-id').value;
  try { await jsonRequest(`${SHOPPING_API}/items/${id}`, { method: 'DELETE' }); closeShoppingEditor(); await loadShoppingList(); showToast('Varen er fjernet fra indkøbslisten'); }
  catch (error) { showMessage(document.querySelector('#edit-shopping-error'), error.message); }
}

const recipeUnitLabels = { GRAM:'g', MILLILITER:'ml', PIECE:'stk', TEASPOON:'tsk', TABLESPOON:'spsk', DECILITER:'dl' };

function recipeCard(recipe) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'recipe-card';
  const name = document.createElement('strong'); name.textContent = recipe.name;
  const description = document.createElement('span'); description.textContent = recipe.description || `${recipe.ingredients.length} ingredienser`;
  button.append(name, description); button.addEventListener('click', () => openRecipe(recipe.id)); return button;
}

async function loadRecipes() {
  const loading = document.querySelector('#recipes-loading'); loading.hidden = false;
  try { currentRecipes = await jsonRequest(RECIPE_API); document.querySelector('#recipe-list').replaceChildren(...currentRecipes.map(recipeCard)); document.querySelector('#recipes-empty').hidden = currentRecipes.length > 0; }
  catch (error) { showToast(`Opskrifterne kunne ikke hentes. ${error.message}`, 'error'); }
  finally { loading.hidden = true; }
}

function scaledDecimal(value, multiplier) {
  const source = String(value); const negative = source.startsWith('-'); const unsigned = negative ? source.slice(1) : source;
  const [whole, fraction = ''] = unsigned.split('.'); const scale = 10n ** BigInt(fraction.length);
  const scaled = BigInt((whole || '0') + fraction) * BigInt(multiplier); const integral = scaled / scale; const remainder = (scaled % scale).toString().padStart(fraction.length, '0').replace(/0+$/, '');
  return `${negative ? '-' : ''}${integral}${remainder ? `.${remainder}` : ''}`;
}

function danishDecimal(value) { return String(value).replace('.', ','); }

function renderRecipeDetail() {
  if (!currentRecipe) return;
  document.querySelector('#recipe-portions').textContent = `${recipePortions} ${recipePortions === 1 ? 'portion' : 'portioner'}`;
  document.querySelector('#recipe-portions-down').disabled = recipePortions === 1;
  document.querySelector('#recipe-detail-ingredients').replaceChildren(...currentRecipe.ingredients.sort((a,b) => a.sortOrder-b.sortOrder).map(ingredient => {
    const row = document.createElement('div'); row.className = 'recipe-ingredient-row';
    const text = document.createElement('span'); text.textContent = ingredient.productTemplate.name;
    if (ingredient.preparation) { const prep = document.createElement('small'); prep.textContent = ` · ${ingredient.preparation}`; text.append(prep); }
    const amount = document.createElement('strong'); amount.textContent = `${danishDecimal(scaledDecimal(ingredient.quantity, recipePortions))} ${recipeUnitLabels[ingredient.unit]}`;
    row.append(text, amount); return row;
  }));
  document.querySelector('#recipe-detail-steps').replaceChildren(...currentRecipe.steps.sort((a,b) => a.sortOrder-b.sortOrder).map(step => { const li = document.createElement('li'); li.textContent = step.instruction; return li; }));
}

async function openRecipe(id) {
  try { currentRecipe = await jsonRequest(`${RECIPE_API}/${id}`); recipePortions = 2;
    document.querySelector('#recipe-detail-title').textContent = currentRecipe.name;
    const description = document.querySelector('#recipe-detail-description'); description.textContent = currentRecipe.description || ''; description.hidden = !currentRecipe.description;
    document.querySelector('#delete-recipe-confirmation').hidden = true; renderRecipeDetail(); document.querySelector('#recipe-detail-dialog').showModal();
  } catch (error) { showToast(`Opskriften kunne ikke åbnes. ${error.message}`, 'error'); }
}

function closeRecipeDetail() { const dialog = document.querySelector('#recipe-detail-dialog'); if (dialog.open) dialog.close(); }

function resetIngredientPicker() {
  clearTimeout(recipeSearchTimer); recipeSearchRequestId++; selectedRecipeTemplate = null;
  document.querySelector('#ingredient-picker').hidden = true; document.querySelector('#recipe-template-search').value = '';
  document.querySelector('#recipe-template-results').replaceChildren(); document.querySelector('#recipe-ingredient-fields').hidden = true;
  document.querySelector('#recipe-ingredient-quantity').value = ''; document.querySelector('#recipe-ingredient-preparation').value = '';
}

function renderRecipeEditor() {
  document.querySelector('#recipe-editor-ingredients').replaceChildren(...recipeIngredients.map((ingredient, index) => {
    const row = document.createElement('div'); row.className = 'editor-item';
    const text = document.createElement('p'); text.textContent = `${ingredient.template.name} · ${danishDecimal(ingredient.quantity)} ${recipeUnitLabels[ingredient.unit]}${ingredient.preparation ? ` · ${ingredient.preparation}` : ''}`;
    row.append(text, editorActions(index, recipeIngredients, renderRecipeEditor)); return row;
  }));
  document.querySelector('#recipe-editor-steps').replaceChildren(...recipeSteps.map((step, index) => {
    const row = document.createElement('div'); row.className = 'editor-item step-input-row'; const number = document.createElement('strong'); number.textContent = `${index + 1}.`;
    const body = document.createElement('div'); const input = document.createElement('textarea'); input.rows = 2; input.value = step.instruction; input.setAttribute('aria-label', `Trin ${index + 1}`); input.addEventListener('input', () => { recipeSteps[index].instruction = input.value; });
    body.append(input, editorActions(index, recipeSteps, renderRecipeEditor)); row.append(number, body); return row;
  }));
}

function editorActions(index, collection, rerender) {
  const actions = document.createElement('div'); actions.className = 'editor-item-actions';
  [['↑',-1,'Flyt op'],['↓',1,'Flyt ned']].forEach(([label, delta, aria]) => { const button = document.createElement('button'); button.type='button'; button.textContent=label; button.setAttribute('aria-label', aria); button.disabled = index + delta < 0 || index + delta >= collection.length; button.onclick=() => { [collection[index], collection[index+delta]]=[collection[index+delta],collection[index]]; rerender(); }; actions.append(button); });
  const remove = document.createElement('button'); remove.type='button'; remove.textContent='×'; remove.setAttribute('aria-label','Fjern'); remove.onclick=() => { collection.splice(index,1); rerender(); }; actions.append(remove); return actions;
}

function openRecipeEditor(recipe = null) {
  editingRecipeId = recipe?.id || null; document.querySelector('#recipe-editor-title').textContent = recipe ? 'Rediger opskrift' : 'Ny opskrift';
  document.querySelector('#recipe-name').value = recipe?.name || ''; document.querySelector('#recipe-description').value = recipe?.description || '';
  recipeIngredients = (recipe?.ingredients || []).sort((a,b)=>a.sortOrder-b.sortOrder).map(i => ({ template:i.productTemplate, quantity:String(i.quantity), unit:i.unit, preparation:i.preparation || '' }));
  recipeSteps = (recipe?.steps || []).sort((a,b)=>a.sortOrder-b.sortOrder).map(s => ({ instruction:s.instruction }));
  resetIngredientPicker(); renderRecipeEditor(); showMessage(document.querySelector('#recipe-error'), '');
  document.querySelector('#recipe-editor-dialog').showModal(); document.querySelector('#recipe-name').focus();
}

function closeRecipeEditor() { const dialog = document.querySelector('#recipe-editor-dialog'); if (dialog.open) dialog.close(); resetIngredientPicker(); }

async function searchRecipeTemplates(search) {
  const requestId = ++recipeSearchRequestId;
  try { const templates = await jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`); if (requestId !== recipeSearchRequestId || document.querySelector('#recipe-template-search').value !== search) return;
    document.querySelector('#recipe-template-results').replaceChildren(...templates.slice(0,15).map(template => { const button=document.createElement('button'); button.type='button'; button.className='template-row inventory-result'; const text=document.createElement('span'); const name=document.createElement('strong'); name.textContent=template.name; const unit=document.createElement('small'); unit.textContent=displayUnit(template.defaultUnit); text.append(name,unit); button.append(text); button.onclick=()=>selectRecipeTemplate(template); return button; }));
  } catch (error) { if (requestId === recipeSearchRequestId) showToast(`Søgningen mislykkedes. ${error.message}`, 'error'); }
}

function selectRecipeTemplate(template) { selectedRecipeTemplate=template; document.querySelector('#recipe-template-results').replaceChildren(); document.querySelector('#recipe-selected-template').textContent=template.name; document.querySelector('#recipe-ingredient-unit').value=template.defaultUnit; document.querySelector('#recipe-ingredient-fields').hidden=false; document.querySelector('#recipe-ingredient-quantity').focus(); }

function addRecipeIngredient() {
  const raw = document.querySelector('#recipe-ingredient-quantity').value.trim().replace(',','.');
  if (!selectedRecipeTemplate || !/^\d+(\.\d+)?$/.test(raw) || Number(raw) <= 0) { showMessage(document.querySelector('#recipe-error'),'Angiv en gyldig mængde større end nul.'); return; }
  recipeIngredients.push({ template:selectedRecipeTemplate, quantity:raw, unit:document.querySelector('#recipe-ingredient-unit').value, preparation:document.querySelector('#recipe-ingredient-preparation').value.trim() });
  resetIngredientPicker(); renderRecipeEditor(); showMessage(document.querySelector('#recipe-error'),'');
}

async function saveRecipe(event) {
  event.preventDefault(); const name=document.querySelector('#recipe-name').value.trim();
  if (!name || recipeSteps.some(step=>!step.instruction.trim())) { showMessage(document.querySelector('#recipe-error'),'Navn og alle trin skal være udfyldt.'); return; }
  const payload={ name, description:document.querySelector('#recipe-description').value.trim() || null,
    ingredients:recipeIngredients.map((i,index)=>({productTemplateId:i.template.id,quantity:i.quantity,unit:i.unit,preparation:i.preparation||null,sortOrder:index+1})),
    steps:recipeSteps.map((s,index)=>({instruction:s.instruction.trim(),sortOrder:index+1})) };
  try { const url=editingRecipeId ? `${RECIPE_API}/${editingRecipeId}` : RECIPE_API; await jsonRequest(url,{method:editingRecipeId?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}); closeRecipeEditor(); closeRecipeDetail(); await loadRecipes(); showToast(`${name} er gemt`); }
  catch(error){ showMessage(document.querySelector('#recipe-error'),error.message); }
}

async function deleteRecipe() { if (!currentRecipe) return; try { const name=currentRecipe.name; await jsonRequest(`${RECIPE_API}/${currentRecipe.id}`,{method:'DELETE'}); closeRecipeDetail(); await loadRecipes(); showToast(`${name} er slettet`); } catch(error){ showToast(`Opskriften kunne ikke slettes. ${error.message}`,'error'); } }

loginForm.addEventListener('submit', authenticate);
registerForm.addEventListener('submit', register);
document.querySelector('#logout').addEventListener('click', logout);
openFormButton.addEventListener('click', () => setFormOpen(formPanel.hidden));
document.querySelector('#close-form').addEventListener('click', () => setFormOpen(false));
document.querySelector('#empty-add').addEventListener('click', () => setFormOpen(true));
productForm.addEventListener('submit', createProduct);
document.querySelector('#show-custom-product').addEventListener('click', () => { productForm.hidden = !productForm.hidden; if (!productForm.hidden) document.querySelector('#name').focus(); });
templateSearch.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchRequestId++;
  const search = templateSearch.value;
  if (search === '') searchTemplates('');
  else searchTimer = setTimeout(() => searchTemplates(search), 250);
});
editForm.addEventListener('submit', updateProduct);
document.querySelector('#close-edit-product').addEventListener('click', closeProductEditor);
document.querySelector('#cancel-edit-product').addEventListener('click', closeProductEditor);
editDialog.addEventListener('close', () => { editForm.reset(); showMessage(editError, ''); });
document.querySelector('#edit-product-category').replaceChildren(
  ...[...document.querySelector('#category').options].map(option => option.cloneNode(true))
);
document.querySelector('#request-delete-product').addEventListener('click', requestProductDeletion);
document.querySelector('#keep-product').addEventListener('click', cancelProductDeletion);
document.querySelector('#confirm-delete-product').addEventListener('click', deleteProduct);
document.querySelector('#show-products').addEventListener('click', () => showView('products'));
document.querySelector('#show-inventory').addEventListener('click', () => showView('inventory'));
document.querySelector('#show-shopping').addEventListener('click', () => showView('shopping'));
document.querySelector('#show-recipes').addEventListener('click', () => showView('recipes'));
document.querySelector('#open-inventory-add').addEventListener('click', openInventoryAdd);
document.querySelector('#inventory-empty-add').addEventListener('click', openInventoryAdd);
document.querySelector('#close-inventory-add').addEventListener('click', closeInventoryAdd);
document.querySelector('#inventory-add-dialog').addEventListener('close', resetInventoryAdd);
document.querySelector('#back-inventory-search').addEventListener('click', () => {
  selectedInventoryCandidate = null;
  document.querySelector('#inventory-amount-form').hidden = true;
  document.querySelector('#inventory-search-step').hidden = false;
  document.querySelector('#inventory-search').focus();
});
document.querySelector('#inventory-amount-form').addEventListener('submit', addInventory);
document.querySelector('#inventory-add-quantity').addEventListener('input', event =>
  updateConversion(event.target, event.target.dataset.unit, document.querySelector('#inventory-add-conversion')));
document.querySelector('#inventory-search').addEventListener('input', event => {
  clearTimeout(inventorySearchTimer); inventorySearchRequestId++;
  const search = event.target.value;
  if (!search) searchInventoryCandidates('');
  else inventorySearchTimer = setTimeout(() => searchInventoryCandidates(search), 250);
});
document.querySelector('#edit-inventory-form').addEventListener('submit', saveInventory);
document.querySelector('#edit-inventory-quantity').addEventListener('input', event =>
  updateConversion(event.target, event.target.dataset.unit, document.querySelector('#edit-inventory-conversion')));
document.querySelector('#delete-inventory').addEventListener('click', deleteInventory);
document.querySelector('#close-edit-inventory').addEventListener('click', closeInventoryEditor);
document.querySelector('#cancel-edit-inventory').addEventListener('click', closeInventoryEditor);
document.querySelector('#open-shopping-add').addEventListener('click', openShoppingAdd);
document.querySelector('#shopping-empty-add').addEventListener('click', openShoppingAdd);
document.querySelector('#close-shopping-add').addEventListener('click', closeShoppingAdd);
document.querySelector('#shopping-add-dialog').addEventListener('close', resetShoppingAdd);
document.querySelector('#back-shopping-search').addEventListener('click', () => {
  selectedShoppingCandidate = null; document.querySelector('#shopping-amount-form').hidden = true;
  document.querySelector('#shopping-search-step').hidden = false; document.querySelector('#shopping-search').focus();
});
document.querySelector('#shopping-search').addEventListener('input', event => {
  clearTimeout(shoppingSearchTimer); shoppingSearchRequestId++; const search = event.target.value;
  if (!search) searchShoppingCandidates(''); else shoppingSearchTimer = setTimeout(() => searchShoppingCandidates(search), 250);
});
document.querySelector('#shopping-amount-form').addEventListener('submit', addShoppingItem);
document.querySelector('#shopping-add-quantity').addEventListener('input', event =>
  updateConversion(event.target, event.target.dataset.unit, document.querySelector('#shopping-add-conversion')));
document.querySelector('#edit-shopping-form').addEventListener('submit', saveShoppingItem);
document.querySelector('#edit-shopping-quantity').addEventListener('input', event =>
  updateConversion(event.target, event.target.dataset.unit, document.querySelector('#edit-shopping-conversion')));
document.querySelector('#delete-shopping-item').addEventListener('click', deleteShoppingItem);
document.querySelector('#close-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#cancel-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#clear-purchased').addEventListener('click', async () => {
  try { await jsonRequest(`${SHOPPING_API}/purchased`, { method: 'DELETE' }); await loadShoppingList(); showToast('Købte varer er ryddet'); }
  catch (error) { showToast(`Købte varer kunne ikke ryddes. ${error.message}`, 'error'); }
});
document.querySelector('#new-recipe').addEventListener('click', () => openRecipeEditor());
document.querySelector('#recipes-empty-add').addEventListener('click', () => openRecipeEditor());
document.querySelector('#close-recipe-detail').addEventListener('click', closeRecipeDetail);
document.querySelector('#recipe-portions-down').addEventListener('click', () => { if (recipePortions > 1) { recipePortions--; renderRecipeDetail(); } });
document.querySelector('#recipe-portions-up').addEventListener('click', () => { recipePortions++; renderRecipeDetail(); });
document.querySelector('#edit-recipe').addEventListener('click', () => { const recipe=currentRecipe; closeRecipeDetail(); openRecipeEditor(recipe); });
document.querySelector('#delete-recipe').addEventListener('click', () => { document.querySelector('#delete-recipe-confirmation').hidden=false; });
document.querySelector('#keep-recipe').addEventListener('click', () => { document.querySelector('#delete-recipe-confirmation').hidden=true; });
document.querySelector('#confirm-delete-recipe').addEventListener('click', deleteRecipe);
document.querySelector('#close-recipe-editor').addEventListener('click', closeRecipeEditor);
document.querySelector('#cancel-recipe').addEventListener('click', closeRecipeEditor);
document.querySelector('#recipe-form').addEventListener('submit', saveRecipe);
document.querySelector('#add-recipe-ingredient').addEventListener('click', () => { resetIngredientPicker(); document.querySelector('#ingredient-picker').hidden=false; document.querySelector('#recipe-template-search').focus(); searchRecipeTemplates(''); });
document.querySelector('#cancel-recipe-ingredient').addEventListener('click', resetIngredientPicker);
document.querySelector('#save-recipe-ingredient').addEventListener('click', addRecipeIngredient);
document.querySelector('#recipe-template-search').addEventListener('input', event => { clearTimeout(recipeSearchTimer); recipeSearchRequestId++; const search=event.target.value; if (!search) searchRecipeTemplates(''); else recipeSearchTimer=setTimeout(()=>searchRecipeTemplates(search),250); });
document.querySelector('#add-recipe-step').addEventListener('click', () => { recipeSteps.push({instruction:''}); renderRecipeEditor(); document.querySelector('#recipe-editor-steps textarea:last-of-type')?.focus(); });

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js')
    .catch(() => showMessage(authError, 'Appen virker, men offline-understøttelse kunne ikke startes.')));
}

initialize();
