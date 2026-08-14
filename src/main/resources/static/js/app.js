const PRODUCT_API = '/v1/products';
const AUTH_API = '/v1/auth';
const INVENTORY_API = '/v1/inventory';
const SHOPPING_API = '/v1/shopping-list';
const RECIPE_API = '/v1/recipes';
const RECIPE_TEMPLATE_API = '/v1/recipe-templates';
const MEAL_PLAN_API = '/v1/meal-plans';
const KITCHEN_EQUIPMENT_API = '/v1/kitchen-equipment';

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
let recipePlanSelections = new Map();
let currentMealPlans = [];
let currentMealPlan = null;
let currentRecipeTemplates = [];
let currentRecipeTemplate = null;
let recipeTemplatePortions = 2;
let recipeTemplateCatalogTimer;
let recipeTemplateCatalogRequestId = 0;
let currentKitchenEquipment = [];
let editingKitchenEquipment = null;

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
  icon.textContent = '';
  icon.dataset.category = product.category;
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

function groupedProductRows(items, renderRow) {
  const groups = new Map();
  items.forEach(item => {
    const product = item.product || item;
    const key = product.category || 'OTHER';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  });
  return [...groups.entries()].sort(([a], [b]) =>
    (categoryLabels[a] || a).localeCompare(categoryLabels[b] || b, 'da-DK'))
    .map(([category, groupItems]) => {
      const section = document.createElement('section'); section.className = 'category-group';
      const heading = document.createElement('h3'); heading.textContent = categoryLabels[category] || category;
      const rows = document.createElement('div'); rows.className = 'grouped-rows';
      rows.append(...groupItems.map(renderRow)); section.append(heading, rows); return section;
    });
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
    list.replaceChildren(...groupedProductRows(products, createProductCard));
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
  const kitchenActive = view === 'kitchen';
  const mealPlansActive = view === 'meal-plans';
  const moreActive = view === 'more';
  const moreNavActive = moreActive || productsActive || kitchenActive;
  document.querySelector('#products-view').hidden = !productsActive;
  document.querySelector('#inventory-view').hidden = !inventoryActive;
  document.querySelector('#shopping-view').hidden = !shoppingActive;
  document.querySelector('#recipes-view').hidden = !recipesActive;
  document.querySelector('#kitchen-view').hidden = !kitchenActive;
  document.querySelector('#more-view').hidden = !moreActive;
  document.querySelector('#show-products').classList.toggle('active', productsActive);
  document.querySelector('#show-inventory').classList.toggle('active', inventoryActive);
  document.querySelector('#show-shopping').classList.toggle('active', shoppingActive);
  document.querySelector('#show-recipes').classList.toggle('active', recipesActive);
  document.querySelector('#show-meal-plans-primary').classList.toggle('active', mealPlansActive);
  document.querySelector('#show-more').classList.toggle('active', moreNavActive);
  document.querySelectorAll('.primary-nav .nav-button').forEach(button => {
    if (button.classList.contains('active')) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  document.querySelector('#recipes-view').classList.toggle('meal-plan-mode', mealPlansActive);
  document.querySelector('#recipes-title').textContent = mealPlansActive ? 'Madplan' : 'Dine opskrifter';
  document.querySelector('#recipes-title').previousElementSibling.textContent = mealPlansActive ? 'Din planlægning' : 'Opskrifter';
  openFormButton.hidden = !productsActive;
  if (inventoryActive) { setFormOpen(false); loadInventory(); }
  if (shoppingActive) { setFormOpen(false); loadShoppingList(); }
  if (recipesActive) { setFormOpen(false); showRecipeSection('recipes'); loadRecipes(); }
  if (mealPlansActive) { setFormOpen(false); document.querySelector('#recipes-view').hidden = false; showRecipeSection('plans'); loadRecipes(); }
  if (kitchenActive) { setFormOpen(false); loadKitchenEquipment(); }
  if (moreActive) setFormOpen(false);
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
  content.append(name, amount);
  if(item.product.inventoryTrackingMode==='PRESENCE'&&item.plannedUsageCount>0){const usage=document.createElement('button');usage.type='button';usage.className='reservation-link';usage.textContent=`Bruges i ${item.plannedUsageCount} planlagte ${item.plannedUsageCount===1?'ret':'retter'}`;usage.onclick=event=>{event.stopPropagation();openInventoryReservations(item);};content.append(usage);}
  if(item.product.inventoryTrackingMode==='QUANTITY'&&Number(item.reservedQuantity)>0){amount.textContent=`${formatQuantity(item.physicalQuantity)} ${displayUnit(item.unit)} på lager`;const planned=document.createElement('button');planned.type='button';planned.className='reservation-link';planned.textContent=`${formatQuantity(item.reservedQuantity)} ${displayUnit(item.unit)} planlagt`;planned.onclick=event=>{event.stopPropagation();openInventoryReservations(item);};const state=document.createElement('p');state.className=`inventory-availability${Number(item.plannedShortfall)>0?' shortfall':''}`;state.textContent=Number(item.plannedShortfall)>0?`Mangler ${formatQuantity(item.plannedShortfall)} ${displayUnit(item.unit)} til planlagte retter`:Number(item.availableQuantity)===0?'Intet ledigt':`${formatQuantity(item.availableQuantity)} ${displayUnit(item.unit)} ledig`;content.append(planned,state);}
  card.append(content); return card;
}

function openInventoryReservations(item){document.querySelector('#inventory-reservation-title').textContent=item.product.name;const rows=(item.reservations||[]).map(detail=>{const row=document.createElement('article');row.className='reservation-detail-row';const name=document.createElement('strong');name.textContent=detail.recipeName;const amount=document.createElement('span');amount.textContent=detail.reservedQuantity==null?`${detail.portions} ${detail.portions===1?'portion':'portioner'}`:`${formatQuantity(detail.reservedQuantity)} ${displayUnit(detail.unit)}`;const plan=document.createElement('small');plan.textContent=detail.mealPlanName;row.append(name,amount,plan);return row;});document.querySelector('#inventory-reservation-list').replaceChildren(...rows);document.querySelector('#inventory-reservation-dialog').showModal();}

async function loadInventory() {
  const loadingElement = document.querySelector('#inventory-loading');
  const empty = document.querySelector('#inventory-empty');
  loadingElement.hidden = false; empty.hidden = true;
  try {
    const items = await jsonRequest(INVENTORY_API);
    document.querySelector('#inventory-list').replaceChildren(...groupedProductRows(items, createInventoryCard));
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
  document.querySelector('#delete-inventory-confirmation').hidden = true;
  document.querySelector('#request-delete-inventory').hidden = false;
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
    document.querySelector('#shopping-active-list').replaceChildren(...groupedProductRows(active, shoppingRow));
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
  document.querySelector('#delete-shopping-confirmation').hidden = true;
  document.querySelector('#request-delete-shopping-item').hidden = false;
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

async function openRecipe(id, initialPortions = 2) {
  try { currentRecipe = await jsonRequest(`${RECIPE_API}/${id}`); recipePortions = initialPortions;
    document.querySelector('#recipe-detail-title').textContent = currentRecipe.name;
    const description = document.querySelector('#recipe-detail-description'); description.textContent = currentRecipe.description || ''; description.hidden = !currentRecipe.description;
    document.querySelector('#delete-recipe-confirmation').hidden = true; document.querySelector('#cook-recipe-summary').hidden = true; renderRecipeDetail(); document.querySelector('#recipe-detail-dialog').showModal();
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

async function deleteRecipe() { if (!currentRecipe) return; try { const name=currentRecipe.name; await jsonRequest(`${RECIPE_API}/${currentRecipe.id}`,{method:'DELETE'}); closeRecipeDetail(); await loadRecipes(); showToast(`${name} er slettet`); } catch(error){ showToast(error.status===409?error.message:`Opskriften kunne ikke slettes. ${error.message}`,'error'); } }

function selectedRecipePayload() { return { recipes:[...recipePlanSelections.entries()].filter(([,value])=>value.selected).map(([recipeId,value])=>({recipeId,portions:value.portions})) }; }
function renderRecipePlanPreview(recipe, portions, container) {
  const label=document.createElement('p'); label.className='recipe-plan-preview-label'; label.textContent='Skal bruge:';
  const ingredients=document.createElement('ul');
  ingredients.replaceChildren(...[...recipe.ingredients].sort((a,b)=>a.sortOrder-b.sortOrder).map(ingredient=>{
    const item=document.createElement('li'); const amount=danishDecimal(scaledDecimal(ingredient.quantity,portions));
    item.textContent=`${amount} ${recipeUnitLabels[ingredient.unit]} ${ingredient.productTemplate.name}${ingredient.preparation?` · ${ingredient.preparation}`:''}`; return item;
  }));
  container.replaceChildren(label,ingredients);
}
function renderRecipePlan() {
  document.querySelector('#recipe-plan-list').replaceChildren(...currentRecipes.map(recipe => {
    const state=recipePlanSelections.get(recipe.id) || {selected:false,portions:2}; recipePlanSelections.set(recipe.id,state);
    const row=document.createElement('div'); row.className='recipe-plan-row'; const label=document.createElement('label'); const checkbox=document.createElement('input'); checkbox.type='checkbox'; checkbox.checked=state.selected; const name=document.createElement('strong'); name.textContent=recipe.name; label.append(checkbox,name);
    const controls=document.createElement('div'); controls.className='mini-portions'; const down=document.createElement('button'); down.type='button'; down.textContent='−'; const count=document.createElement('span'); count.textContent=`${state.portions} portioner`; const up=document.createElement('button'); up.type='button'; up.textContent='+'; const preview=document.createElement('div'); preview.className='recipe-plan-preview';
    const sync=()=>{controls.hidden=!state.selected;preview.hidden=!state.selected;checkbox.checked=state.selected;count.textContent=`${state.portions} ${state.portions===1?'portion':'portioner'}`;if(state.selected)renderRecipePlanPreview(recipe,state.portions,preview);document.querySelector('#recipe-plan-results').hidden=true;}; checkbox.onchange=()=>{state.selected=checkbox.checked;sync();}; down.onclick=()=>{if(state.portions>1)state.portions--;sync();}; up.onclick=()=>{state.portions++;sync();}; controls.append(down,count,up); row.append(label,controls,preview); sync(); return row;
  }));
}
function openRecipePlan(){recipePlanSelections=new Map(currentRecipes.map(r=>[r.id,{selected:false,portions:2}]));renderRecipePlan();document.querySelector('#recipe-plan-results').hidden=true;document.querySelector('#save-meal-plan-form').hidden=true;document.querySelector('#meal-plan-name').value='';showMessage(document.querySelector('#recipe-plan-error'),'');document.querySelector('#recipe-plan-dialog').showModal();}
function closeRecipePlan(){const dialog=document.querySelector('#recipe-plan-dialog');if(dialog.open)dialog.close();}
function requirementAmount(value,unit){return `${danishDecimal(value)} ${displayUnit(unit)}`;}
function requirementValue(label,value){const line=document.createElement('div');line.className='requirement-value';const key=document.createElement('span');key.textContent=label;const amount=document.createElement('b');amount.textContent=value;line.append(key,amount);return line;}
function requirementResultRow(r){const row=document.createElement('div');row.className='requirement-row';const name=document.createElement('strong');name.textContent=r.productTemplate.name;row.append(name);
  if(r.warning){row.classList.add('warning');const warning=document.createElement('span');warning.textContent=`⚠ ${r.warning}`;row.append(warning);return row;}
  if(r.trackingMode==='PRESENCE'){row.classList.add(r.satisfied?'satisfied':'missing');const status=document.createElement('span');status.className=`requirement-status${r.satisfied?'':' missing'}`;status.textContent=r.satisfied?'✓ På lager':'Mangler';row.append(status);if(r.plannedUsageCount>0){const usage=document.createElement('small');usage.textContent=`Bruges i ${r.plannedUsageCount} andre planlagte ${r.plannedUsageCount===1?'ret':'retter'}`;row.append(usage);}return row;}
  const available=r.availableQuantity??0;const missing=r.missingQuantity??0;row.append(requirementValue('Behov',requirementAmount(r.requiredQuantity,r.unit)),requirementValue('På lager',requirementAmount(r.physicalQuantity??0,r.unit)));if(Number(r.reservedQuantity)>0)row.append(requirementValue('Planlagt til andre retter',requirementAmount(r.reservedQuantity,r.unit)));row.append(requirementValue('Tilgængelig',requirementAmount(available,r.unit)));
  const status=document.createElement('span');if(r.satisfied){row.classList.add('satisfied');status.className='requirement-status';status.textContent='✓ Du har nok';}else{row.classList.add(Number(available)>0?'partial':'missing');status.className='requirement-status missing';status.textContent=`Mangler: ${requirementAmount(missing,r.unit)}`;}row.append(status);return row;}
function renderRequirementResults(calculation){const rows=calculation.requirements.map(requirementResultRow);const hasMissing=calculation.requirements.some(r=>!r.satisfied&&!r.warning);document.querySelector('#recipe-plan-requirements').replaceChildren(...rows);document.querySelector('#recipe-plan-results').hidden=false;document.querySelector('#add-recipe-missing').hidden=!hasMissing;}
async function calculateRecipePlan(){const payload=selectedRecipePayload();if(!payload.recipes.length){showMessage(document.querySelector('#recipe-plan-error'),'Vælg mindst én opskrift.');return;}const button=document.querySelector('#calculate-recipe-plan');button.disabled=true;try{renderRequirementResults(await jsonRequest(`${RECIPE_API}/calculate-requirements`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}));showMessage(document.querySelector('#recipe-plan-error'),'');}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
async function addRecipeMissing(){const button=document.querySelector('#add-recipe-missing');button.disabled=true;try{await jsonRequest(`${RECIPE_API}/add-missing-to-shopping-list`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(selectedRecipePayload())});closeRecipePlan();showToast('Manglerne er tilføjet til indkøb');}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
async function cookCurrentRecipe(){if(!currentRecipe)return;const button=document.querySelector('#cook-recipe');button.disabled=true;try{const result=await jsonRequest(`${RECIPE_API}/${currentRecipe.id}/cook`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({portions:recipePortions})});showToast(`${currentRecipe.name} er markeret som lavet`);const summary=document.querySelector('#cook-recipe-summary');summary.textContent=result.warnings.length?result.warnings.join(' · '):'Lageret er opdateret.';summary.classList.toggle('error',result.warnings.length>0);summary.hidden=false;await loadInventory();}catch(error){showToast(`Opskriften kunne ikke markeres som lavet. ${error.message}`,'error');}finally{button.disabled=false;}}

function showRecipeSection(section){const plans=section==='plans',templates=section==='templates';document.querySelector('#recipe-library-panel').hidden=plans||templates;document.querySelector('#recipe-templates-panel').hidden=!templates;document.querySelector('#meal-plans-panel').hidden=!plans;document.querySelector('#show-recipe-library').classList.toggle('active',!plans&&!templates);document.querySelector('#show-recipe-templates').classList.toggle('active',templates);document.querySelector('#show-meal-plans').classList.toggle('active',plans);if(plans)loadMealPlans();if(templates)loadRecipeTemplates(document.querySelector('#recipe-template-catalog-search').value);}

function recipeTemplateCard(template){const button=document.createElement('button');button.type='button';button.className='recipe-card';const name=document.createElement('strong');name.textContent=template.name;const meta=document.createElement('span');meta.textContent=template.added?'Tilføjet til dine opskrifter':(template.description||'Åbn opskrift');button.append(name,meta);button.onclick=()=>openRecipeTemplate(template.id);return button;}
async function loadRecipeTemplates(query=''){const request=++recipeTemplateCatalogRequestId;const loading=document.querySelector('#recipe-templates-loading');loading.hidden=false;try{const suffix=query.trim()?`?query=${encodeURIComponent(query.trim())}`:'';const result=await jsonRequest(`${RECIPE_TEMPLATE_API}${suffix}`);if(request!==recipeTemplateCatalogRequestId)return;currentRecipeTemplates=result;document.querySelector('#recipe-template-list').replaceChildren(...result.map(recipeTemplateCard));document.querySelector('#recipe-templates-empty').hidden=result.length>0;}catch(error){if(request===recipeTemplateCatalogRequestId)showToast(`Opskrifterne kunne ikke hentes. ${error.message}`,'error');}finally{if(request===recipeTemplateCatalogRequestId)loading.hidden=true;}}
function renderRecipeTemplateDetail(){if(!currentRecipeTemplate)return;document.querySelector('#recipe-template-portions').textContent=`${recipeTemplatePortions} ${recipeTemplatePortions===1?'portion':'portioner'}`;document.querySelector('#recipe-template-portions-down').disabled=recipeTemplatePortions===1;document.querySelector('#recipe-template-detail-ingredients').replaceChildren(...[...currentRecipeTemplate.ingredients].sort((a,b)=>a.sortOrder-b.sortOrder).map(ingredient=>{const row=document.createElement('div');row.className='recipe-ingredient-row';const text=document.createElement('div');const name=document.createElement('div');name.textContent=ingredient.productTemplate.name;const prep=document.createElement('small');prep.textContent=ingredient.preparation||'';text.append(name,prep);const amount=document.createElement('strong');amount.textContent=`${danishDecimal(scaledDecimal(ingredient.quantity,recipeTemplatePortions))} ${recipeUnitLabels[ingredient.unit]}`;row.append(text,amount);return row;}));document.querySelector('#recipe-template-detail-steps').replaceChildren(...[...currentRecipeTemplate.steps].sort((a,b)=>a.sortOrder-b.sortOrder).map(step=>{const item=document.createElement('li');item.textContent=step.instruction;return item;}));const add=document.querySelector('#add-recipe-template');add.textContent=currentRecipeTemplate.added?'Åbn min opskrift':'Føj til mine opskrifter';}
async function openRecipeTemplate(id){try{currentRecipeTemplate=await jsonRequest(`${RECIPE_TEMPLATE_API}/${id}`);recipeTemplatePortions=2;document.querySelector('#recipe-template-detail-title').textContent=currentRecipeTemplate.name;const description=document.querySelector('#recipe-template-detail-description');description.textContent=currentRecipeTemplate.description||'';description.hidden=!currentRecipeTemplate.description;showMessage(document.querySelector('#recipe-template-error'),'');renderRecipeTemplateDetail();document.querySelector('#recipe-template-detail-dialog').showModal();}catch(error){showToast(`Opskriften kunne ikke åbnes. ${error.message}`,'error');}}
function closeRecipeTemplate(){const dialog=document.querySelector('#recipe-template-detail-dialog');if(dialog.open)dialog.close();}
async function addRecipeTemplate(){if(!currentRecipeTemplate)return;const button=document.querySelector('#add-recipe-template');button.disabled=true;try{if(currentRecipeTemplate.added&&currentRecipeTemplate.userRecipeId){closeRecipeTemplate();showRecipeSection('recipes');await openRecipe(currentRecipeTemplate.userRecipeId);return;}const recipe=await jsonRequest(`${RECIPE_TEMPLATE_API}/${currentRecipeTemplate.id}/add-to-my-recipes`,{method:'POST'});currentRecipeTemplate.added=true;currentRecipeTemplate.userRecipeId=recipe.id;await Promise.all([loadRecipes(),loadRecipeTemplates(document.querySelector('#recipe-template-catalog-search').value)]);showToast(`${recipe.name} er føjet til dine opskrifter`);renderRecipeTemplateDetail();}catch(error){showMessage(document.querySelector('#recipe-template-error'),error.message);}finally{button.disabled=false;}}
function mealPlanCard(plan){const button=document.createElement('button');button.type='button';button.className='recipe-card';const name=document.createElement('strong');name.textContent=plan.name;const meta=document.createElement('span');meta.textContent=plan.completed?`${plan.recipes.length} retter · Færdig ✓`:`${plan.recipes.length} ${plan.recipes.length===1?'ret':'retter'}`;button.append(name,meta);button.onclick=()=>openMealPlan(plan.id);return button;}
async function loadMealPlans(){const loading=document.querySelector('#meal-plans-loading');loading.hidden=false;try{currentMealPlans=await jsonRequest(MEAL_PLAN_API);document.querySelector('#meal-plan-list').replaceChildren(...currentMealPlans.map(mealPlanCard));document.querySelector('#meal-plans-empty').hidden=currentMealPlans.length>0;}catch(error){showToast(`Madplanerne kunne ikke hentes. ${error.message}`,'error');}finally{loading.hidden=true;}}
async function saveCurrentMealPlan(event){event.preventDefault();const payload=selectedRecipePayload();const name=document.querySelector('#meal-plan-name').value.trim();if(!name||!payload.recipes.length){showMessage(document.querySelector('#recipe-plan-error'),!name?'Giv madplanen et navn.':'Vælg mindst én opskrift.');return;}const button=event.currentTarget.querySelector('button[type="submit"]');button.disabled=true;try{await jsonRequest(MEAL_PLAN_API,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,recipes:payload.recipes})});closeRecipePlan();showRecipeSection('plans');showToast(`${name} er gemt`);}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
function plannedStatus(status){return {PLANNED:'Planlagt',COOKED:'✓ Lavet',SKIPPED:'Sprunget over'}[status]||status;}
function plannedRecipeRow(item){const row=document.createElement('article');row.className='planned-recipe-row';const main=document.createElement('div');main.className='planned-recipe-main';const text=document.createElement('div');const name=document.createElement('strong');name.textContent=item.recipe?.name||item.recipeName;const portions=document.createElement('div');portions.className='product-meta';portions.textContent=`${item.portions} ${item.portions===1?'portion':'portioner'}`;text.append(name,portions);const status=document.createElement('span');status.className='planned-recipe-status';status.textContent=plannedStatus(item.status);main.append(text,status);if(item.recipe){main.tabIndex=0;main.setAttribute('role','button');const open=()=>openRecipe(item.recipe.id,item.portions);main.onclick=open;main.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();open();}}}row.append(main);
  const actions=document.createElement('div');actions.className='planned-recipe-actions';if(item.status==='PLANNED'){const down=document.createElement('button');down.type='button';down.textContent='− portion';down.disabled=item.portions===1;down.onclick=()=>changePlannedPortions(item,item.portions-1);const up=document.createElement('button');up.type='button';up.textContent='+ portion';up.onclick=()=>changePlannedPortions(item,item.portions+1);const cook=document.createElement('button');cook.type='button';cook.textContent='Markér lavet';cook.onclick=()=>cookPlanned(item,cook);const skip=document.createElement('button');skip.type='button';skip.textContent='Spring over';skip.onclick=()=>togglePlannedSkip(item);actions.append(down,up,cook,skip);}else if(item.status==='SKIPPED'&&item.recipe){const undo=document.createElement('button');undo.type='button';undo.textContent='Planlæg igen';undo.onclick=()=>togglePlannedSkip(item);actions.append(undo);}if(item.status!=='COOKED'){const remove=document.createElement('button');remove.type='button';remove.textContent='Fjern';remove.onclick=()=>removePlannedRecipe(item);actions.append(remove);}row.append(actions);return row;}
function renderMealPlan(plan){currentMealPlan=plan;document.querySelector('#meal-plan-detail-title').textContent=plan.name;document.querySelector('#meal-plan-detail-summary').textContent=plan.completed?`${plan.recipes.length} retter · Færdig ✓`:`${plan.recipes.filter(r=>r.status==='PLANNED').length} planlagt`;document.querySelector('#meal-plan-recipes').replaceChildren(...[...plan.recipes].sort((a,b)=>a.sortOrder-b.sortOrder).map(plannedRecipeRow));document.querySelector('#meal-plan-results').hidden=true;document.querySelector('#delete-meal-plan-confirmation').hidden=true;}
async function openMealPlan(id){try{renderMealPlan(await jsonRequest(`${MEAL_PLAN_API}/${id}`));document.querySelector('#meal-plan-detail-dialog').showModal();}catch(error){showToast(`Madplanen kunne ikke åbnes. ${error.message}`,'error');}}
function closeMealPlan(){const d=document.querySelector('#meal-plan-detail-dialog');if(d.open)d.close();}
async function refreshMealPlan(){renderMealPlan(await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}`));await loadMealPlans();}
async function changePlannedPortions(item,portions){try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({portions,sortOrder:item.sortOrder})});await refreshMealPlan();}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function cookPlanned(item,button){button.disabled=true;try{const result=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}/cook`,{method:'POST'});showToast(`${item.recipe?.name||item.recipeName} er markeret som lavet`);if(result.warnings.length)showMessage(document.querySelector('#meal-plan-error'),result.warnings.join(' · '));await Promise.all([refreshMealPlan(),loadInventory()]);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function togglePlannedSkip(item){try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}/skip`,{method:'POST'});await refreshMealPlan();}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function removePlannedRecipe(item){try{const plan=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}`,{method:'DELETE'});renderMealPlan(plan);await loadMealPlans();showToast(`${item.recipe?.name||item.recipeName} er fjernet fra madplanen`);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function calculateMealPlan(){const button=document.querySelector('#meal-plan-requirements');button.disabled=true;try{const result=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/requirements`);const rows=result.requirements.map(requirementResultRow);document.querySelector('#meal-plan-requirement-list').replaceChildren(...rows);document.querySelector('#meal-plan-results').hidden=false;document.querySelector('#meal-plan-add-missing').hidden=!result.requirements.some(r=>!r.satisfied&&!r.warning);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function addMealPlanMissing(){const button=document.querySelector('#meal-plan-add-missing');button.disabled=true;try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/add-missing-to-shopping-list`,{method:'POST'});showToast('Manglerne er sikret på indkøbslisten');}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function deleteMealPlan(){try{const name=currentMealPlan.name;await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}`,{method:'DELETE'});closeMealPlan();await loadMealPlans();showToast(`${name} er slettet`);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}

const equipmentTypeLabels={STOVE:'Komfur',OVEN:'Ovn',POT:'Gryde',PAN:'Stegepande',AIR_FRYER:'Airfryer',THERMOMETER:'Stegetermometer',MICROWAVE:'Mikroovn'};
const heatSourceLabels={INDUCTION:'Induktion',CERAMIC:'Keramisk',ELECTRIC:'Elektrisk',GAS:'Gas',OTHER:'Andet'};
const ovenModeLabels={CONVENTIONAL:'Almindelig ovn',FAN:'Varmluft',GRILL:'Grill'};
function liters(ml){return `${formatQuantity(ml/1000)} liter`;}
function centimeters(mm){return `${formatQuantity(mm/10)} cm`;}
function equipmentSummary(e){switch(e.equipmentType){case'STOVE':return `${heatSourceLabels[e.heatSource]||''}${e.minimumLevel!=null?` · trin ${e.minimumLevel}–${e.maximumLevel}`:''}`;case'OVEN':return (e.ovenModes||[]).map(x=>ovenModeLabels[x]).join(' / ')||'Ovn';case'POT':return e.capacityMl?liters(e.capacityMl):'Gryde';case'PAN':return [e.diameterMm?centimeters(e.diameterMm):'',e.nonStick?'non-stick':''].filter(Boolean).join(' · ')||'Stegepande';case'AIR_FRYER':return e.capacityMl?liters(e.capacityMl):'Airfryer';case'THERMOMETER':return {INSTANT_READ:'Hurtig aflæsning',PROBE:'Stegesonde',OTHER:'Anden type'}[e.thermometerType]||'Termometer';case'MICROWAVE':return e.maxPowerWatts?`${e.maxPowerWatts} watt`:'Mikroovn';}}
function equipmentCard(e){const button=document.createElement('button');button.type='button';button.className='equipment-card';const text=document.createElement('span');const name=document.createElement('strong');name.textContent=e.name;const meta=document.createElement('span');meta.className='product-meta';meta.textContent=equipmentSummary(e);text.append(name,meta);const badge=document.createElement('span');badge.className='preferred-badge';badge.textContent=e.preferred?'Foretrukket':'›';button.append(text,badge);button.onclick=()=>openKitchenEquipment(e);return button;}
async function loadKitchenEquipment(){const loading=document.querySelector('#kitchen-loading');loading.hidden=false;try{currentKitchenEquipment=await jsonRequest(KITCHEN_EQUIPMENT_API);document.querySelector('#kitchen-equipment-list').replaceChildren(...currentKitchenEquipment.map(equipmentCard));document.querySelector('#kitchen-empty').hidden=currentKitchenEquipment.length>0;}catch(error){showToast(`Dit køkken kunne ikke hentes. ${error.message}`,'error');}finally{loading.hidden=true;}}
function defaultEquipmentName(type){return equipmentTypeLabels[type];}
function setEquipmentFields(type){document.querySelectorAll('[data-equipment-fields]').forEach(section=>section.hidden=section.dataset.equipmentFields!==type);document.querySelector('#kitchen-preferred-row').hidden=!['STOVE','OVEN'].includes(type);}
function suggestedHeatMappings(){const source=document.querySelector('#stove-heat-source').value,minText=document.querySelector('#stove-minimum-level').value,maxText=document.querySelector('#stove-maximum-level').value,min=Number(minText),max=Number(maxText);const gas=source==='GAS'&&(!minText||!maxText);const values=gas?['Lavt blus','Middel-lavt blus','Middel blus','Middel-højt blus','Højt blus','Fuldt blus']:[.15,.30,.50,.70,.85,1].map(p=>String(Math.max(min,Math.min(max,Math.round(min+(max-min)*p)))));document.querySelectorAll('[data-heat]').forEach((input,index)=>input.value=values[index]);}
function resetKitchenEquipmentForm(){const form=document.querySelector('#kitchen-equipment-form');form.reset();editingKitchenEquipment=null;document.querySelector('#kitchen-equipment-id').value='';document.querySelector('#kitchen-equipment-type').disabled=false;document.querySelector('#kitchen-equipment-type').value='STOVE';document.querySelector('#kitchen-equipment-name').value='Komfur';document.querySelector('#stove-minimum-level').value='1';document.querySelector('#stove-maximum-level').value='9';document.querySelector('#delete-kitchen-equipment').hidden=true;document.querySelector('#delete-kitchen-equipment-confirmation').hidden=true;showMessage(document.querySelector('#kitchen-equipment-error'),'');setEquipmentFields('STOVE');suggestedHeatMappings();}
function openKitchenEquipment(e=null){resetKitchenEquipmentForm();editingKitchenEquipment=e;if(e){document.querySelector('#kitchen-equipment-title').textContent='Rediger udstyr';document.querySelector('#kitchen-equipment-id').value=e.id;document.querySelector('#kitchen-equipment-type').value=e.equipmentType;document.querySelector('#kitchen-equipment-type').disabled=true;document.querySelector('#kitchen-equipment-name').value=e.name;document.querySelector('#kitchen-equipment-preferred').checked=e.preferred;setEquipmentFields(e.equipmentType);document.querySelector('#delete-kitchen-equipment').hidden=false;if(e.equipmentType==='STOVE'){document.querySelector('#stove-heat-source').value=e.heatSource;document.querySelector('#stove-minimum-level').value=e.minimumLevel??'';document.querySelector('#stove-maximum-level').value=e.maximumLevel??'';document.querySelectorAll('[data-heat]').forEach(input=>input.value=e.heatMappings?.[input.dataset.heat]||'');}if(e.equipmentType==='OVEN'){document.querySelectorAll('[data-oven-mode]').forEach(input=>input.checked=(e.ovenModes||[]).includes(input.dataset.ovenMode));document.querySelector('#oven-min-temperature').value=e.minimumTemperatureCelsius??'';document.querySelector('#oven-max-temperature').value=e.maximumTemperatureCelsius??'';}if(e.equipmentType==='POT')document.querySelector('#pot-capacity').value=e.capacityMl??'';if(e.equipmentType==='PAN'){document.querySelector('#pan-diameter').value=e.diameterMm??'';document.querySelector('#pan-non-stick').checked=Boolean(e.nonStick);}if(e.equipmentType==='AIR_FRYER'){document.querySelector('#air-fryer-capacity').value=e.capacityMl??'';document.querySelector('#air-fryer-min-temperature').value=e.minimumTemperatureCelsius??'';document.querySelector('#air-fryer-max-temperature').value=e.maximumTemperatureCelsius??'';}if(e.equipmentType==='THERMOMETER')document.querySelector('#thermometer-type').value=e.thermometerType||'OTHER';if(e.equipmentType==='MICROWAVE')document.querySelector('#microwave-power').value=e.maxPowerWatts??'';}else document.querySelector('#kitchen-equipment-title').textContent='Tilføj udstyr';document.querySelector('#kitchen-equipment-dialog').showModal();}
function optionalNumber(selector){const value=document.querySelector(selector).value;return value===''?null:Number(value);}
function kitchenEquipmentPayload(){const type=document.querySelector('#kitchen-equipment-type').value,payload={equipmentType:type,name:document.querySelector('#kitchen-equipment-name').value.trim(),active:true,preferred:document.querySelector('#kitchen-equipment-preferred').checked};if(type==='STOVE'){payload.heatSource=document.querySelector('#stove-heat-source').value;payload.minimumLevel=optionalNumber('#stove-minimum-level');payload.maximumLevel=optionalNumber('#stove-maximum-level');payload.heatMappings=Object.fromEntries([...document.querySelectorAll('[data-heat]')].map(i=>[i.dataset.heat,i.value.trim()]));}if(type==='OVEN'){payload.ovenModes=[...document.querySelectorAll('[data-oven-mode]:checked')].map(i=>i.dataset.ovenMode);payload.minimumTemperatureCelsius=optionalNumber('#oven-min-temperature');payload.maximumTemperatureCelsius=optionalNumber('#oven-max-temperature');}if(type==='POT')payload.capacityMl=optionalNumber('#pot-capacity');if(type==='PAN'){payload.diameterMm=optionalNumber('#pan-diameter');payload.nonStick=document.querySelector('#pan-non-stick').checked;}if(type==='AIR_FRYER'){payload.capacityMl=optionalNumber('#air-fryer-capacity');payload.minimumTemperatureCelsius=optionalNumber('#air-fryer-min-temperature');payload.maximumTemperatureCelsius=optionalNumber('#air-fryer-max-temperature');}if(type==='THERMOMETER')payload.thermometerType=document.querySelector('#thermometer-type').value;if(type==='MICROWAVE')payload.maxPowerWatts=optionalNumber('#microwave-power');return payload;}
async function saveKitchenEquipment(event){event.preventDefault();const button=event.currentTarget.querySelector('button[type="submit"]');button.disabled=true;try{const id=document.querySelector('#kitchen-equipment-id').value;await jsonRequest(id?`${KITCHEN_EQUIPMENT_API}/${id}`:KITCHEN_EQUIPMENT_API,{method:id?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(kitchenEquipmentPayload())});document.querySelector('#kitchen-equipment-dialog').close();await loadKitchenEquipment();showToast(id?'Udstyret er opdateret':'Udstyret er tilføjet');}catch(error){showMessage(document.querySelector('#kitchen-equipment-error'),error.message);}finally{button.disabled=false;}}
async function deleteKitchenEquipment(){if(!editingKitchenEquipment)return;try{await jsonRequest(`${KITCHEN_EQUIPMENT_API}/${editingKitchenEquipment.id}`,{method:'DELETE'});document.querySelector('#kitchen-equipment-dialog').close();await loadKitchenEquipment();showToast('Udstyret er slettet');}catch(error){showMessage(document.querySelector('#kitchen-equipment-error'),error.message);}}

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
document.querySelector('#show-meal-plans-primary').addEventListener('click', () => showView('meal-plans'));
document.querySelector('#show-more').addEventListener('click', () => showView('more'));
document.querySelector('#more-products').addEventListener('click', () => showView('products'));
document.querySelector('#more-kitchen').addEventListener('click', () => showView('kitchen'));
document.querySelector('#more-templates').addEventListener('click', () => { showView('recipes'); showRecipeSection('templates'); });
document.querySelector('#more-logout').addEventListener('click', logout);
document.querySelector('#close-inventory-reservations').addEventListener('click',()=>document.querySelector('#inventory-reservation-dialog').close());
document.querySelector('#show-kitchen').addEventListener('click', () => showView('kitchen'));
document.querySelector('#close-kitchen').addEventListener('click', () => showView('inventory'));
document.querySelector('#add-kitchen-equipment').addEventListener('click',()=>openKitchenEquipment());
document.querySelector('#kitchen-empty-add').addEventListener('click',()=>openKitchenEquipment());
document.querySelector('#close-kitchen-equipment').addEventListener('click',()=>document.querySelector('#kitchen-equipment-dialog').close());
document.querySelector('#kitchen-equipment-type').addEventListener('change',event=>{setEquipmentFields(event.target.value);document.querySelector('#kitchen-equipment-name').value=defaultEquipmentName(event.target.value);if(event.target.value==='STOVE')suggestedHeatMappings();});
document.querySelector('#generate-heat-mappings').addEventListener('click',suggestedHeatMappings);
document.querySelector('#kitchen-equipment-form').addEventListener('submit',saveKitchenEquipment);
document.querySelector('#delete-kitchen-equipment').addEventListener('click',()=>document.querySelector('#delete-kitchen-equipment-confirmation').hidden=false);
document.querySelector('#keep-kitchen-equipment').addEventListener('click',()=>document.querySelector('#delete-kitchen-equipment-confirmation').hidden=true);
document.querySelector('#confirm-delete-kitchen-equipment').addEventListener('click',deleteKitchenEquipment);
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
document.querySelector('#request-delete-inventory').addEventListener('click', () => { document.querySelector('#request-delete-inventory').hidden = true; document.querySelector('#delete-inventory-confirmation').hidden = false; });
document.querySelector('#cancel-delete-inventory').addEventListener('click', () => { document.querySelector('#request-delete-inventory').hidden = false; document.querySelector('#delete-inventory-confirmation').hidden = true; });
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
document.querySelector('#request-delete-shopping-item').addEventListener('click', () => { document.querySelector('#request-delete-shopping-item').hidden = true; document.querySelector('#delete-shopping-confirmation').hidden = false; });
document.querySelector('#cancel-delete-shopping-item').addEventListener('click', () => { document.querySelector('#request-delete-shopping-item').hidden = false; document.querySelector('#delete-shopping-confirmation').hidden = true; });
document.querySelector('#delete-shopping-item').addEventListener('click', deleteShoppingItem);
document.querySelector('#close-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#cancel-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#clear-purchased').addEventListener('click', async () => {
  try { await jsonRequest(`${SHOPPING_API}/purchased`, { method: 'DELETE' }); await loadShoppingList(); showToast('Købte varer er ryddet'); }
  catch (error) { showToast(`Købte varer kunne ikke ryddes. ${error.message}`, 'error'); }
});
document.querySelector('#new-recipe').addEventListener('click', () => openRecipeEditor());
document.querySelector('#plan-recipes').addEventListener('click', openRecipePlan);
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
document.querySelector('#close-recipe-plan').addEventListener('click', closeRecipePlan);
document.querySelector('#calculate-recipe-plan').addEventListener('click', calculateRecipePlan);
document.querySelector('#add-recipe-missing').addEventListener('click', addRecipeMissing);
document.querySelector('#cook-recipe').addEventListener('click', cookCurrentRecipe);
document.querySelector('#show-recipe-library').addEventListener('click',()=>showRecipeSection('recipes'));
document.querySelector('#show-recipe-templates').addEventListener('click',()=>showRecipeSection('templates'));
document.querySelector('#show-meal-plans').addEventListener('click',()=>showRecipeSection('plans'));
document.querySelector('#recipe-template-catalog-search').addEventListener('input',event=>{clearTimeout(recipeTemplateCatalogTimer);recipeTemplateCatalogRequestId++;const query=event.target.value;if(!query)loadRecipeTemplates('');else recipeTemplateCatalogTimer=setTimeout(()=>loadRecipeTemplates(query),250);});
document.querySelector('#close-recipe-template-detail').addEventListener('click',closeRecipeTemplate);
document.querySelector('#recipe-template-portions-down').addEventListener('click',()=>{if(recipeTemplatePortions>1){recipeTemplatePortions--;renderRecipeTemplateDetail();}});
document.querySelector('#recipe-template-portions-up').addEventListener('click',()=>{recipeTemplatePortions++;renderRecipeTemplateDetail();});
document.querySelector('#add-recipe-template').addEventListener('click',addRecipeTemplate);
document.querySelector('#meal-plans-empty-create').addEventListener('click',openRecipePlan);
document.querySelector('#request-save-meal-plan').addEventListener('click',()=>{const form=document.querySelector('#save-meal-plan-form');form.hidden=false;document.querySelector('#meal-plan-name').focus();});
document.querySelector('#cancel-save-meal-plan').addEventListener('click',()=>{document.querySelector('#save-meal-plan-form').hidden=true;});
document.querySelector('#save-meal-plan-form').addEventListener('submit',saveCurrentMealPlan);
document.querySelector('#close-meal-plan-detail').addEventListener('click',closeMealPlan);
document.querySelector('#meal-plan-requirements').addEventListener('click',calculateMealPlan);
document.querySelector('#meal-plan-add-missing').addEventListener('click',addMealPlanMissing);
document.querySelector('#delete-meal-plan').addEventListener('click',()=>{document.querySelector('#delete-meal-plan-confirmation').hidden=false;});
document.querySelector('#keep-meal-plan').addEventListener('click',()=>{document.querySelector('#delete-meal-plan-confirmation').hidden=true;});
document.querySelector('#confirm-delete-meal-plan').addEventListener('click',deleteMealPlan);

const iconPaths = {
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/><path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01"/>',
  inventory: '<path d="M4 7h16l-1 14H5L4 7Z"/><path d="M8 7V5a4 4 0 0 1 8 0v2M9 11h6"/>',
  cart: '<circle cx="9" cy="20" r="1"/><circle cx="19" cy="20" r="1"/><path d="M3 4h2l2.4 11.4a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.6L21 8H6"/>',
  recipe: '<path d="M8 3v5M5.5 3v3a2.5 2.5 0 0 0 5 0V3M8 8v13M16 3c-2 3-2 7 0 9h3V3h-3Zm3 0v18"/>',
  products: '<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4.5 7.8 7.5 4.3 7.5-4.3M12 12v9"/>',
  kitchen: '<path d="M4 10h16M6 10v10h12V10M8 10V6a4 4 0 0 1 8 0v4M9 14h6"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  logout: '<path d="M10 17l5-5-5-5M15 12H3M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5"/>'
};
document.querySelectorAll('[data-icon]').forEach(element => {
  element.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">${iconPaths[element.dataset.icon] || iconPaths.more}</svg>`;
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js')
    .catch(() => showMessage(authError, 'Appen virker, men offline-understøttelse kunne ikke startes.')));
}

initialize();
