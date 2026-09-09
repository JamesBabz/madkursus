const PRODUCT_API = '/v1/products';
const AUTH_API = '/v1/auth';
const INVENTORY_API = '/v1/inventory';
const SHOPPING_API = '/v1/shopping-list';
const RECIPE_API = '/v1/recipes';
const RECIPE_TEMPLATE_API = '/v1/recipe-templates';
const MEAL_PLAN_API = '/v1/meal-plans';
const KITCHEN_EQUIPMENT_API = '/v1/kitchen-equipment';
const COOKING_PROCESS_API = '/v1/cooking-processes';
const NUTRITION_ADMIN_API = '/v1/admin/product-template-nutrition';

// Start dialogs on their header button, without opening a software keyboard.
// Native dialog focus management still handles Tab, Escape, and focus restoration.
document.querySelectorAll('dialog').forEach(dialog => {
  const initialFocus = dialog.querySelector('.section-heading button');
  if (initialFocus) initialFocus.autofocus = true;
});

const categoryLabels = {
  BAKING:t("products.categories.baking"), BREAD:t("products.categories.bread"), DAIRY:t("products.categories.dairy"), EGG:t("products.categories.eggs"), FISH:t("products.categories.fish"), FROZEN:t("products.categories.frozen"),
  FRUIT:t("products.categories.fruit"), GRAIN_PASTA:t("products.categories.grainsPasta"), HERB:t("products.categories.herbs"), LEGUME:t("products.categories.legumes"), MEAT:t("products.categories.meat"),
  NUT_SEED:t("products.categories.nutsSeeds"), OIL_FAT:t("products.categories.oilsFats"), OTHER:t("common.other"), PRESERVED:t("products.categories.preserved"),
  SAUCE_CONDIMENT:t("products.categories.saucesCondiments"), SPICE:t("products.categories.spices"), STOCK:t("products.categories.stock"),
  SWEETENER:t("products.categories.sweeteners"), VEGETABLE:t("products.categories.vegetables"), VINEGAR_ACID:t("products.categories.vinegarAcid"), DRY_GOODS:t("products.categories.dryGoods")
};
const unitLabels = { GRAM: t("units.gram"), MILLILITER: t("units.milliliter"), PIECE: t("units.piece") };
const categoryIcons = {
  MEAT: '🍗', VEGETABLE: '🥕', FRUIT: '🍎', DAIRY: '🥛',
  DRY_GOODS: '🌾', SPICE: '🌿', OTHER: '🍽️'
};

const authScreen = document.querySelector('#auth-screen');
const application = document.querySelector('#application');
const loginForm = document.querySelector('#login-form');
const registerForm = document.querySelector('#register-form');
const loginTab = document.querySelector('#auth-tab-login');
const registerTab = document.querySelector('#auth-tab-register');
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
let currentUser = null;
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
let recipePreparationSteps = [];
let recipeEquipmentRequirements = [];
let recipePreparedComponents = [];
let cookingProcesses = [];
let selectedCookingProcess = null;
let editingProcessStepIndex = null;
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
const aiChat = createAiChat(document.querySelector('#chat-component'), jsonRequest, recipe => {
  aiChatLauncher.close();
  if (recipe.source === 'TEMPLATE') openRecipeTemplate(recipe.id, 1);
  else openRecipe(recipe.id, 1);
});
const aiChatLauncher = createAiChatLauncher(document, aiChat);

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
  const error = new Error(localizeError(detail) || t("common.serverError", {status: response.status}));
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

function showAuthMode(mode = 'login') {
  const showRegister = mode === 'register' && registrationEnabled;
  loginForm.hidden = showRegister;
  registerForm.hidden = !showRegister;
  loginTab.classList.toggle('active', !showRegister);
  registerTab.classList.toggle('active', showRegister);
  loginTab.setAttribute('aria-selected', String(!showRegister));
  registerTab.setAttribute('aria-selected', String(showRegister));
  showMessage(authError, '');
  showMessage(authSuccess, '');
}

function showLogin() {
  registerTab.hidden = !registrationEnabled;
  showAuthMode('login');
}

function showAuthenticatedApp() {
  authScreen.hidden = true;
  application.hidden = false;
  showView('inventory');
  loadProducts();
}

let recipeImportEnabled = false;
let recipeImportNavigationEpoch = 0;
const recipeImport = createRecipeImport({document, request: jsonRequest});
function renderAdminNavigation() {
  const admin = currentUser?.admin === true;
  document.querySelector('#show-nutrition').hidden = !admin;
  document.querySelector('#more-nutrition').hidden = !admin;
  recipeImportEnabled = false;
  document.querySelector('#show-more').classList.remove('import-navigation');
  document.querySelector('#more-recipe-import').hidden = true;
  const epoch = ++recipeImportNavigationEpoch;
  if (admin) jsonRequest('/v1/admin/recipe-template-import').then(status => {
    if (epoch !== recipeImportNavigationEpoch) return;
    recipeImportEnabled = status.enabled === true;
    document.querySelector('#show-more').classList.toggle('import-navigation', recipeImportEnabled);
    document.querySelector('#more-recipe-import').hidden = !recipeImportEnabled;
  }).catch(() => {});
}

function showUnauthenticatedApp() {
  aiChatLauncher.close();
  aiChat.reset();
  recipeImport.reset();
  currentUser = null;
  renderAdminNavigation();
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
    currentUser = await jsonRequest(`${AUTH_API}/me`);
    renderAdminNavigation();
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
    currentUser = await jsonRequest(`${AUTH_API}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: data.get('username').trim(), password: data.get('password') })
    });
    renderAdminNavigation();
    loginForm.reset();
    await refreshCsrfToken();
    showAuthenticatedApp();
  } catch (error) {
    showMessage(authError, t("auth.loginFailed", {message: error.message}));
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
    showMessage(authError, t("auth.passwordsMismatch"));
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
    showAuthMode('login');
    showMessage(authSuccess, t("auth.registered"));
    document.querySelector('#login-username').value = data.get('username').trim();
    document.querySelector('#login-password').focus();
  } catch (error) {
    showMessage(authError, t("auth.registrationFailed", {message: error.message}));
  } finally {
    button.disabled = false;
  }
}

async function logout() {
  aiChatLauncher.close();
  aiChat.reset();
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
  const add = document.createElement('button'); add.type = 'button'; add.className = 'template-add'; add.textContent = t("common.add");
  add.addEventListener('click', () => addTemplate(template.id, add));
  text.append(name, meta); row.append(text, add); return row;
}

async function searchTemplates(search) {
  const requestId = ++searchRequestId;
  try {
    const templates = await jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`);
    if (requestId !== searchRequestId || templateSearch.value !== search) return;
    templateResults.replaceChildren(...templates.slice(0, 12).map(templateRow));
  } catch (error) { if (requestId === searchRequestId) showToast(t("products.catalog.loadFailed", {message: error.message}), 'error'); }
}

async function addTemplate(id, button) {
  button.disabled = true;
  try {
    const created = await jsonRequest(`/v1/products/from-template/${id}`, { method: 'POST' });
    await loadProducts(); showToast(t("products.added", {name: created.name}));
  } catch (error) { showToast(error.message, 'error'); }
  finally { button.disabled = false; }
}

function createProductCard(product) {
  const card = document.createElement('article');
  card.className = 'product-card';
  card.tabIndex = 0;
  card.setAttribute('role', 'button');
  card.setAttribute('aria-label', t("shoppingList.editItemLabel", {name: product.name}));
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
    showToast(t("common.itemSaved", {name: updated.name}));
  } catch (error) { showMessage(editError, t("products.saveFailed", {message: error.message})); }
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
    showMessage(errorMessage, t("products.loadFailed", {message: error.message}));
  } finally {
    loading.hidden = true;
  }
}

function showView(view) {
  if (view === 'recipe-import' && (!currentUser?.admin || !recipeImportEnabled)) return;
  aiChatLauncher.close();
  document.querySelector('#chat-launcher').hidden = view === 'ai';
  const inventoryActive = view === 'inventory';
  const shoppingActive = view === 'shopping';
  const productsActive = view === 'products';
  const recipesActive = view === 'recipes';
  const kitchenActive = view === 'kitchen';
  const mealPlansActive = view === 'meal-plans';
  const moreActive = view === 'more';
  const nutritionActive = view === 'nutrition-admin';
  const importActive = view === 'recipe-import';
  document.querySelector('#recipe-import-view').hidden = !importActive;
  const aiActive = view === 'ai';
  const moreNavActive = moreActive || productsActive || kitchenActive || nutritionActive || aiActive || importActive;
  document.querySelector('#ai-view').hidden = !aiActive;
  document.querySelector('#show-ai').classList.toggle('active', aiActive);
  if (aiActive) { aiChat.scrollToLatest(); aiChat.refreshModel(); }
  document.querySelector('#products-view').hidden = !productsActive;
  document.querySelector('#inventory-view').hidden = !inventoryActive;
  document.querySelector('#shopping-view').hidden = !shoppingActive;
  document.querySelector('#recipes-view').hidden = !recipesActive;
  document.querySelector('#kitchen-view').hidden = !kitchenActive;
  document.querySelector('#more-view').hidden = !moreActive;
  document.querySelector('#nutrition-admin-view').hidden = !nutritionActive;
  document.querySelector('#show-products').classList.toggle('active', productsActive);
  document.querySelector('#show-nutrition').classList.toggle('active', nutritionActive);
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
  document.querySelector('#recipes-title').textContent = mealPlansActive ? t("mealPlan.title") : t("recipes.yourRecipes");
  document.querySelector('#recipes-title').previousElementSibling.textContent = mealPlansActive ? t("mealPlan.subtitle") : t("recipes.title");
  openFormButton.hidden = !productsActive;
  if (inventoryActive) { setFormOpen(false); loadInventory(); }
  if (shoppingActive) { setFormOpen(false); loadShoppingList(); }
  if (recipesActive) { setFormOpen(false); showRecipeSection('recipes'); loadRecipes(); }
  if (mealPlansActive) { setFormOpen(false); document.querySelector('#recipes-view').hidden = false; showRecipeSection('plans'); loadRecipes(); }
  if (kitchenActive) { setFormOpen(false); loadKitchenEquipment(); }
  if (nutritionActive) loadNutritionAdmin();
  if (moreActive) setFormOpen(false);
}

function displayUnit(unit) {
  return { GRAM: t("units.gramShort"), MILLILITER: t("units.milliliterShort"), PIECE: t("units.pieceShort"), TEASPOON: t("units.teaspoonShort"), TABLESPOON: t("units.tablespoonShort"), DECILITER: t("units.deciliterShort"), GRINDER_TURN: t("units.grinderTurns") }[unit] || unit;
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
  return `= ${converted} ${unit === 'GRAM' ? t("units.kilogramShort") : t("units.liter")}`;
}

function updateConversion(input, unit, output) {
  output.textContent = inventoryConversion(input.value, unit);
}

function createInventoryCard(item) {
  const card = document.createElement('article');
  card.className = 'product-card inventory-card'; card.tabIndex = 0; card.setAttribute('role', 'button');
  card.setAttribute('aria-label', t("inventory.editLabel", {name: item.product.name}));
  const open = () => openInventoryEditor(item);
  card.addEventListener('click', open);
  card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); } });
  const content = document.createElement('div');
  const name = document.createElement('h3'); name.className = 'product-name'; name.textContent = item.product.name;
  const amount = document.createElement('p'); amount.className = 'product-meta inventory-amount';
  amount.textContent = item.product.inventoryTrackingMode === 'PRESENCE' ? t("inventory.onHand")
    : `${formatQuantity(item.quantity)} ${displayUnit(item.unit)}`;
  content.append(name, amount);
  if(item.product.inventoryTrackingMode==='PRESENCE'&&item.plannedUsageCount>0){const usage=document.createElement('button');usage.type='button';usage.className='reservation-link';usage.textContent=t("inventory.plannedUses", {plannedUsageCount: item.plannedUsageCount, count: item.plannedUsageCount});usage.onclick=event=>{event.stopPropagation();openInventoryReservations(item);};content.append(usage);}
  if(item.product.inventoryTrackingMode==='QUANTITY'&&item.plannedUsageCount>0&&item.availableQuantity==null){const warning=document.createElement('button');warning.type='button';warning.className='reservation-link';warning.textContent=t('inventory.unknownReservation');warning.onclick=event=>{event.stopPropagation();openInventoryReservations(item);};content.append(warning);}
  if(item.product.inventoryTrackingMode==='QUANTITY'&&Number(item.reservedQuantity)>0){amount.textContent=t("inventory.physicalAmount", {value1: formatQuantity(item.physicalQuantity), value2: displayUnit(item.unit)});const planned=document.createElement('button');planned.type='button';planned.className='reservation-link';planned.textContent=t("inventory.plannedAmount", {value1: formatQuantity(item.reservedQuantity), value2: displayUnit(item.unit)});planned.onclick=event=>{event.stopPropagation();openInventoryReservations(item);};const state=document.createElement('p');state.className=`inventory-availability${Number(item.plannedShortfall)>0?' shortfall':''}`;state.textContent=Number(item.plannedShortfall)>0?t("inventory.plannedShortage", {value1: formatQuantity(item.plannedShortfall), value2: displayUnit(item.unit)}):Number(item.availableQuantity)===0?t("inventory.noneAvailable"):t("inventory.availableAmount", {value1: formatQuantity(item.availableQuantity), value2: displayUnit(item.unit)});content.append(planned,state);}
  card.append(content); return card;
}

function openInventoryReservations(item){document.querySelector('#inventory-reservation-title').textContent=item.product.name;const rows=(item.reservations||[]).map(detail=>{const row=document.createElement('article');row.className='reservation-detail-row';const name=document.createElement('strong');name.textContent=detail.recipeName;const amount=document.createElement('span');amount.textContent=detail.reservedQuantity==null?t("common.portions", {count: detail.portions}):`${formatQuantity(detail.reservedQuantity)} ${displayUnit(detail.unit)}`;const plan=document.createElement('small');plan.textContent=detail.mealPlanName;row.append(name,amount,plan);return row;});document.querySelector('#inventory-reservation-list').replaceChildren(...rows);document.querySelector('#inventory-reservation-dialog').showModal();}

let inventoryForCopy = [];
function inventoryText(items) {
  return items.filter(item => item.product.inventoryTrackingMode !== 'UNTRACKED').map(item => {
    const name = item.product.name.replace(/\s+/g, ' ').trim();
    const amount = item.product.inventoryTrackingMode === 'PRESENCE' ? t("inventory.presenceText")
      : `${formatQuantity(item.quantity)} ${item.unit === 'PIECE' ? t("shoppingList.import.pieceAbbreviation") : displayUnit(item.unit)}`;
    return `- ${name}: ${amount}`;
  }).join('\n');
}
async function copyInventory() {
  try { await navigator.clipboard.writeText(inventoryText(inventoryForCopy)); showToast(t("inventory.copied")); }
  catch (error) { showToast(t("inventory.copyFailed"), 'error'); }
}
async function loadInventory() {
  const loadingElement = document.querySelector('#inventory-loading');
  const empty = document.querySelector('#inventory-empty');
  loadingElement.hidden = false; empty.hidden = true;
  document.querySelector('#copy-inventory').disabled = true;
  try {
    const items = await jsonRequest(INVENTORY_API);
    inventoryForCopy = items;
    document.querySelector('#copy-inventory').disabled = false;
    document.querySelector('#inventory-list').replaceChildren(...groupedProductRows(items, createInventoryCard));
    empty.hidden = items.length !== 0;
  } catch (error) {
    document.querySelector('#inventory-list').replaceChildren();
    showToast(t("inventory.loadFailed", {message: error.message}), 'error');
  } finally { loadingElement.hidden = true; }
}

let shoppingTextBusy = false;
let shoppingTextPreview = null;
function invalidateShoppingText() {
  shoppingTextPreview = null;
  document.querySelector('#shopping-text-preview').hidden = true;
  document.querySelector('#confirm-shopping-text').hidden = true;
  document.querySelector('#preview-shopping-text').hidden = false;
  showMessage(document.querySelector('#shopping-text-error'), '');
}
function openShoppingText() {
  if (shoppingTextBusy) return;
  document.querySelector('#shopping-text-form').reset(); invalidateShoppingText();
  document.querySelector('#shopping-text-dialog').showModal();
}
function closeShoppingText(force = false) {
  if (shoppingTextBusy && force !== true) return;
  document.querySelector('#shopping-text-dialog').close(); invalidateShoppingText();
}
function renderShoppingTextResult(result) {
  const host = document.querySelector('#shopping-text-preview');
  host.replaceChildren(...result.items.map(item => {
    const row = document.createElement('li');
    row.textContent = item.error ? t("shoppingList.import.invalidLine", {line: item.line, value: item.text, error: localizeError(item.error)})
      : t(item.quantity == null
        ? (item.newProduct ? "shoppingList.import.newProduct" : "shoppingList.import.existingProduct")
        : (item.newProduct ? "shoppingList.import.newQuantity" : "shoppingList.import.existingQuantity"), {
          name: item.name, quantity: formatQuantity(item.quantity),
          unit: item.unit === 'PIECE' ? t("shoppingList.import.pieceAbbreviation") : displayUnit(item.unit)
        });
    return row;
  }));
  host.hidden = false;
  document.querySelector('#confirm-shopping-text').hidden = !result.valid;
  document.querySelector('#preview-shopping-text').hidden = result.valid;
  if (!result.valid) showMessage(document.querySelector('#shopping-text-error'), t("shoppingList.import.fixErrors"));
}
async function submitShoppingText(confirm = false) {
  const form = document.querySelector('#shopping-text-form');
  if (shoppingTextBusy || !form.reportValidity()) return;
  const text = document.querySelector('#shopping-text-input').value;
  if (confirm && shoppingTextPreview !== text) return;
  shoppingTextBusy = true;
  form.setAttribute('aria-busy', 'true');
  form.querySelectorAll('button, textarea').forEach(control => { control.disabled = true; });
  const button = document.querySelector(confirm ? '#confirm-shopping-text' : '#preview-shopping-text');
  button.textContent = confirm ? t("shoppingList.import.adding") : t("shoppingList.import.checking");
  showMessage(document.querySelector('#shopping-text-error'), '');
  try {
    const result = await jsonRequest(`${SHOPPING_API}/import${confirm ? '' : '/preview'}`, {
      method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text
    });
    if (result.imported) {
      closeShoppingText(true);
      await Promise.all([loadShoppingList(), loadProducts()]); showToast(t("shoppingList.import.added"));
    } else {
      shoppingTextPreview = result.valid ? text : null;
      renderShoppingTextResult(result);
    }
  } catch (error) { showMessage(document.querySelector('#shopping-text-error'), error.message); }
  finally {
    shoppingTextBusy = false; form.setAttribute('aria-busy', 'false');
    form.querySelectorAll('button, textarea').forEach(control => { control.disabled = false; });
    document.querySelector('#preview-shopping-text').textContent = t("shoppingList.import.preview");
    document.querySelector('#confirm-shopping-text').textContent = t("shoppingList.import.add");
  }
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
    const matchingProducts = products.filter(product => product.inventoryTrackingMode !== 'UNTRACKED').filter(product => !query || normalizeName(product.name).includes(query))
      .map(product => ({ ...product, source: 'product' }));
    const ownedNames = new Set(products.map(product => normalizeName(product.name)));
    const catalog = templates.filter(template => template.defaultTrackingMode !== 'UNTRACKED').filter(template => !ownedNames.has(normalizeName(template.name)))
      .map(template => ({ ...template, source: 'template' }));
    document.querySelector('#inventory-search-results').replaceChildren(
      ...[...matchingProducts, ...catalog].slice(0, 20).map(inventoryCandidateRow)
    );
  } catch (error) { if (requestId === inventorySearchRequestId) showToast(t("recipes.searchFailed", {message: error.message}), 'error'); }
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
  searchInventoryCandidates('');
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
    showToast(t("shoppingList.addedToInventory", {name: name}));
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
  document.querySelector('#edit-inventory-dialog').showModal();
}

function closeInventoryEditor() { const dialog = document.querySelector('#edit-inventory-dialog'); if (dialog.open) dialog.close(); showMessage(document.querySelector('#edit-inventory-error'), ''); }

async function saveInventory(event) {
  event.preventDefault(); const id = document.querySelector('#edit-inventory-id').value;
  try {
    await jsonRequest(`${INVENTORY_API}/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity: numericValue(document.querySelector('#edit-inventory-quantity')) }) });
    closeInventoryEditor(); await loadInventory(); showToast(t("inventory.saved"));
  } catch (error) { showMessage(document.querySelector('#edit-inventory-error'), error.message); }
}

async function deleteInventory() {
  const id = document.querySelector('#edit-inventory-id').value;
  try { await jsonRequest(`${INVENTORY_API}/${id}`, { method: 'DELETE' }); closeInventoryEditor(); await loadInventory(); showToast(t("inventory.itemRemoved")); }
  catch (error) { showMessage(document.querySelector('#edit-inventory-error'), error.message); }
}

async function createProduct(event) {
  event.preventDefault();
  showMessage(errorMessage, '');
  saveButton.disabled = true;
  saveButton.textContent = t("common.saving");
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
    showToast(t("products.added", {name: created.name}));
  } catch (error) {
    showMessage(errorMessage, t("products.saveFailed", {message: error.message}));
  } finally {
    saveButton.disabled = false;
    saveButton.textContent = t("products.save");
  }
}

function requestProductDeletion() {
  const name = document.querySelector('#edit-product-name').value.trim();
  document.querySelector('#delete-product-question').textContent = t("products.confirmDeletion", {name: name});
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
    closeProductEditor(); await loadProducts(); showToast(t("products.removed", {name: name}));
  } catch (error) {
    const message = error.status === 409
      ? t("products.stillInInventory")
      : t("products.deleteFailed", {message: error.message});
    showMessage(editError, message);
  } finally { button.disabled = false; }
}

function shoppingRow(item) {
  const row = document.createElement('article');
  row.className = `shopping-row${item.purchased ? ' purchased' : ''}`;
  row.tabIndex = 0; row.setAttribute('role', 'button');
  row.setAttribute('aria-label', item.purchased ? t("shoppingList.purchasedItemLabel", {name: item.product.name}) : t("shoppingList.markPurchasedLabel", {name: item.product.name}));
  const check = document.createElement('span'); check.className = 'shopping-check'; check.textContent = item.purchased ? '✓' : '';
  const name = document.createElement('span'); name.className = 'shopping-row-name'; name.textContent = item.product.name;
  const quantity = document.createElement('span'); quantity.className = 'shopping-row-quantity';
  quantity.textContent = item.product.inventoryTrackingMode === 'PRESENCE' ? t("shoppingList.purchase")
    : `${formatQuantity(item.quantity)} ${displayUnit(item.unit)}`;
  const edit = document.createElement('button'); edit.type = 'button'; edit.className = 'shopping-edit-button'; edit.textContent = '⋯';
  edit.setAttribute('aria-label', t("shoppingList.editItemLabel", {name: item.product.name})); edit.hidden = item.purchased;
  edit.addEventListener('click', event => { event.stopPropagation(); openShoppingEditor(item); });
  row.append(check, name, quantity, edit);
  if (!item.purchased) attachShoppingGestures(row, item);
  else {
    row.setAttribute('aria-label', t("shoppingList.undoPurchaseLabel", {name: item.product.name}));
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
  } catch (error) { showToast(t("shoppingList.loadFailed", {message: error.message}), 'error'); }
  finally { loadingElement.hidden = true; }
}

const shoppingPurchasesInFlight = new Set();
async function purchaseShoppingItem(item, quantity = undefined) {
  if (shoppingPurchasesInFlight.has(item.id)) return false;
  shoppingPurchasesInFlight.add(item.id);
  try {
    const options = { method: 'POST' };
    if (quantity !== undefined) { options.headers = { 'Content-Type': 'application/json' }; options.body = JSON.stringify({ quantity }); }
    await jsonRequest(`${SHOPPING_API}/items/${item.id}/purchase`, options);
    await loadShoppingList();
    showToast(t("shoppingList.addedToInventory", {name: item.product.name}), 'success', {
      label: t("common.undo"), run: () => undoShoppingItem(item)
    });
    return true;
  } catch (error) {
    if (quantity !== undefined) throw error;
    showToast(t("shoppingList.purchaseFailed", {message: error.message}), 'error');
    return false;
  } finally { shoppingPurchasesInFlight.delete(item.id); }
}

async function undoShoppingItem(item) {
  try {
    await jsonRequest(`${SHOPPING_API}/items/${item.id}/undo-purchase`, { method: 'POST' });
    await Promise.all([loadShoppingList(), loadInventory()]); showToast(t("shoppingList.itemRestored", {name: item.product.name}));
  } catch (error) { showToast(t("shoppingList.undoFailed", {message: error.message}), 'error'); }
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
    const owned = products.filter(product => product.inventoryTrackingMode !== 'UNTRACKED').filter(product => !query || normalizeName(product.name).includes(query)).map(product => ({ ...product, source: 'product' }));
    const names = new Set(products.map(product => normalizeName(product.name)));
    const catalog = templates.filter(template => template.defaultTrackingMode !== 'UNTRACKED').filter(template => !names.has(normalizeName(template.name))).map(template => ({ ...template, source: 'template' }));
    document.querySelector('#shopping-search-results').replaceChildren(...[...owned, ...catalog].slice(0, 20).map(shoppingCandidateRow));
  } catch (error) { if (requestId === shoppingSearchRequestId) showToast(t("recipes.searchFailed", {message: error.message}), 'error'); }
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

function openShoppingAdd() { resetShoppingAdd(); document.querySelector('#shopping-add-dialog').showModal(); searchShoppingCandidates(''); }
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
  try { await jsonRequest(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }); const name = selectedShoppingCandidate.name; closeShoppingAdd(); await Promise.all([loadShoppingList(), loadProducts()]); showToast(t("shoppingList.itemAdded", {name: name})); }
  catch (error) { showMessage(document.querySelector('#shopping-add-error'), error.message); }
}

let shoppingEditorItem = null;
let shoppingEditorBusy = false;
function setShoppingEditorBusy(busy) {
  shoppingEditorBusy = busy;
  const form = document.querySelector('#edit-shopping-form');
  form.setAttribute('aria-busy', String(busy));
  form.querySelectorAll('button, input').forEach(control => { control.disabled = busy; });
  document.querySelector('#purchase-edit-shopping').textContent = busy ? t("common.pleaseWait") : t("common.purchased");
}
function shoppingEditorQuantity() {
  return shoppingEditorItem.product.inventoryTrackingMode === 'PRESENCE'
    ? null : numericValue(document.querySelector('#edit-shopping-quantity'));
}
function changeShoppingEditorQuantity(direction) {
  if (shoppingEditorBusy || !shoppingEditorItem || shoppingEditorItem.product.inventoryTrackingMode === 'PRESENCE') return;
  const input = document.querySelector('#edit-shopping-quantity');
  const increment = { PIECE: 1, GRAM: 100, MILLILITER: 100 }[input.dataset.unit];
  const next = numericValue(input) + direction * increment;
  if (!Number.isFinite(next) || next <= 0) return;
  input.value = next;
  updateConversion(input, input.dataset.unit, document.querySelector('#edit-shopping-conversion'));
}
function openShoppingEditor(item) {
  if (shoppingEditorBusy) return;
  shoppingEditorItem = item;
  setShoppingEditorBusy(false);
  showMessage(document.querySelector('#edit-shopping-error'), '');
  document.querySelector('#edit-shopping-id').value = item.id; document.querySelector('#edit-shopping-name').textContent = item.product.name;
  const presence = item.product.inventoryTrackingMode === 'PRESENCE';
  document.querySelector('#edit-shopping-quantity-controls').hidden = presence;
  document.querySelector('#edit-shopping-presence').hidden = !presence;
  const input = document.querySelector('#edit-shopping-quantity'); input.required = !presence;
  input.value = item.quantity ?? ''; configureQuantityInput(input, item.unit);
  document.querySelector('#edit-shopping-unit').textContent = displayUnit(item.unit); updateConversion(input, item.unit, document.querySelector('#edit-shopping-conversion'));
  document.querySelector('#edit-shopping-dialog').showModal();
}
function closeShoppingEditor(force = false) { if (shoppingEditorBusy && force !== true) return; const dialog = document.querySelector('#edit-shopping-dialog'); if (dialog.open) dialog.close(); showMessage(document.querySelector('#edit-shopping-error'), ''); }

async function saveShoppingItem(event) {
  event.preventDefault(); const id = document.querySelector('#edit-shopping-id').value;
  if (shoppingEditorBusy || !document.querySelector('#edit-shopping-form').reportValidity()) return;
  const quantity = shoppingEditorQuantity();
  setShoppingEditorBusy(true);
  try { await jsonRequest(`${SHOPPING_API}/items/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity }) }); closeShoppingEditor(true); await loadShoppingList(); showToast(t("shoppingList.itemSaved")); }
  catch (error) { showMessage(document.querySelector('#edit-shopping-error'), error.message); }
  finally { setShoppingEditorBusy(false); }
}

async function purchaseEditedShoppingItem() {
  if (shoppingEditorBusy || !document.querySelector('#edit-shopping-form').reportValidity()) return;
  const quantity = shoppingEditorQuantity();
  setShoppingEditorBusy(true);
  try { if (await purchaseShoppingItem(shoppingEditorItem, quantity)) closeShoppingEditor(true); }
  catch (error) { showMessage(document.querySelector('#edit-shopping-error'), error.message); }
  finally { setShoppingEditorBusy(false); }
}

async function deleteShoppingItem() {
  if (shoppingEditorBusy) return;
  const id = document.querySelector('#edit-shopping-id').value;
  setShoppingEditorBusy(true);
  try { await jsonRequest(`${SHOPPING_API}/items/${id}`, { method: 'DELETE' }); closeShoppingEditor(true); await loadShoppingList(); showToast(t("shoppingList.itemRemoved")); }
  catch (error) { showMessage(document.querySelector('#edit-shopping-error'), error.message); }
  finally { setShoppingEditorBusy(false); }
}

const recipeUnitLabels = { GRAM:t("units.gramShort"), MILLILITER:t("units.milliliterShort"), PIECE:t("units.pieceShort"), TEASPOON:t("units.teaspoonShort"), TABLESPOON:t("units.tablespoonShort"), DECILITER:t("units.deciliterShort"), GRINDER_TURN:t("units.grinderTurns") };

function recipeCard(recipe) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'recipe-card';
  const name = document.createElement('strong'); name.textContent = recipe.name;
  const description = document.createElement('span'); description.textContent = recipe.description || t("recipes.ingredientCount", {length: recipe.ingredients.length});
  button.append(name, description); button.addEventListener('click', () => openRecipe(recipe.id)); return button;
}

async function loadRecipes() {
  const loading = document.querySelector('#recipes-loading'); loading.hidden = false;
  try { currentRecipes = await jsonRequest(RECIPE_API); document.querySelector('#recipe-list').replaceChildren(...currentRecipes.map(recipeCard)); document.querySelector('#recipes-empty').hidden = currentRecipes.length > 0; }
  catch (error) { showToast(t("recipes.catalog.loadFailed", {message: error.message}), 'error'); }
  finally { loading.hidden = true; }
}

function scaledDecimal(value, multiplier) {
  const source = String(value); const negative = source.startsWith('-'); const unsigned = negative ? source.slice(1) : source;
  const [whole, fraction = ''] = unsigned.split('.'); const scale = 10n ** BigInt(fraction.length);
  const scaled = BigInt((whole || '0') + fraction) * BigInt(multiplier); const integral = scaled / scale; const remainder = (scaled % scale).toString().padStart(fraction.length, '0').replace(/0+$/, '');
  return `${negative ? '-' : ''}${integral}${remainder ? `.${remainder}` : ''}`;
}

function danishDecimal(value) { const number=Number(value),whole=Math.trunc(number),fraction=Math.round((number-whole)*100)/100;const glyph={0.25:'¼',0.5:'½',0.75:'¾'}[fraction];if(glyph)return whole===0?glyph:`${whole}${glyph}`;return String(value).replace('.', ','); }
function recipeUnitLabel(unit,value){return unit==='GRINDER_TURN'&&Number(value)===1?t("units.grinderTurn"):recipeUnitLabels[unit];}
function renderProcessDetails(step){const rendered=step.renderedProcess||{},details=document.createElement('details');details.className='process-details';const summary=document.createElement('summary');summary.className='process-summary';const title=document.createElement('strong');title.textContent=rendered.processName||step.processName||t("recipes.process.title");summary.append(title);if(rendered.durationSummary){const timing=document.createElement('span');timing.textContent=rendered.durationSummary;summary.append(timing);}if(rendered.inputSummary){const inputs=document.createElement('small');inputs.className='process-input-summary';inputs.textContent=rendered.inputSummary;summary.append(inputs);}const content=document.createElement('div');content.className='process-expanded';const instructions=document.createElement('ul');instructions.className='process-instructions';instructions.replaceChildren(...(rendered.instructions||[]).map(text=>{const item=document.createElement('li');item.textContent=text;return item;}));const completion=document.createElement('p');completion.className='process-completion';completion.textContent=t("recipes.process.completionCondition", {value1: rendered.completionCriterion||t("recipes.process.checkResult")});const warnings=document.createElement('div');warnings.className='process-warnings';warnings.textContent=(rendered.warnings||[]).join(' · ');warnings.hidden=!warnings.textContent;content.append(instructions,completion,warnings);details.append(summary,content);return details;}

function renderCarbohydrates(value,host){if(!value){host.hidden=true;return;}host.hidden=false;const heading=document.createElement('strong');heading.textContent=t("nutrition.carbohydrates");const amount=document.createElement('div');amount.className='nutrition-amount';amount.textContent=t("nutrition.carbohydrates.perPortion", {value1: danishDecimal(value.perPortionGrams)});const total=document.createElement('small');total.textContent=t("nutrition.carbohydrates.total", {value1: danishDecimal(value.totalGrams)});host.replaceChildren(heading,amount,total);if(!value.complete){const warning=document.createElement('p');warning.className='nutrition-warning';warning.textContent=t("nutrition.carbohydrates.unknownContribution", {unknownIngredientCount: value.unknownIngredientCount, count: value.unknownIngredientCount});host.append(warning);}const known=(value.ingredients||[]).filter(i=>i.known);if(known.length){const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent=t("nutrition.carbohydrates.showBreakdown");const list=document.createElement('ul');known.forEach(i=>{const row=document.createElement('li');row.textContent=t("nutrition.carbohydrates.ingredientAmount", {name: i.name, value2: danishDecimal(i.grams)});list.append(row);});details.append(summary,list);host.append(details);}}
function renderUnknownCarbohydrates(value,host){const unknown=(value?.ingredients||[]).filter(i=>!i.known);if(!unknown.length)return;const details=document.createElement('details');const summary=document.createElement('summary');summary.textContent=t("nutrition.showUnknown");const heading=document.createElement('strong');heading.textContent=t("nutrition.missingCarbohydrates");const list=document.createElement('ul');unknown.forEach(i=>{const row=document.createElement('li');row.className='unknown-nutrition-row';const label=document.createElement('span');label.textContent=t("nutrition.ingredientMissing", {name: i.name});row.append(label);if(currentUser?.admin){const maintain=document.createElement('button');maintain.type='button';maintain.className='text-button';maintain.textContent=t("nutrition.openData");maintain.onclick=()=>{pendingNutritionTemplateId=i.productTemplateId;document.querySelector('#nutrition-status').value='ALL';document.querySelector('#nutrition-dtu-status').value='ALL';document.querySelector('#nutrition-search').value='';document.querySelector('#recipe-detail-dialog').close();document.querySelector('#recipe-template-detail-dialog').close();showView('nutrition-admin');};row.append(maintain);}list.append(row);});details.append(summary,heading,list);host.append(details);}
function renderRecipeDetail() {
  if (!currentRecipe) return;
  document.querySelector('#recipe-portions').textContent = t("common.portions", {count: recipePortions});
  document.querySelector('#recipe-portions-down').disabled = recipePortions === 1;
  renderCarbohydrates(currentRecipe.carbohydrates,document.querySelector('#recipe-carbohydrates'));
  renderUnknownCarbohydrates(currentRecipe.carbohydrates,document.querySelector('#recipe-carbohydrates'));
  document.querySelector('#recipe-detail-ingredients').replaceChildren(...currentRecipe.ingredients.sort((a,b) => a.sortOrder-b.sortOrder).map(ingredient => {
    const row = document.createElement('div'); row.className = 'recipe-ingredient-row';
    const text = document.createElement('span'); text.textContent = ingredient.productTemplate.name;
    if (ingredient.preparation) { const prep = document.createElement('small'); prep.textContent = ` · ${ingredient.preparation}`; text.append(prep); }
    const quantity=ingredient.quantity; const amount = document.createElement('strong'); amount.textContent = `${danishDecimal(quantity)} ${recipeUnitLabel(ingredient.unit,quantity)}`;
    row.append(text, amount); return row;
  }));
  document.querySelector('#recipe-detail-steps').replaceChildren(...currentRecipe.steps.sort((a,b) => a.sortOrder-b.sortOrder).map(step => {
    const li = document.createElement('li');
    if (step.type !== 'PROCESS') { li.textContent = step.instruction; return li; }
    li.className='process-step';
    li.append(renderProcessDetails(step)); return li;
  }));
  const preparation=currentRecipe.preparationSteps||[],components=currentRecipe.preparedComponents||[],prepHost=document.querySelector('#recipe-detail-preparation');document.querySelector('#recipe-preparation-section').hidden=!preparation.length&&!components.length;const componentRows=components.sort((a,b)=>a.sortOrder-b.sortOrder).map(component=>{const item=document.createElement('li');item.className='prepared-component-card';const name=document.createElement('strong');name.textContent=component.name;const contents=document.createElement('small');contents.textContent=component.ingredients.map(a=>`${danishDecimal(a.quantity)} ${recipeUnitLabel(a.unit,a.quantity)} ${a.productTemplate?.name||''}`).join(' · ');item.append(name,contents);(component.preparationSteps||[]).forEach(p=>{const line=document.createElement('p');line.textContent=p.instruction;item.append(line);});return item;});prepHost.replaceChildren(...componentRows,...preparation.sort((a,b)=>a.sortOrder-b.sortOrder).map(value=>{const item=document.createElement('li');item.textContent=value.instruction;return item;}));
  const equipment=currentRecipe.equipment||[];document.querySelector('#recipe-equipment-section').hidden=!equipment.length;document.querySelector('#recipe-detail-equipment').replaceChildren(...equipment.map(value=>{const item=document.createElement('li');item.textContent=value;return item;}));
}

async function openRecipe(id, initialPortions = 2) {
  try { recipePortions = initialPortions; currentRecipe = await jsonRequest(`${RECIPE_API}/${id}?portions=${recipePortions}`);
    document.querySelector('#recipe-detail-title').textContent = currentRecipe.name;
    const description = document.querySelector('#recipe-detail-description'); description.textContent = currentRecipe.description || ''; description.hidden = !currentRecipe.description;
    document.querySelector('#delete-recipe-confirmation').hidden = true; document.querySelector('#cook-recipe-summary').hidden = true; renderRecipeDetail(); document.querySelector('#recipe-detail-dialog').showModal();
  } catch (error) { showToast(t("recipes.catalog.openFailed", {message: error.message}), 'error'); }
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
  document.querySelector('#recipe-editor-components').replaceChildren(...recipePreparedComponents.map((component,index)=>{const row=document.createElement('div');row.className='editor-item prepared-component-editor';const text=document.createElement('p');text.textContent=t("recipes.component.summary", {name: component.name, length: component.ingredients.length});row.append(text,editorActions(index,recipePreparedComponents,renderRecipeEditor));return row;}));
  document.querySelector('#recipe-editor-steps').replaceChildren(...recipeSteps.map((step, index) => {
    const row = document.createElement('div'); row.className = 'editor-item step-input-row'; const number = document.createElement('strong'); number.textContent = `${index + 1}.`;
    const body = document.createElement('div');
    if (step.type === 'PROCESS') { const badge=document.createElement('small'); badge.className='process-badge'; badge.textContent=t("recipes.process.label"); const title=document.createElement('strong'); title.textContent=step.processName || t("recipes.process.title"); const edit=document.createElement('button'); edit.type='button'; edit.className='text-button'; edit.textContent=t("recipes.process.edit"); edit.onclick=()=>openProcessPicker(index); body.append(badge,title,edit); }
    else { const input = document.createElement('textarea'); input.rows = 2; input.value = step.instruction; input.setAttribute('aria-label', t("recipes.stepNumber", {value1: index + 1})); input.addEventListener('input', () => { recipeSteps[index].instruction = input.value; }); body.append(input); }
    body.append(editorActions(index, recipeSteps, renderRecipeEditor)); row.append(number, body); return row;
  }));
}

function editorActions(index, collection, rerender) {
  const actions = document.createElement('div'); actions.className = 'editor-item-actions';
  [['↑',-1,t("common.moveUp")],['↓',1,t("common.moveDown")]].forEach(([label, delta, aria]) => { const button = document.createElement('button'); button.type='button'; button.textContent=label; button.setAttribute('aria-label', aria); button.disabled = index + delta < 0 || index + delta >= collection.length; button.onclick=() => { [collection[index], collection[index+delta]]=[collection[index+delta],collection[index]]; rerender(); }; actions.append(button); });
  const remove = document.createElement('button'); remove.type='button'; remove.textContent='×'; remove.setAttribute('aria-label',t("common.remove")); remove.onclick=() => { collection.splice(index,1); rerender(); }; actions.append(remove); return actions;
}

function openRecipeEditor(recipe = null) {
  editingRecipeId = recipe?.id || null; document.querySelector('#recipe-editor-title').textContent = recipe ? t("recipes.edit") : t("recipes.create");
  document.querySelector('#recipe-name').value = recipe?.name || ''; document.querySelector('#recipe-description').value = recipe?.description || '';
  recipeIngredients = (recipe?.ingredients || []).sort((a,b)=>a.sortOrder-b.sortOrder).map(i => ({ id:i.id, template:i.productTemplate, quantity:String(i.quantity), unit:i.unit, preparation:i.preparation || '' }));
  recipeSteps = (recipe?.steps || []).sort((a,b)=>a.sortOrder-b.sortOrder).map(s => ({ type:s.type || 'TEXT', instruction:s.instruction || '', cookingProcessId:s.cookingProcessId, processName:s.processName || t("recipes.process.title"), parameterBindings:s.parameterBindings || [] }));
  recipePreparationSteps=(recipe?.preparationSteps||[]).map(value=>({...value}));recipeEquipmentRequirements=(recipe?.equipmentRequirements||[]).map(value=>({...value}));
  recipePreparedComponents=(recipe?.preparedComponents||[]).map(value=>({...value,ingredients:(value.ingredients||[]).map(a=>({...a}))}));
  resetIngredientPicker(); renderRecipeEditor(); showMessage(document.querySelector('#recipe-error'), '');
  document.querySelector('#recipe-editor-dialog').showModal();
}

function closeRecipeEditor() { const dialog = document.querySelector('#recipe-editor-dialog'); if (dialog.open) dialog.close(); resetIngredientPicker(); }

async function searchRecipeTemplates(search) {
  const requestId = ++recipeSearchRequestId;
  try { const templates = await jsonRequest(`/v1/product-templates?search=${encodeURIComponent(search)}`); if (requestId !== recipeSearchRequestId || document.querySelector('#recipe-template-search').value !== search) return;
    document.querySelector('#recipe-template-results').replaceChildren(...templates.slice(0,15).map(template => { const button=document.createElement('button'); button.type='button'; button.className='template-row inventory-result'; const text=document.createElement('span'); const name=document.createElement('strong'); name.textContent=template.name; const unit=document.createElement('small'); unit.textContent=displayUnit(template.defaultUnit); text.append(name,unit); button.append(text); button.onclick=()=>selectRecipeTemplate(template); return button; }));
  } catch (error) { if (requestId === recipeSearchRequestId) showToast(t("recipes.searchFailed", {message: error.message}), 'error'); }
}

function selectRecipeTemplate(template) { selectedRecipeTemplate=template; document.querySelector('#recipe-template-results').replaceChildren(); document.querySelector('#recipe-selected-template').textContent=template.name; document.querySelector('#recipe-ingredient-unit').value=template.defaultUnit; document.querySelector('#recipe-ingredient-fields').hidden=false; document.querySelector('#recipe-ingredient-quantity').focus(); }

function addRecipeIngredient() {
  const raw = document.querySelector('#recipe-ingredient-quantity').value.trim().replace(',','.');
  if (!selectedRecipeTemplate || !/^\d+(\.\d+)?$/.test(raw) || Number(raw) <= 0) { showMessage(document.querySelector('#recipe-error'),t("recipes.validation.quantityPositive")); return; }
  recipeIngredients.push({ id:crypto.randomUUID(), template:selectedRecipeTemplate, quantity:raw, unit:document.querySelector('#recipe-ingredient-unit').value, preparation:document.querySelector('#recipe-ingredient-preparation').value.trim() });
  resetIngredientPicker(); renderRecipeEditor(); showMessage(document.querySelector('#recipe-error'),'');
}
function openPreparedComponentPicker(){const host=document.querySelector('#prepared-component-ingredients');host.replaceChildren(...recipeIngredients.map(ingredient=>{const row=document.createElement('div');row.className='component-allocation-row';const label=document.createElement('label'),check=document.createElement('input');check.type='checkbox';check.value=ingredient.id;check.dataset.componentIngredient='true';label.append(check,document.createTextNode(` ${ingredient.template.name}`));const quantity=document.createElement('input');quantity.type='number';quantity.min='0.01';quantity.step='any';quantity.value=ingredient.quantity;quantity.dataset.componentQuantity='true';quantity.setAttribute('aria-label',t("recipes.component.ingredientQuantity", {name: ingredient.template.name}));const unit=document.createElement('select');unit.dataset.componentUnit='true';Object.entries(recipeUnitLabels).forEach(([value,text])=>unit.append(new Option(text,value)));unit.value=ingredient.unit;row.append(label,quantity,unit);return row;}));document.querySelector('#prepared-component-picker').hidden=false;document.querySelector('#prepared-component-name').focus();}
function closePreparedComponentPicker(){document.querySelector('#prepared-component-picker').hidden=true;document.querySelector('#prepared-component-name').value='';document.querySelector('#prepared-component-preparation').value='';}
function savePreparedComponent(){const name=document.querySelector('#prepared-component-name').value.trim(),selected=[...document.querySelectorAll('#prepared-component-ingredients [data-component-ingredient]:checked')];if(!name||!selected.length){showMessage(document.querySelector('#recipe-error'),t("recipes.component.validation.nameAndIngredientsRequired"));return;}const ingredients=[];for(const [index,input] of selected.entries()){const row=input.closest('.component-allocation-row'),source=recipeIngredients.find(i=>i.id===input.value),quantity=row.querySelector('[data-component-quantity]').value,unit=row.querySelector('[data-component-unit]').value;if(!quantity||Number(quantity)<=0){showMessage(document.querySelector('#recipe-error'),t("recipes.component.validation.quantityPositive", {name: source.template.name}));return;}ingredients.push({id:crypto.randomUUID(),recipeIngredientId:source.id,productTemplate:source.template,quantity,unit,sortOrder:index+1});}const id=crypto.randomUUID(),key=`COMPONENT_${recipePreparedComponents.length+1}`,instruction=document.querySelector('#prepared-component-preparation').value.trim();recipePreparedComponents.push({id,key,name,sortOrder:recipePreparedComponents.length+1,ingredients,preparationSteps:instruction?[{id:crypto.randomUUID(),instruction,sortOrder:1}]:[]});closePreparedComponentPicker();renderRecipeEditor();}

async function openProcessPicker(stepIndex = null) {
  const picker=document.querySelector('#process-picker'); picker.hidden=false; selectedCookingProcess=null; editingProcessStepIndex=Number.isInteger(stepIndex)?stepIndex:null;
  try {
    if (!cookingProcesses.length) cookingProcesses=await jsonRequest(COOKING_PROCESS_API);
    const select=document.querySelector('#cooking-process-select');
    select.replaceChildren(new Option(t("recipes.process.select"),''),...cookingProcesses.map(process=>new Option(process.name,process.id)));
    document.querySelector('#cooking-process-parameters').replaceChildren(); document.querySelector('#cooking-process-description').textContent='';
    document.querySelector('#save-process-step').textContent=editingProcessStepIndex===null?t("recipes.process.add"):t("recipes.process.save");
    if(editingProcessStepIndex!==null){select.value=recipeSteps[editingProcessStepIndex].cookingProcessId;selectCookingProcess();} else select.focus();
  } catch(error) { picker.hidden=true; showMessage(document.querySelector('#recipe-error'),t("recipes.process.loadFailed", {message: error.message})); }
}

function closeProcessPicker() { document.querySelector('#process-picker').hidden=true; selectedCookingProcess=null; editingProcessStepIndex=null; }
function unitDimension(unit){return unit==='GRAM'?'MASS':unit==='PIECE'?'COUNT':unit==='GRINDER_TURN'?'GRINDER_TURN':'VOLUME';}
function unitFactor(unit){return {GRAM:1,MILLILITER:1,PIECE:1,TEASPOON:5,TABLESPOON:15,DECILITER:100,GRINDER_TURN:1}[unit]||1;}
function convertQuantity(value,from,to){if(unitDimension(from)!==unitDimension(to))return null;return Number(value)*unitFactor(from)/unitFactor(to);}
function compatibleRecipeUnits(unit){return Object.keys(recipeUnitLabels).filter(candidate=>unitDimension(candidate)===unitDimension(unit));}
function allocatedForIngredient(ingredientId,excludeStep=editingProcessStepIndex){let base=0;recipeSteps.forEach((step,index)=>{if(index===excludeStep)return;(step.parameterBindings||[]).forEach(binding=>{if(binding.recipeIngredientId===ingredientId&&binding.quantity!=null&&binding.unit)base+=Number(binding.quantity)*unitFactor(binding.unit);});});return base;}
function exactIngredientMatch(parameter){const aliases={POTATOES:['kartoffel','kartofler'],SALT:['salt'],CHICKEN:['kyllingebryst']};const accepted=new Set([parameter.label.toLocaleLowerCase('da-DK').trim(),...(aliases[parameter.key]||[])]);const matches=recipeIngredients.filter(ingredient=>accepted.has(ingredient.template.name.toLocaleLowerCase('da-DK').trim()));return matches.length===1?matches[0]:null;}
function currentProcessBinding(key){return editingProcessStepIndex===null?null:(recipeSteps[editingProcessStepIndex].parameterBindings||[]).find(binding=>binding.parameterKey===key)||null;}

function updateIngredientAllocation(wrapper,binding=null) {
  const select=wrapper.querySelector('[data-ingredient-select]'),fields=wrapper.querySelector('.ingredient-allocation-fields'),context=wrapper.querySelector('.ingredient-allocation-context');
  if(select?.value.startsWith('component:')){fields.hidden=true;context.textContent=t("recipes.process.componentInputHint");return;}
  const ingredient=recipeIngredients.find(value=>value.id===select?.value); fields.hidden=!ingredient; if(!ingredient){context.textContent='';return;}
  const quantity=wrapper.querySelector('[data-allocation-quantity]'),unit=wrapper.querySelector('[data-allocation-unit]');
  unit.replaceChildren(...compatibleRecipeUnits(ingredient.unit).map(value=>new Option(recipeUnitLabels[value],value)));
  unit.value=binding?.unit&&unitDimension(binding.unit)===unitDimension(ingredient.unit)?binding.unit:ingredient.unit;
  const allocatedBase=allocatedForIngredient(ingredient.id),totalBase=Number(ingredient.quantity)*unitFactor(ingredient.unit),remainingBase=Math.max(0,totalBase-allocatedBase);
  if(binding?.quantity!=null)quantity.value=binding.quantity;else quantity.value=String(remainingBase/unitFactor(unit.value));
  const refresh=()=>{const allocated=allocatedBase/unitFactor(ingredient.unit),remaining=Math.max(0,totalBase-allocatedBase)/unitFactor(ingredient.unit);context.textContent=t("recipes.process.allocationSummary", {value1: danishDecimal(ingredient.quantity), value2: recipeUnitLabels[ingredient.unit], value3: danishDecimal(allocated), value4: recipeUnitLabels[ingredient.unit], value5: danishDecimal(remaining), value6: recipeUnitLabels[ingredient.unit]});};
  unit.onchange=()=>{const converted=convertQuantity(quantity.value,ingredient.unit,unit.value);if(converted!==null)quantity.value=String(converted);refresh();}; refresh();
}

function ingredientParameterField(parameter,binding) {
  const wrapper=document.createElement('fieldset'); wrapper.className='process-parameter ingredient-process-parameter'; wrapper.dataset.parameterKey=parameter.key; wrapper.dataset.parameterType=parameter.type; wrapper.dataset.required=String(parameter.required);
  const legend=document.createElement('legend');legend.textContent=parameter.required ? parameter.label : t("recipes.optionalParameter", {label: parameter.label});wrapper.append(legend);
  if(!recipeIngredients.length){const empty=document.createElement('p');empty.className='process-ingredient-empty';empty.textContent=t("recipes.process.validation.ingredientsRequired");wrapper.append(empty);return wrapper;}
  const selectLabel=document.createElement('label');selectLabel.textContent=t("recipes.process.inputType");const select=document.createElement('select');select.dataset.ingredientSelect='true';select.append(new Option(t("recipes.process.selectInput"),''));recipePreparedComponents.forEach(component=>select.append(new Option(component.name,`component:${component.id}`)));recipeIngredients.forEach(ingredient=>select.append(new Option(`${ingredient.template.name} · ${danishDecimal(ingredient.quantity)} ${recipeUnitLabels[ingredient.unit]}`,ingredient.id)));selectLabel.append(select);wrapper.append(selectLabel);
  const fields=document.createElement('div');fields.className='ingredient-allocation-fields';fields.hidden=true;const quantityLabel=document.createElement('label');quantityLabel.textContent=t("recipes.process.ingredientUsage");const allocation=document.createElement('div');allocation.className='quantity-unit-row';const quantity=document.createElement('input');quantity.type='number';quantity.min='0.01';quantity.step='any';quantity.inputMode='decimal';quantity.dataset.allocationQuantity='true';const unit=document.createElement('select');unit.dataset.allocationUnit='true';allocation.append(quantity,unit);quantityLabel.append(allocation);const context=document.createElement('small');context.className='ingredient-allocation-context';fields.append(quantityLabel,context);wrapper.append(fields);
  const preferred=binding?.preparedComponentId?`component:${binding.preparedComponentId}`:binding?.recipeIngredientId||exactIngredientMatch(parameter)?.id||'';select.value=preferred;select.onchange=()=>updateIngredientAllocation(wrapper);updateIngredientAllocation(wrapper,binding);return wrapper;
}

function processParameterField(parameter) {
  const binding=currentProcessBinding(parameter.key);if(parameter.type==='INGREDIENT_QUANTITY')return ingredientParameterField(parameter,binding);
  if(parameter.type==='INGREDIENT_LIST')return ingredientSetField(parameter);
  const wrapper=document.createElement('label'); wrapper.className='process-parameter';wrapper.dataset.parameterKey=parameter.key;wrapper.dataset.parameterType=parameter.type;wrapper.dataset.required=String(parameter.required);wrapper.dataset.source=parameter.source||'INPUT';wrapper.textContent=`${parameter.label}${parameter.required?'':t("recipes.process.optionalSuffix")}`;
  if(parameter.type==='DURATION'){const seconds=binding?.durationSeconds??parameter.defaultValue?.durationSeconds??0;const row=document.createElement('div');row.className='duration-input-row';const minutes=document.createElement('input');minutes.type='number';minutes.min='0';minutes.step='1';minutes.inputMode='numeric';minutes.dataset.durationMinutes='true';minutes.value=String(Math.floor(seconds/60));const minuteText=document.createElement('span');minuteText.textContent=t("recipes.process.minutes");const remainder=document.createElement('input');remainder.type='number';remainder.min='0';remainder.max='59';remainder.step='1';remainder.inputMode='numeric';remainder.dataset.durationSeconds='true';remainder.value=String(seconds%60);const secondText=document.createElement('span');secondText.textContent=t("recipes.process.seconds");row.append(minutes,minuteText,remainder,secondText);wrapper.append(row);return wrapper;}
  let input;if(parameter.type==='HEAT_LEVEL'){input=document.createElement('select');['LOW','MEDIUM_LOW','MEDIUM','MEDIUM_HIGH','HIGH','MAX'].forEach(level=>input.append(new Option({LOW:t("recipes.process.heat.low"),MEDIUM_LOW:t("recipes.process.heat.mediumLow"),MEDIUM:t("recipes.process.heat.medium"),MEDIUM_HIGH:t("recipes.process.heat.mediumHigh"),HIGH:t("recipes.process.heat.high"),MAX:t("recipes.process.heat.maximum")}[level],level)));input.value=binding?.heatLevel||parameter.defaultValue?.heatLevel||'';}else{input=document.createElement('input');input.type=parameter.type==='TEXT'?'text':'number';if(parameter.type==='TEMPERATURE')input.min='0';else if(parameter.type==='QUANTITY')input.min='0.01';input.step=parameter.type==='NUMBER'||parameter.type==='QUANTITY'?'any':'1';if(parameter.type==='TEMPERATURE')input.value=binding?.temperatureCelsius??parameter.defaultValue?.temperatureCelsius??'';else if(parameter.type==='NUMBER')input.value=binding?.number??parameter.defaultValue?.number??'';else if(parameter.type==='QUANTITY')input.value=binding?.quantity??parameter.defaultValue?.quantity??'';else input.value=binding?.text??parameter.defaultValue?.text??'';}input.dataset.parameterInput='true';input.dataset.unit=parameter.unit||parameter.defaultValue?.unit||'';wrapper.append(input);if(parameter.type==='TEMPERATURE'){const help=document.createElement('small');help.textContent='°C';wrapper.append(help);}if(parameter.type==='QUANTITY'&&input.dataset.unit){const help=document.createElement('small');help.textContent=recipeUnitLabels[input.dataset.unit]||input.dataset.unit;wrapper.append(help);}return wrapper;
}

function ingredientSetField(parameter){const wrapper=document.createElement('fieldset');wrapper.className='process-parameter ingredient-set-parameter';wrapper.dataset.parameterKey=parameter.key;wrapper.dataset.parameterType=parameter.type;wrapper.dataset.required=String(parameter.required);const legend=document.createElement('legend');legend.textContent=parameter.label;wrapper.append(legend);const existing=editingProcessStepIndex===null?[]:(recipeSteps[editingProcessStepIndex].parameterBindings||[]).filter(b=>b.parameterKey.startsWith(parameter.key+':'));recipeIngredients.forEach(ingredient=>{const member=document.createElement('div');member.className='ingredient-set-member';const label=document.createElement('label');const checkbox=document.createElement('input');checkbox.type='checkbox';checkbox.dataset.setIngredient=ingredient.id;const bound=existing.find(b=>b.recipeIngredientId===ingredient.id);checkbox.checked=!!bound;label.append(checkbox,document.createTextNode(` ${ingredient.template.name} · ${danishDecimal(ingredient.quantity)} ${recipeUnitLabels[ingredient.unit]}`));const allocation=document.createElement('div');allocation.className='quantity-unit-row';allocation.hidden=!checkbox.checked;const quantity=document.createElement('input');quantity.type='number';quantity.min='0.01';quantity.step='any';quantity.dataset.setQuantity='true';quantity.value=bound?.quantity??ingredient.quantity;const unit=document.createElement('select');unit.dataset.setUnit='true';compatibleRecipeUnits(ingredient.unit).forEach(value=>unit.append(new Option(recipeUnitLabels[value],value)));unit.value=bound?.unit||ingredient.unit;checkbox.onchange=()=>allocation.hidden=!checkbox.checked;allocation.append(quantity,unit);member.append(label,allocation);wrapper.append(member);});return wrapper;}

function selectCookingProcess() {selectedCookingProcess=cookingProcesses.find(process=>process.id===document.querySelector('#cooking-process-select').value)||null;document.querySelector('#cooking-process-description').textContent=selectedCookingProcess?.description||'';const host=document.querySelector('#cooking-process-parameters');host.replaceChildren();const parameters=(selectedCookingProcess?.parameters||[]).sort((a,b)=>a.sortOrder-b.sortOrder),normal=parameters.filter(p=>(p.source||'INPUT')==='INPUT'),advanced=parameters.filter(p=>(p.source||'INPUT')!=='INPUT'&&p.source!=='DEFAULT');host.append(...normal.map(processParameterField));if(advanced.length){const details=document.createElement('details');details.className='process-advanced';const summary=document.createElement('summary');summary.textContent=t("recipes.process.advancedSettings");details.append(summary,...advanced.map(processParameterField));host.append(details);}}

function addProcessStep() {
  if(!selectedCookingProcess){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.processRequired"));return;}const bindings=[];
  for(const wrapper of document.querySelectorAll('#cooking-process-parameters .process-parameter')){const key=wrapper.dataset.parameterKey,type=wrapper.dataset.parameterType,required=wrapper.dataset.required==='true';const binding={parameterKey:key};
    if(type==='INGREDIENT_QUANTITY'){const select=wrapper.querySelector('[data-ingredient-select]');if(!select?.value){if(required){showMessage(document.querySelector('#recipe-error'),recipeIngredients.length?t("recipes.process.validation.ingredientRequired", {textContent: wrapper.querySelector('legend').textContent}):t("recipes.process.validation.ingredientsRequired"));select?.focus();return;}continue;}if(select.value.startsWith('component:')){binding.preparedComponentId=select.value.substring(10);bindings.push(binding);continue;}const ingredient=recipeIngredients.find(value=>value.id===select.value),quantity=wrapper.querySelector('[data-allocation-quantity]').value,unit=wrapper.querySelector('[data-allocation-unit]').value;if(!quantity||Number(quantity)<=0){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.parameterQuantityPositive", {textContent: wrapper.querySelector('legend').textContent}));return;}const allocatedBase=allocatedForIngredient(ingredient.id),requestedBase=Number(quantity)*unitFactor(unit),totalBase=Number(ingredient.quantity)*unitFactor(ingredient.unit);if(allocatedBase+requestedBase>totalBase+1e-9){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.allocationExceeded", {name: ingredient.template.name, value2: danishDecimal(Math.max(0,totalBase-allocatedBase)/unitFactor(ingredient.unit)), value3: recipeUnitLabels[ingredient.unit]}));return;}Object.assign(binding,{recipeIngredientId:ingredient.id,productTemplateId:ingredient.template.id,quantity,unit});}
    else if(type==='INGREDIENT_LIST'){for(const input of wrapper.querySelectorAll('[data-set-ingredient]:checked')){const ingredient=recipeIngredients.find(i=>i.id===input.dataset.setIngredient),member=input.closest('.ingredient-set-member'),quantity=member.querySelector('[data-set-quantity]').value,unit=member.querySelector('[data-set-unit]').value;if(!quantity||Number(quantity)<=0){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.ingredientQuantityPositive", {name: ingredient.template.name}));return;}bindings.push({parameterKey:`${key}:${ingredient.id}`,recipeIngredientId:ingredient.id,productTemplateId:ingredient.template.id,quantity,unit});}continue;}
    else if(type==='DURATION'){const minutes=wrapper.querySelector('[data-duration-minutes]').value,seconds=wrapper.querySelector('[data-duration-seconds]').value;if(!/^\d+$/.test(minutes)||!/^\d+$/.test(seconds)||Number(seconds)>59){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.durationInvalid", {textContent: wrapper.firstChild.textContent}));return;}const total=Number(minutes)*60+Number(seconds);const original=currentProcessBinding(key)?.durationSeconds,standard=selectedCookingProcess.parameters.find(p=>p.key===key)?.defaultValue?.durationSeconds;if(total>0&&total!==standard)binding.durationSeconds=total;else if(original&&total===original&&original!==standard)binding.durationSeconds=total;else continue;}
    else{const input=wrapper.querySelector('[data-parameter-input]'),value=input.value;if(!value&&required){showMessage(document.querySelector('#recipe-error'),t("recipes.process.validation.fieldRequired", {textContent: wrapper.firstChild.textContent}));input.focus();return;}if(!value)continue;const parameter=selectedCookingProcess.parameters.find(p=>p.key===key),defaults=parameter?.defaultValue||{},defaultValue=type==='HEAT_LEVEL'?defaults.heatLevel:type==='TEMPERATURE'?defaults.temperatureCelsius:type==='NUMBER'?defaults.number:type==='QUANTITY'?defaults.quantity:defaults.text;if(wrapper.dataset.source!=='INPUT'&&!currentProcessBinding(key)&&String(value)===String(defaultValue??''))continue;if(type==='HEAT_LEVEL')binding.heatLevel=value;else if(type==='TEMPERATURE')binding.temperatureCelsius=Number(value);else if(type==='NUMBER')binding.number=value;else if(type==='QUANTITY'){binding.quantity=value;binding.unit=input.dataset.unit;}else binding.text=value.trim();}
    bindings.push(binding);
  }
  const step={type:'PROCESS',cookingProcessId:selectedCookingProcess.id,processName:selectedCookingProcess.name,parameterBindings:bindings,instruction:''};if(editingProcessStepIndex===null)recipeSteps.push(step);else recipeSteps[editingProcessStepIndex]=step;closeProcessPicker();renderRecipeEditor();showMessage(document.querySelector('#recipe-error'),'');
}

async function saveRecipe(event) {
  event.preventDefault(); const name=document.querySelector('#recipe-name').value.trim();
  if (!name || recipeSteps.some(step=>step.type !== 'PROCESS' && !step.instruction.trim())) { showMessage(document.querySelector('#recipe-error'),t("recipes.validation.nameAndStepsRequired")); return; }
  const payload={ name, description:document.querySelector('#recipe-description').value.trim() || null,
    ingredients:recipeIngredients.map((i,index)=>({id:i.id,productTemplateId:i.template.id,quantity:i.quantity,unit:i.unit,preparation:i.preparation||null,sortOrder:index+1})),
    steps:recipeSteps.map((s,index)=>s.type === 'PROCESS' ? {type:'PROCESS',cookingProcessId:s.cookingProcessId,parameterBindings:s.parameterBindings,sortOrder:index+1} : {type:'TEXT',instruction:s.instruction.trim(),parameterBindings:[],sortOrder:index+1}),preparationSteps:recipePreparationSteps,equipmentRequirements:recipeEquipmentRequirements,preparedComponents:recipePreparedComponents.map((c,index)=>({...c,sortOrder:index+1,ingredients:c.ingredients.map(({productTemplate,...a},ingredientIndex)=>({...a,sortOrder:ingredientIndex+1}))})) };
  try { const url=editingRecipeId ? `${RECIPE_API}/${editingRecipeId}` : RECIPE_API; await jsonRequest(url,{method:editingRecipeId?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}); closeRecipeEditor(); closeRecipeDetail(); await loadRecipes(); showToast(t("common.itemSaved", {name: name})); }
  catch(error){ showMessage(document.querySelector('#recipe-error'),error.message); }
}

async function deleteRecipe() { if (!currentRecipe) return; try { const name=currentRecipe.name; await jsonRequest(`${RECIPE_API}/${currentRecipe.id}`,{method:'DELETE'}); closeRecipeDetail(); await loadRecipes(); showToast(t("common.itemDeleted", {name: name})); } catch(error){ showToast(error.status===409?error.message:t("recipes.deleteFailed", {message: error.message}),'error'); } }

function selectedRecipePayload() { return { recipes:[...recipePlanSelections.entries()].filter(([,value])=>value.selected).map(([recipeId,value])=>({recipeId,portions:value.portions})) }; }
function renderRecipePlanPreview(recipe, portions, container) {
  const label=document.createElement('p'); label.className='recipe-plan-preview-label'; label.textContent=t("recipes.requirementsLabel");
  const ingredients=document.createElement('ul');
  ingredients.replaceChildren(...[...recipe.ingredients].sort((a,b)=>a.sortOrder-b.sortOrder).map(ingredient=>{
    const item=document.createElement('li'); const amount=danishDecimal(scaledDecimal(ingredient.quantity,portions));
    item.textContent=`${amount} ${recipeUnitLabel(ingredient.unit,scaledDecimal(ingredient.quantity,portions))} ${ingredient.productTemplate.name}${ingredient.preparation?` · ${ingredient.preparation}`:''}`; return item;
  }));
  container.replaceChildren(label,ingredients);
}
function renderRecipePlan() {
  document.querySelector('#recipe-plan-list').replaceChildren(...currentRecipes.map(recipe => {
    const state=recipePlanSelections.get(recipe.id) || {selected:false,portions:2}; recipePlanSelections.set(recipe.id,state);
    const row=document.createElement('div'); row.className='recipe-plan-row'; const label=document.createElement('label'); const checkbox=document.createElement('input'); checkbox.type='checkbox'; checkbox.checked=state.selected; const name=document.createElement('strong'); name.textContent=recipe.name; label.append(checkbox,name);
    const controls=document.createElement('div'); controls.className='mini-portions'; const down=document.createElement('button'); down.type='button'; down.textContent='−'; const count=document.createElement('span'); count.textContent=t("mealPlan.portions", {portions: state.portions}); const up=document.createElement('button'); up.type='button'; up.textContent='+'; const preview=document.createElement('div'); preview.className='recipe-plan-preview';
    const sync=()=>{controls.hidden=!state.selected;preview.hidden=!state.selected;checkbox.checked=state.selected;count.textContent=t("common.portions", {count: state.portions});if(state.selected)renderRecipePlanPreview(recipe,state.portions,preview);document.querySelector('#recipe-plan-results').hidden=true;}; checkbox.onchange=()=>{state.selected=checkbox.checked;sync();}; down.onclick=()=>{if(state.portions>1)state.portions--;sync();}; up.onclick=()=>{state.portions++;sync();}; controls.append(down,count,up); row.append(label,controls,preview); sync(); return row;
  }));
}
function openRecipePlan(){recipePlanSelections=new Map(currentRecipes.map(r=>[r.id,{selected:false,portions:2}]));renderRecipePlan();document.querySelector('#recipe-plan-results').hidden=true;document.querySelector('#save-meal-plan-form').hidden=true;document.querySelector('#meal-plan-name').value='';showMessage(document.querySelector('#recipe-plan-error'),'');document.querySelector('#recipe-plan-dialog').showModal();}
function closeRecipePlan(){const dialog=document.querySelector('#recipe-plan-dialog');if(dialog.open)dialog.close();}
function requirementAmount(value,unit){return `${danishDecimal(value)} ${displayUnit(unit)}`;}
function requirementValue(label,value){const line=document.createElement('div');line.className='requirement-value';const key=document.createElement('span');key.textContent=label;const amount=document.createElement('b');amount.textContent=value;line.append(key,amount);return line;}
function requirementResultRow(r){const row=document.createElement('div');row.className='requirement-row';const name=document.createElement('strong');name.textContent=r.productTemplate.name;row.append(name);
  if(r.warning){row.classList.add('warning');const warning=document.createElement('span');warning.textContent=`⚠ ${r.warning}`;row.append(warning);return row;}
  if(r.trackingMode==='PRESENCE'){row.classList.add(r.satisfied?'satisfied':'missing');const status=document.createElement('span');status.className=`requirement-status${r.satisfied?'':' missing'}`;status.textContent=r.satisfied?t("inventory.presenceUnknown"):t("inventory.missing");row.append(status);if(r.plannedUsageCount>0){const usage=document.createElement('small');usage.textContent=t("inventory.otherPlannedUses", {plannedUsageCount: r.plannedUsageCount, count: r.plannedUsageCount});row.append(usage);}return row;}
  const available=r.availableQuantity??0;const missing=r.missingQuantity??0;const reserved=Number(r.reservedQuantity??0);const needed=requirementAmount(r.displayRequiredQuantity??r.requiredQuantity,r.displayRequiredUnit??r.unit);if(reserved>0){row.append(requirementValue(t("inventory.required"),needed),requirementValue(t("inventory.onHand"),requirementAmount(r.physicalQuantity??0,r.unit)),requirementValue(t("inventory.reserved"),requirementAmount(r.reservedQuantity,r.unit)),requirementValue(t("inventory.available"),requirementAmount(available,r.unit)));}else{row.append(requirementValue(t("inventory.required"),needed),requirementValue(t("inventory.owned"),requirementAmount(r.physicalQuantity??0,r.unit)));}
  const status=document.createElement('span');if(r.satisfied){row.classList.add('satisfied');status.className='requirement-status';status.textContent=t("inventory.sufficient");}else{row.classList.add(Number(available)>0?'partial':'missing');status.className='requirement-status missing';status.textContent=t("inventory.missingAmount", {value1: requirementAmount(missing,r.unit)});}row.append(status);return row;}
function renderRequirementResults(calculation){const rows=calculation.requirements.map(requirementResultRow);const hasMissing=calculation.requirements.some(r=>!r.satisfied&&!r.warning);document.querySelector('#recipe-plan-requirements').replaceChildren(...rows);document.querySelector('#recipe-plan-results').hidden=false;document.querySelector('#add-recipe-missing').hidden=!hasMissing;}
async function calculateRecipePlan(){const payload=selectedRecipePayload();if(!payload.recipes.length){showMessage(document.querySelector('#recipe-plan-error'),t("mealPlan.validation.recipeRequired"));return;}const button=document.querySelector('#calculate-recipe-plan');button.disabled=true;try{renderRequirementResults(await jsonRequest(`${RECIPE_API}/calculate-requirements`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}));showMessage(document.querySelector('#recipe-plan-error'),'');}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
async function addRecipeMissing(){const button=document.querySelector('#add-recipe-missing');button.disabled=true;try{await jsonRequest(`${RECIPE_API}/add-missing-to-shopping-list`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(selectedRecipePayload())});closeRecipePlan();showToast(t("recipes.shortagesAdded"));}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
async function cookCurrentRecipe(){if(!currentRecipe)return;const button=document.querySelector('#cook-recipe');button.disabled=true;try{const result=await jsonRequest(`${RECIPE_API}/${currentRecipe.id}/cook`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({portions:recipePortions})});showToast(t("recipes.cooked", {name: currentRecipe.name}));const summary=document.querySelector('#cook-recipe-summary');summary.textContent=result.warnings.length?result.warnings.join(' · '):t("recipes.inventoryUpdated");summary.classList.toggle('error',result.warnings.length>0);summary.hidden=false;await loadInventory();}catch(error){showToast(t("recipes.cookFailed", {message: error.message}),'error');}finally{button.disabled=false;}}

function showRecipeSection(section){const plans=section==='plans',templates=section==='templates';document.querySelector('#recipe-library-panel').hidden=plans||templates;document.querySelector('#recipe-templates-panel').hidden=!templates;document.querySelector('#meal-plans-panel').hidden=!plans;document.querySelector('#show-recipe-library').classList.toggle('active',!plans&&!templates);document.querySelector('#show-recipe-templates').classList.toggle('active',templates);document.querySelector('#show-meal-plans').classList.toggle('active',plans);if(plans)loadMealPlans();if(templates)loadRecipeTemplates(document.querySelector('#recipe-template-catalog-search').value);}

function recipeTemplateCard(template){const button=document.createElement('button');button.type='button';button.className='recipe-card';const name=document.createElement('strong');name.textContent=template.name;const meta=document.createElement('span');meta.textContent=template.added?t("recipes.catalog.inLibrary"):(template.description||t("recipes.open"));button.append(name,meta);button.onclick=()=>openRecipeTemplate(template.id);return button;}
async function loadRecipeTemplates(query=''){const request=++recipeTemplateCatalogRequestId;const loading=document.querySelector('#recipe-templates-loading');loading.hidden=false;try{const suffix=query.trim()?`?query=${encodeURIComponent(query.trim())}`:'';const result=await jsonRequest(`${RECIPE_TEMPLATE_API}${suffix}`);if(request!==recipeTemplateCatalogRequestId)return;currentRecipeTemplates=result;document.querySelector('#recipe-template-list').replaceChildren(...result.map(recipeTemplateCard));document.querySelector('#recipe-templates-empty').hidden=result.length>0;}catch(error){if(request===recipeTemplateCatalogRequestId)showToast(t("recipes.catalog.loadFailed", {message: error.message}),'error');}finally{if(request===recipeTemplateCatalogRequestId)loading.hidden=true;}}
function renderRecipeTemplateDetail(){if(!currentRecipeTemplate)return;document.querySelector('#recipe-template-portions').textContent=t("common.portions", {count: recipeTemplatePortions});document.querySelector('#recipe-template-portions-down').disabled=recipeTemplatePortions===1;document.querySelector('#recipe-template-detail-ingredients').replaceChildren(...[...currentRecipeTemplate.ingredients].sort((a,b)=>a.sortOrder-b.sortOrder).map(ingredient=>{const row=document.createElement('div');row.className='recipe-ingredient-row';const text=document.createElement('div');const name=document.createElement('div');name.textContent=ingredient.productTemplate.name;const prep=document.createElement('small');prep.textContent=ingredient.preparation||'';text.append(name,prep);const quantity=ingredient.quantity;const amount=document.createElement('strong');amount.textContent=`${danishDecimal(quantity)} ${recipeUnitLabel(ingredient.unit,quantity)}`;row.append(text,amount);return row;}));document.querySelector('#recipe-template-detail-steps').replaceChildren(...[...currentRecipeTemplate.steps].sort((a,b)=>a.sortOrder-b.sortOrder).map(step=>{const item=document.createElement('li');if(step.type==='PROCESS'&&step.renderedProcess)item.append(renderProcessDetails(step));else item.textContent=step.instruction;return item;}));const add=document.querySelector('#add-recipe-template');add.textContent=currentRecipeTemplate.added?t("recipes.catalog.openCopy"):t("recipes.catalog.addToLibrary");}
function renderRecipeTemplateMeta(){const preparation=currentRecipeTemplate.preparationSteps||[];document.querySelector('#recipe-template-preparation-section').hidden=!preparation.length;document.querySelector('#recipe-template-detail-preparation').replaceChildren(...preparation.sort((a,b)=>a.sortOrder-b.sortOrder).map(value=>{const item=document.createElement('li');item.textContent=value.instruction;return item;}));const equipment=currentRecipeTemplate.equipment||[];document.querySelector('#recipe-template-equipment-section').hidden=!equipment.length;document.querySelector('#recipe-template-detail-equipment').replaceChildren(...equipment.map(value=>{const item=document.createElement('li');item.textContent=value;return item;}));}
async function loadRecipeTemplateDetail(id,portions){currentRecipeTemplate=await jsonRequest(`${RECIPE_TEMPLATE_API}/${id}?portions=${portions}`);renderRecipeTemplateDetail();renderRecipeTemplateMeta();renderCarbohydrates(currentRecipeTemplate.carbohydrates,document.querySelector('#recipe-template-carbohydrates'));renderUnknownCarbohydrates(currentRecipeTemplate.carbohydrates,document.querySelector('#recipe-template-carbohydrates'));}
async function openRecipeTemplate(id, initialPortions=2){try{recipeTemplatePortions=initialPortions;await loadRecipeTemplateDetail(id,recipeTemplatePortions);document.querySelector('#recipe-template-detail-title').textContent=currentRecipeTemplate.name;const description=document.querySelector('#recipe-template-detail-description');description.textContent=currentRecipeTemplate.description||'';description.hidden=!currentRecipeTemplate.description;showMessage(document.querySelector('#recipe-template-error'),'');document.querySelector('#recipe-template-detail-dialog').showModal();}catch(error){showToast(t("recipes.catalog.openFailed", {message: error.message}),'error');}}
function closeRecipeTemplate(){const dialog=document.querySelector('#recipe-template-detail-dialog');if(dialog.open)dialog.close();}
async function addRecipeTemplate(){if(!currentRecipeTemplate)return;const button=document.querySelector('#add-recipe-template');button.disabled=true;try{if(currentRecipeTemplate.added&&currentRecipeTemplate.userRecipeId){closeRecipeTemplate();showRecipeSection('recipes');await openRecipe(currentRecipeTemplate.userRecipeId);return;}const recipe=await jsonRequest(`${RECIPE_TEMPLATE_API}/${currentRecipeTemplate.id}/add-to-my-recipes`,{method:'POST'});currentRecipeTemplate.added=true;currentRecipeTemplate.userRecipeId=recipe.id;await Promise.all([loadRecipes(),loadRecipeTemplates(document.querySelector('#recipe-template-catalog-search').value)]);showToast(t("recipes.catalog.added", {name: recipe.name}));renderRecipeTemplateDetail();}catch(error){showMessage(document.querySelector('#recipe-template-error'),error.message);}finally{button.disabled=false;}}
function mealPlanCard(plan){const button=document.createElement('button');button.type='button';button.className='recipe-card';const name=document.createElement('strong');name.textContent=plan.name;const meta=document.createElement('span');meta.textContent=plan.completed?t("mealPlan.completedSummary", {length: plan.recipes.length}):t("mealPlan.recipeCount", {count: plan.recipes.length});button.append(name,meta);button.onclick=()=>openMealPlan(plan.id);return button;}
async function loadMealPlans(){const loading=document.querySelector('#meal-plans-loading');loading.hidden=false;try{currentMealPlans=await jsonRequest(MEAL_PLAN_API);document.querySelector('#meal-plan-list').replaceChildren(...currentMealPlans.map(mealPlanCard));document.querySelector('#meal-plans-empty').hidden=currentMealPlans.length>0;}catch(error){showToast(t("mealPlan.loadFailed", {message: error.message}),'error');}finally{loading.hidden=true;}}
async function saveCurrentMealPlan(event){event.preventDefault();const payload=selectedRecipePayload();const name=document.querySelector('#meal-plan-name').value.trim();if(!name||!payload.recipes.length){showMessage(document.querySelector('#recipe-plan-error'),!name?t("mealPlan.validation.nameRequired"):t("mealPlan.validation.recipeRequired"));return;}const button=event.currentTarget.querySelector('button[type="submit"]');button.disabled=true;try{await jsonRequest(MEAL_PLAN_API,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,recipes:payload.recipes})});closeRecipePlan();showRecipeSection('plans');showToast(t("common.itemSaved", {name: name}));}catch(error){showMessage(document.querySelector('#recipe-plan-error'),error.message);}finally{button.disabled=false;}}
function plannedStatus(status){return {PLANNED:t("mealPlan.status.planned"),COOKED:'✓ Lavet',SKIPPED:t("mealPlan.status.skipped")}[status]||status;}
function plannedRecipeRow(item){const row=document.createElement('article');row.className='planned-recipe-row';const main=document.createElement('div');main.className='planned-recipe-main';const text=document.createElement('div');const name=document.createElement('strong');name.textContent=item.recipe?.name||item.recipeName;const portions=document.createElement('div');portions.className='product-meta';portions.textContent=t("common.portions", {count: item.portions});text.append(name,portions);const status=document.createElement('span');status.className='planned-recipe-status';status.textContent=plannedStatus(item.status);main.append(text,status);if(item.recipe){main.tabIndex=0;main.setAttribute('role','button');const open=()=>openRecipe(item.recipe.id,item.portions);main.onclick=open;main.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();open();}}}row.append(main);
  const actions=document.createElement('div');actions.className='planned-recipe-actions';if(item.status==='PLANNED'){const down=document.createElement('button');down.type='button';down.textContent=t("mealPlan.decreasePortions");down.disabled=item.portions===1;down.onclick=()=>changePlannedPortions(item,item.portions-1);const up=document.createElement('button');up.type='button';up.textContent=t("mealPlan.increasePortions");up.onclick=()=>changePlannedPortions(item,item.portions+1);const cook=document.createElement('button');cook.type='button';cook.textContent=t("mealPlan.markCooked");cook.onclick=()=>cookPlanned(item,cook);const skip=document.createElement('button');skip.type='button';skip.textContent=t("mealPlan.skip");skip.onclick=()=>togglePlannedSkip(item);actions.append(down,up,cook,skip);}else if(item.status==='SKIPPED'&&item.recipe){const undo=document.createElement('button');undo.type='button';undo.textContent=t("mealPlan.replan");undo.onclick=()=>togglePlannedSkip(item);actions.append(undo);}if(item.status!=='COOKED'){const remove=document.createElement('button');remove.type='button';remove.textContent=t("common.remove");remove.onclick=()=>removePlannedRecipe(item);actions.append(remove);}row.append(actions);return row;}
function renderMealPlan(plan){currentMealPlan=plan;document.querySelector('#meal-plan-detail-title').textContent=plan.name;document.querySelector('#meal-plan-detail-summary').textContent=plan.completed?t("mealPlan.completedSummary", {length: plan.recipes.length}):t("mealPlan.plannedCount", {length: plan.recipes.filter(r=>r.status==='PLANNED').length});document.querySelector('#meal-plan-recipes').replaceChildren(...[...plan.recipes].sort((a,b)=>a.sortOrder-b.sortOrder).map(plannedRecipeRow));document.querySelector('#meal-plan-results').hidden=true;document.querySelector('#delete-meal-plan-confirmation').hidden=true;}
async function openMealPlan(id){try{renderMealPlan(await jsonRequest(`${MEAL_PLAN_API}/${id}`));document.querySelector('#meal-plan-detail-dialog').showModal();}catch(error){showToast(t("mealPlan.openFailed", {message: error.message}),'error');}}
function closeMealPlan(){const d=document.querySelector('#meal-plan-detail-dialog');if(d.open)d.close();}
async function refreshMealPlan(){renderMealPlan(await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}`));await loadMealPlans();}
async function changePlannedPortions(item,portions){try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({portions,sortOrder:item.sortOrder})});await refreshMealPlan();}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function cookPlanned(item,button){button.disabled=true;try{const result=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}/cook`,{method:'POST'});showToast(t("mealPlan.recipeCooked", {value1: item.recipe?.name||item.recipeName}));if(result.warnings.length)showMessage(document.querySelector('#meal-plan-error'),result.warnings.join(' · '));await Promise.all([refreshMealPlan(),loadInventory()]);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function togglePlannedSkip(item){try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}/skip`,{method:'POST'});await refreshMealPlan();}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function removePlannedRecipe(item){try{const plan=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/recipes/${item.id}`,{method:'DELETE'});renderMealPlan(plan);await loadMealPlans();showToast(t("mealPlan.recipeRemoved", {value1: item.recipe?.name||item.recipeName}));}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}
async function calculateMealPlan(){const button=document.querySelector('#meal-plan-requirements');button.disabled=true;try{const result=await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/requirements`);const rows=result.requirements.map(requirementResultRow);document.querySelector('#meal-plan-requirement-list').replaceChildren(...rows);document.querySelector('#meal-plan-results').hidden=false;document.querySelector('#meal-plan-add-missing').hidden=!result.requirements.some(r=>!r.satisfied&&!r.warning);}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function addMealPlanMissing(){const button=document.querySelector('#meal-plan-add-missing');button.disabled=true;try{await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}/add-missing-to-shopping-list`,{method:'POST'});showToast(t("mealPlan.shortagesEnsured"));}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}finally{button.disabled=false;}}
async function deleteMealPlan(){try{const name=currentMealPlan.name;await jsonRequest(`${MEAL_PLAN_API}/${currentMealPlan.id}`,{method:'DELETE'});closeMealPlan();await loadMealPlans();showToast(t("common.itemDeleted", {name: name}));}catch(error){showMessage(document.querySelector('#meal-plan-error'),error.message);}}

const equipmentTypeLabels={STOVE:t("equipment.types.stove"),OVEN:t("equipment.types.oven"),POT:t("equipment.types.pot"),PAN:t("equipment.types.pan"),AIR_FRYER:t("equipment.types.airFryer"),THERMOMETER:t("equipment.types.cookingThermometer"),MICROWAVE:t("equipment.types.microwave")};
const heatSourceLabels={INDUCTION:t("equipment.heatSources.induction"),CERAMIC:t("equipment.heatSources.ceramic"),ELECTRIC:t("equipment.heatSources.electric"),GAS:t("equipment.heatSources.gas"),OTHER:t("common.other")};
const ovenModeLabels={CONVENTIONAL:t("equipment.ovenModes.conventional"),FAN:t("equipment.ovenModes.fan"),GRILL:t("equipment.ovenModes.grill")};
function liters(ml){return t("units.liters", {value1: formatQuantity(ml/1000)});}
function centimeters(mm){return t("units.centimeters", {value1: formatQuantity(mm/10)});}
function equipmentSummary(e){switch(e.equipmentType){case'STOVE':return `${heatSourceLabels[e.heatSource]||''}${e.minimumLevel!=null?t("equipment.levelRange", {minimumLevel: e.minimumLevel, maximumLevel: e.maximumLevel}):''}`;case'OVEN':return (e.ovenModes||[]).map(x=>ovenModeLabels[x]).join(' / ')||t("equipment.types.oven");case'POT':return e.capacityMl?liters(e.capacityMl):t("equipment.types.pot");case'PAN':return [e.diameterMm?centimeters(e.diameterMm):'',e.nonStick?'non-stick':''].filter(Boolean).join(' · ')||t("equipment.types.pan");case'AIR_FRYER':return e.capacityMl?liters(e.capacityMl):t("equipment.types.airFryer");case'THERMOMETER':return {INSTANT_READ:t("equipment.thermometerTypes.instant"),PROBE:t("equipment.thermometerTypes.probe"),OTHER:t("equipment.thermometerTypes.other")}[e.thermometerType]||t("equipment.types.thermometer");case'MICROWAVE':return e.maxPowerWatts?t("equipment.powerWatts", {maxPowerWatts: e.maxPowerWatts}):t("equipment.types.microwave");}}
function equipmentCard(e){const button=document.createElement('button');button.type='button';button.className='equipment-card';const text=document.createElement('span');const name=document.createElement('strong');name.textContent=e.name;const meta=document.createElement('span');meta.className='product-meta';meta.textContent=equipmentSummary(e);text.append(name,meta);const badge=document.createElement('span');badge.className='preferred-badge';badge.textContent=e.preferred?t("equipment.preferred"):'›';button.append(text,badge);button.onclick=()=>openKitchenEquipment(e);return button;}
async function loadKitchenEquipment(){const loading=document.querySelector('#kitchen-loading');loading.hidden=false;try{currentKitchenEquipment=await jsonRequest(KITCHEN_EQUIPMENT_API);document.querySelector('#kitchen-equipment-list').replaceChildren(...currentKitchenEquipment.map(equipmentCard));document.querySelector('#kitchen-empty').hidden=currentKitchenEquipment.length>0;}catch(error){showToast(t("equipment.loadFailed", {message: error.message}),'error');}finally{loading.hidden=true;}}
function defaultEquipmentName(type){return equipmentTypeLabels[type];}
function setEquipmentFields(type){document.querySelectorAll('[data-equipment-fields]').forEach(section=>section.hidden=section.dataset.equipmentFields!==type);document.querySelector('#kitchen-preferred-row').hidden=!['STOVE','OVEN'].includes(type);}
function suggestedHeatMappings(){const source=document.querySelector('#stove-heat-source').value,minText=document.querySelector('#stove-minimum-level').value,maxText=document.querySelector('#stove-maximum-level').value,min=Number(minText),max=Number(maxText);const gas=source==='GAS'&&(!minText||!maxText);const values=gas?[t("equipment.heat.low"),t("equipment.heat.mediumLow"),t("equipment.heat.medium"),t("equipment.heat.mediumHigh"),t("equipment.heat.high"),t("equipment.heat.maximum")]:[.15,.30,.50,.70,.85,1].map(p=>String(Math.max(min,Math.min(max,Math.round(min+(max-min)*p)))));document.querySelectorAll('[data-heat]').forEach((input,index)=>input.value=values[index]);}
function resetKitchenEquipmentForm(){const form=document.querySelector('#kitchen-equipment-form');form.reset();editingKitchenEquipment=null;document.querySelector('#kitchen-equipment-id').value='';document.querySelector('#kitchen-equipment-type').disabled=false;document.querySelector('#kitchen-equipment-type').value='STOVE';document.querySelector('#kitchen-equipment-name').value=t("equipment.types.stove");document.querySelector('#stove-minimum-level').value='1';document.querySelector('#stove-maximum-level').value='9';document.querySelector('#delete-kitchen-equipment').hidden=true;document.querySelector('#delete-kitchen-equipment-confirmation').hidden=true;showMessage(document.querySelector('#kitchen-equipment-error'),'');setEquipmentFields('STOVE');suggestedHeatMappings();}
function openKitchenEquipment(e=null){resetKitchenEquipmentForm();editingKitchenEquipment=e;if(e){document.querySelector('#kitchen-equipment-title').textContent=t("equipment.edit");document.querySelector('#kitchen-equipment-id').value=e.id;document.querySelector('#kitchen-equipment-type').value=e.equipmentType;document.querySelector('#kitchen-equipment-type').disabled=true;document.querySelector('#kitchen-equipment-name').value=e.name;document.querySelector('#kitchen-equipment-preferred').checked=e.preferred;setEquipmentFields(e.equipmentType);document.querySelector('#delete-kitchen-equipment').hidden=false;if(e.equipmentType==='STOVE'){document.querySelector('#stove-heat-source').value=e.heatSource;document.querySelector('#stove-minimum-level').value=e.minimumLevel??'';document.querySelector('#stove-maximum-level').value=e.maximumLevel??'';document.querySelectorAll('[data-heat]').forEach(input=>input.value=e.heatMappings?.[input.dataset.heat]||'');}if(e.equipmentType==='OVEN'){document.querySelectorAll('[data-oven-mode]').forEach(input=>input.checked=(e.ovenModes||[]).includes(input.dataset.ovenMode));document.querySelector('#oven-min-temperature').value=e.minimumTemperatureCelsius??'';document.querySelector('#oven-max-temperature').value=e.maximumTemperatureCelsius??'';}if(e.equipmentType==='POT')document.querySelector('#pot-capacity').value=e.capacityMl??'';if(e.equipmentType==='PAN'){document.querySelector('#pan-diameter').value=e.diameterMm??'';document.querySelector('#pan-non-stick').checked=Boolean(e.nonStick);}if(e.equipmentType==='AIR_FRYER'){document.querySelector('#air-fryer-capacity').value=e.capacityMl??'';document.querySelector('#air-fryer-min-temperature').value=e.minimumTemperatureCelsius??'';document.querySelector('#air-fryer-max-temperature').value=e.maximumTemperatureCelsius??'';}if(e.equipmentType==='THERMOMETER')document.querySelector('#thermometer-type').value=e.thermometerType||'OTHER';if(e.equipmentType==='MICROWAVE')document.querySelector('#microwave-power').value=e.maxPowerWatts??'';}else document.querySelector('#kitchen-equipment-title').textContent=t("equipment.add");document.querySelector('#kitchen-equipment-dialog').showModal();}
function optionalNumber(selector){const value=document.querySelector(selector).value;return value===''?null:Number(value);}
function kitchenEquipmentPayload(){const type=document.querySelector('#kitchen-equipment-type').value,payload={equipmentType:type,name:document.querySelector('#kitchen-equipment-name').value.trim(),active:true,preferred:document.querySelector('#kitchen-equipment-preferred').checked};if(type==='STOVE'){payload.heatSource=document.querySelector('#stove-heat-source').value;payload.minimumLevel=optionalNumber('#stove-minimum-level');payload.maximumLevel=optionalNumber('#stove-maximum-level');payload.heatMappings=Object.fromEntries([...document.querySelectorAll('[data-heat]')].map(i=>[i.dataset.heat,i.value.trim()]));}if(type==='OVEN'){payload.ovenModes=[...document.querySelectorAll('[data-oven-mode]:checked')].map(i=>i.dataset.ovenMode);payload.minimumTemperatureCelsius=optionalNumber('#oven-min-temperature');payload.maximumTemperatureCelsius=optionalNumber('#oven-max-temperature');}if(type==='POT')payload.capacityMl=optionalNumber('#pot-capacity');if(type==='PAN'){payload.diameterMm=optionalNumber('#pan-diameter');payload.nonStick=document.querySelector('#pan-non-stick').checked;}if(type==='AIR_FRYER'){payload.capacityMl=optionalNumber('#air-fryer-capacity');payload.minimumTemperatureCelsius=optionalNumber('#air-fryer-min-temperature');payload.maximumTemperatureCelsius=optionalNumber('#air-fryer-max-temperature');}if(type==='THERMOMETER')payload.thermometerType=document.querySelector('#thermometer-type').value;if(type==='MICROWAVE')payload.maxPowerWatts=optionalNumber('#microwave-power');return payload;}
async function saveKitchenEquipment(event){event.preventDefault();const button=event.currentTarget.querySelector('button[type="submit"]');button.disabled=true;try{const id=document.querySelector('#kitchen-equipment-id').value;await jsonRequest(id?`${KITCHEN_EQUIPMENT_API}/${id}`:KITCHEN_EQUIPMENT_API,{method:id?'PATCH':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(kitchenEquipmentPayload())});document.querySelector('#kitchen-equipment-dialog').close();await loadKitchenEquipment();showToast(id?t("equipment.updated"):t("equipment.added"));}catch(error){showMessage(document.querySelector('#kitchen-equipment-error'),error.message);}finally{button.disabled=false;}}
async function deleteKitchenEquipment(){if(!editingKitchenEquipment)return;try{await jsonRequest(`${KITCHEN_EQUIPMENT_API}/${editingKitchenEquipment.id}`,{method:'DELETE'});document.querySelector('#kitchen-equipment-dialog').close();await loadKitchenEquipment();showToast(t("equipment.deleted"));}catch(error){showMessage(document.querySelector('#kitchen-equipment-error'),error.message);}}

loginForm.addEventListener('submit', authenticate);
registerForm.addEventListener('submit', register);
loginTab.addEventListener('click', () => { showAuthMode('login'); document.querySelector('#login-username').focus(); });
registerTab.addEventListener('click', () => { showAuthMode('register'); document.querySelector('#register-username').focus(); });
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
document.querySelector('#show-ai').addEventListener('click', () => showView('ai'));
document.querySelector('#more-ai').addEventListener('click', () => showView('ai'));
let nutritionEntries=[],nutritionCoverage=[],pendingNutritionTemplateId=null,currentNutritionMatch=null;const selectedNutritionMappings=new Map();
function nutritionStatusText(entry){return entry.status==='MISSING'?t("nutrition.status.missing"):entry.status==='KNOWN_ZERO'?t("nutrition.status.knownZero"):t("nutrition.status.available");}
function dtuMatchText(entry){if(entry.dtuMapping?.status==='REQUIRES_REVIEW')return t("nutrition.dtu.changedFood", {danishName: entry.dtuMapping.food.danishName});if(entry.dtuMapping?.status==='APPROVED')return t("nutrition.dtu.matchedFood", {danishName: entry.dtuMapping.food.danishName});const suggestion=entry.dtuSuggestion,candidate=suggestion?.candidates?.[0];if(suggestion?.resolution==='AUTO_EQUIVALENT_CARBOHYDRATE')return t("nutrition.dtu.equivalentCandidates", {value1: danishDecimal(suggestion.representative.carbohydrateGrams), equivalentCandidateCount: suggestion.equivalentCandidateCount});if(!candidate)return t("nutrition.dtu.noSuggestion");if(suggestion.classification==='REVIEW_REQUIRED'&&suggestion.candidates.length>1)return t("nutrition.dtu.candidateCount", {length: suggestion.candidates.length});return t("nutrition.dtu.matchedFood", {danishName: candidate.food.danishName});}
function nutritionQueueMatches(entry,queue){if(queue==='ALL')return true;if(queue==='HAS_DATA')return entry.dtuMapping?.status==='APPROVED'||entry.status!=='MISSING';if(queue==='AUTO')return entry.dtuMapping?.status!=='APPROVED'&&entry.dtuSuggestion?.resolution==='AUTO_EQUIVALENT_CARBOHYDRATE';if(queue==='READY')return entry.dtuMapping?.status!=='APPROVED'&&entry.dtuSuggestion?.resolution!=='AUTO_EQUIVALENT_CARBOHYDRATE'&&['EXACT','HIGH_CONFIDENCE'].includes(entry.dtuSuggestion?.classification);if(queue==='REVIEW_REQUIRED')return entry.dtuMapping?.status==='REQUIRES_REVIEW'||entry.dtuSuggestion?.resolution!=='AUTO_EQUIVALENT_CARBOHYDRATE'&&entry.dtuSuggestion?.classification==='REVIEW_REQUIRED';return entry.dtuSuggestion?.classification==='NO_MATCH';}
function nutritionQueue(entry){if(nutritionQueueMatches(entry,'AUTO'))return'AUTO';if(nutritionQueueMatches(entry,'READY'))return'READY';if(nutritionQueueMatches(entry,'REVIEW_REQUIRED'))return'REVIEW_REQUIRED';if(nutritionQueueMatches(entry,'NO_MATCH'))return'NO_MATCH';return'HAS_DATA';}
function visibleNutritionEntries(){const queue=document.querySelector('#nutrition-dtu-status').value,status=document.querySelector('#nutrition-status').value,usage=document.querySelector('#nutrition-recipe-usage').value,search=document.querySelector('#nutrition-search').value.trim().toLocaleLowerCase('da-DK');return nutritionEntries.filter(entry=>nutritionQueueMatches(entry,queue)&&(status==='ALL'||status==='MISSING'&&entry.status==='MISSING'||status==='KNOWN'&&entry.status!=='MISSING')&&(usage==='ALL'||entry.recipeTemplateUsageCount>0&&(usage==='USED'||entry.status==='MISSING'))&&(!search||[entry.name,entry.key,...(entry.aliases||[])].some(value=>value.toLocaleLowerCase('da-DK').includes(search))));}
function renderNutritionCounts(){const count=queue=>nutritionEntries.filter(entry=>nutritionQueueMatches(entry,queue)).length;document.querySelector('#nutrition-count-data').textContent=count('HAS_DATA');document.querySelector('#nutrition-count-auto').textContent=count('AUTO');document.querySelector('#nutrition-count-ready').textContent=count('READY');document.querySelector('#nutrition-count-review').textContent=count('REVIEW_REQUIRED');document.querySelector('#nutrition-count-none').textContent=count('NO_MATCH');document.querySelector('#nutrition-safe-tools').hidden=document.querySelector('#nutrition-dtu-status').value!=='AUTO'||count('AUTO')===0;document.querySelectorAll('[data-nutrition-queue]').forEach(button=>button.classList.toggle('active',button.dataset.nutritionQueue===document.querySelector('#nutrition-dtu-status').value));}
function renderNutritionAdmin(){const queue=document.querySelector('#nutrition-dtu-status').value;const entries=visibleNutritionEntries();document.querySelector('#nutrition-review-tools').hidden=queue!=='READY';const rows=entries.map(entry=>{const row=document.createElement('article');row.className='nutrition-row';row.dataset.productTemplateId=entry.productTemplateId;const product=document.createElement('div');product.className='nutrition-product';const suggestion=entry.dtuSuggestion,candidate=suggestion?.candidates?.length===1?suggestion.candidates[0]:null;const selectable=nutritionQueue(entry)==='READY'&&candidate;const check=document.createElement('input');check.type='checkbox';check.setAttribute('aria-label',t("nutrition.dtu.selectMatch", {name: entry.name}));check.hidden=!selectable;check.checked=selectedNutritionMappings.has(entry.productTemplateId);check.onchange=()=>{if(check.checked)selectedNutritionMappings.set(entry.productTemplateId,candidate.food);else selectedNutritionMappings.delete(entry.productTemplateId);renderNutritionBulkActions();};const name=document.createElement('div');name.className='nutrition-product-name';const strong=document.createElement('strong');strong.textContent=entry.name;const key=document.createElement('small');key.textContent=entry.key;name.append(strong,key);if(entry.aliases?.length){const aliases=document.createElement('small');aliases.className='nutrition-aliases';aliases.textContent=t("nutrition.alias", {value1: entry.aliases.join(', ')});name.append(aliases);}product.append(check,name);const status=document.createElement('div');status.className='nutrition-cell';status.dataset.label=t("nutrition.status");const badge=document.createElement('span');badge.className=`nutrition-status-badge ${entry.status==='MISSING'?'missing':''}`;badge.textContent=nutritionStatusText(entry);status.append(badge);const value=document.createElement('div');value.className='nutrition-cell';value.dataset.label=t("nutrition.carbohydrates");value.textContent=entry.nutrition?t("nutrition.amount", {value1: danishDecimal(entry.nutrition.carbohydrateGrams), value2: danishDecimal(entry.nutrition.basisQuantity), value3: recipeUnitLabels[entry.nutrition.basisUnit]}):'—';const source=document.createElement('div');source.className='nutrition-cell';source.dataset.label=t("nutrition.source");source.textContent=entry.nutrition?.provider||entry.nutrition?.source||'—';const match=document.createElement('div');match.className='nutrition-cell';match.dataset.label=t("nutrition.dtu.match");match.textContent=dtuMatchText(entry);if(candidate){const detail=document.createElement('small');detail.textContent=t("nutrition.dtu.sourceDetails", {foodId: candidate.food.foodId, value2: danishDecimal(candidate.food.carbohydrateGrams), value3: suggestion.classification==='EXACT'?t("nutrition.confidence.exact"):t("nutrition.confidence.high")});match.append(detail);}else if(suggestion?.reason){const reason=document.createElement('small');reason.textContent=suggestion.reason;match.append(reason);}if(entry.dtuMapping?.status==='REQUIRES_REVIEW'){const note=document.createElement('small');note.textContent=t("nutrition.dtu.changedValues", {value1: danishDecimal(entry.dtuMapping.approvedCarbohydrateGrams), value2: danishDecimal(entry.dtuMapping.food.carbohydrateGrams)});match.append(note);}const actions=document.createElement('div');actions.className='nutrition-row-actions';const edit=document.createElement('button');edit.type='button';edit.className='secondary-button';edit.textContent=t("nutrition.edit");edit.onclick=()=>openNutritionEdit(entry);if(selectable){const approve=document.createElement('button');approve.type='button';approve.className='primary-button';approve.textContent=t("nutrition.dtu.approve");approve.onclick=()=>approveDtuFood(entry,candidate.food);const change=document.createElement('button');change.type='button';change.className='secondary-button';change.textContent=t("nutrition.dtu.change");change.onclick=()=>openNutritionMatch(entry);actions.append(edit,approve,change);}else{const choose=document.createElement('button');choose.type='button';choose.className='secondary-button';choose.textContent=nutritionQueue(entry)==='NO_MATCH'?t("nutrition.dtu.search"):t("nutrition.review");choose.onclick=()=>openNutritionMatch(entry);actions.append(edit,choose);}row.append(product,status,value,source,match,actions);return row;});document.querySelector('#nutrition-admin-list').replaceChildren(...rows);renderNutritionCounts();renderNutritionBulkActions();updateSelectAllNutrition();if(pendingNutritionTemplateId){const target=document.querySelector(`[data-product-template-id="${pendingNutritionTemplateId}"]`);target?.scrollIntoView({block:'center'});target?.classList.add('highlight');pendingNutritionTemplateId=null;}}
async function loadNutritionAdmin(){[nutritionEntries,nutritionCoverage]=await Promise.all([jsonRequest(`${NUTRITION_ADMIN_API}?status=ALL&search=`),jsonRequest('/v1/admin/recipe-template-nutrition-coverage')]);renderNutritionCoverage();renderNutritionAdmin();}
function renderNutritionCoverage(){const complete=nutritionCoverage.filter(value=>value.coveredIngredients===value.totalIngredients).length,few=nutritionCoverage.filter(value=>value.missing.length>0&&value.missing.length<=2).length,many=nutritionCoverage.filter(value=>value.missing.length>=5).length;document.querySelector('#nutrition-coverage-summary').textContent=t("nutrition.coverage.summary", {complete: complete, length: nutritionCoverage.length, few: few, many: many});const rows=nutritionCoverage.filter(value=>value.missing.length).map(value=>{const row=document.createElement('div');row.className='nutrition-coverage-row';const text=document.createElement('span');const title=document.createElement('strong');title.textContent=value.name;const detail=document.createElement('small');detail.textContent=`${value.coveredIngredients}/${value.totalIngredients} dækket · mangler: ${value.missing.map(item=>item.name).join(', ')}`;text.append(title,detail);const button=document.createElement('button');button.type='button';button.className='secondary-button compact-button';button.textContent=t("nutrition.coverage.resolveMissing");button.onclick=()=>{document.querySelector('#nutrition-recipe-usage').value='USED_MISSING';document.querySelector('#nutrition-status').value='MISSING';renderNutritionAdmin();document.querySelector('#nutrition-admin-list').scrollIntoView({behavior:'smooth'});};row.append(text,button);return row;});document.querySelector('#nutrition-coverage-list').replaceChildren(...rows);}
document.querySelector('#nutrition-recipe-usage').addEventListener('change',renderNutritionAdmin);
function renderNutritionBulkActions(){const count=selectedNutritionMappings.size;document.querySelector('#nutrition-bulk-actions').hidden=count===0;document.querySelector('#nutrition-selected-count').textContent=count===1?t("nutrition.dtu.selectedMatch"):t("nutrition.dtu.selectedMatches", {count: count});}
async function approveDtuFood(entry,food){await jsonRequest(`${NUTRITION_ADMIN_API}/${entry.productTemplateId}/dtu-mapping`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({datasetVersion:food.datasetVersion,foodId:food.foodId})});selectedNutritionMappings.delete(entry.productTemplateId);document.querySelector('#nutrition-match-dialog').close();await loadNutritionAdmin();}
function requestApproveSelectedDtu(){const count=selectedNutritionMappings.size;if(!count)return;document.querySelector('#nutrition-bulk-confirm-text').textContent=t("nutrition.dtu.confirmSelectedCount", {count: count});document.querySelector('#nutrition-bulk-confirm-dialog').showModal();}
async function approveSelectedDtu(){const readyIds=new Set(nutritionEntries.filter(entry=>nutritionQueue(entry)==='READY').map(entry=>entry.productTemplateId));const values=[...selectedNutritionMappings].filter(([id])=>readyIds.has(id)).map(([productTemplateId,food])=>({productTemplateId,datasetVersion:food.datasetVersion,foodId:food.foodId}));if(!values.length)return;await jsonRequest(`${NUTRITION_ADMIN_API}/dtu-mappings`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});selectedNutritionMappings.clear();document.querySelector('#nutrition-bulk-confirm-dialog').close();await loadNutritionAdmin();}
async function approveSafeDtu(){await jsonRequest(`${NUTRITION_ADMIN_API}/dtu-mappings/approve-safe`,{method:'POST'});await loadNutritionAdmin();}
function updateSelectAllNutrition(){const ready=visibleNutritionEntries().filter(entry=>nutritionQueue(entry)==='READY'&&entry.dtuSuggestion?.candidates?.length===1);const control=document.querySelector('#select-all-nutrition');control.checked=ready.length>0&&ready.every(entry=>selectedNutritionMappings.has(entry.productTemplateId));control.indeterminate=ready.some(entry=>selectedNutritionMappings.has(entry.productTemplateId))&&!control.checked;}
function selectAllNutrition(event){visibleNutritionEntries().filter(entry=>nutritionQueue(entry)==='READY'&&entry.dtuSuggestion?.candidates?.length===1).forEach(entry=>{if(event.target.checked)selectedNutritionMappings.set(entry.productTemplateId,entry.dtuSuggestion.candidates[0].food);else selectedNutritionMappings.delete(entry.productTemplateId);});renderNutritionAdmin();}
function renderDtuResults(foods){const host=document.querySelector('#nutrition-dtu-results');host.replaceChildren(...foods.map(food=>{const row=document.createElement('div');row.className='dtu-result';const text=document.createElement('span');const name=document.createElement('strong');name.textContent=food.danishName;const meta=document.createElement('small');meta.textContent=t("nutrition.dtu.candidateDetails", {foodId: food.foodId, value2: danishDecimal(food.carbohydrateGrams), datasetVersion: food.datasetVersion});text.append(name,meta);const approve=document.createElement('button');approve.type='button';approve.className='primary-button';approve.textContent=t("nutrition.dtu.approve");approve.onclick=()=>approveDtuFood(currentNutritionMatch,food);row.append(text,approve);return row;}));}
function openNutritionMatch(entry){currentNutritionMatch=entry;document.querySelector('#nutrition-match-title').textContent=t("nutrition.dtu.matchTitle", {name: entry.name});document.querySelector('#nutrition-dtu-search').value='';renderDtuResults((entry.dtuSuggestion?.candidates||[]).map(value=>value.food));document.querySelector('#nutrition-match-dialog').showModal();}
async function searchDtuCatalog(){const search=encodeURIComponent(document.querySelector('#nutrition-dtu-search').value.trim());renderDtuResults(await jsonRequest(`/v1/admin/dtu-catalog?search=${search}`));}
function openNutritionEdit(entry){const n=entry.nutrition||{};document.querySelector('#nutrition-template-id').value=entry.productTemplateId;document.querySelector('#nutrition-edit-title').textContent=t("nutrition.editTitle", {name: entry.name});document.querySelector('#nutrition-grams').value=n.carbohydrateGrams??'';document.querySelector('#nutrition-basis').value=n.basisQuantity??100;document.querySelector('#nutrition-basis-unit').value=n.basisUnit||'GRAM';document.querySelector('#nutrition-source').value=n.source||'';document.querySelector('#nutrition-provider').value=n.provider||'';document.querySelector('#nutrition-external-id').value=n.externalFoodId||'';document.querySelector('#nutrition-source-version').value=n.sourceVersion||'';document.querySelector('#nutrition-source-url').value=n.sourceUrl||'';document.querySelector('#nutrition-note').value=n.note||'';document.querySelector('#nutrition-edit-dialog').showModal();}
async function saveNutrition(event){event.preventDefault();const value=id=>document.querySelector(id).value.trim();try{await jsonRequest(`${NUTRITION_ADMIN_API}/${value('#nutrition-template-id')}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({carbohydrateGrams:Number(value('#nutrition-grams').replace(',','.')),basisQuantity:Number(value('#nutrition-basis').replace(',','.')),basisUnit:value('#nutrition-basis-unit'),source:value('#nutrition-source'),provider:value('#nutrition-provider')||null,externalFoodId:value('#nutrition-external-id')||null,sourceVersion:value('#nutrition-source-version')||null,sourceUrl:value('#nutrition-source-url')||null,note:value('#nutrition-note')||null})});document.querySelector('#nutrition-edit-dialog').close();await loadNutritionAdmin();}catch(error){showMessage(document.querySelector('#nutrition-edit-error'),error.message);}}
document.querySelector('#nutrition-status').addEventListener('change',renderNutritionAdmin);document.querySelector('#nutrition-dtu-status').addEventListener('change',renderNutritionAdmin);document.querySelector('#nutrition-search').addEventListener('input',renderNutritionAdmin);document.querySelectorAll('[data-nutrition-queue]').forEach(button=>button.addEventListener('click',()=>{document.querySelector('#nutrition-dtu-status').value=button.dataset.nutritionQueue;renderNutritionAdmin();}));document.querySelector('#select-all-nutrition').addEventListener('change',selectAllNutrition);document.querySelector('#nutrition-edit-form').addEventListener('submit',saveNutrition);document.querySelector('#close-nutrition-edit').addEventListener('click',()=>document.querySelector('#nutrition-edit-dialog').close());document.querySelector('#cancel-nutrition-edit').addEventListener('click',()=>document.querySelector('#nutrition-edit-dialog').close());document.querySelector('#approve-selected-dtu').addEventListener('click',requestApproveSelectedDtu);document.querySelector('#confirm-nutrition-bulk').addEventListener('click',approveSelectedDtu);document.querySelector('#close-nutrition-bulk-confirm').addEventListener('click',()=>document.querySelector('#nutrition-bulk-confirm-dialog').close());document.querySelector('#cancel-nutrition-bulk-confirm').addEventListener('click',()=>document.querySelector('#nutrition-bulk-confirm-dialog').close());document.querySelector('#nutrition-dtu-search').addEventListener('input',searchDtuCatalog);document.querySelector('#close-nutrition-match').addEventListener('click',()=>document.querySelector('#nutrition-match-dialog').close());document.querySelector('#import-dtu-catalog').addEventListener('click',async()=>{await jsonRequest('/v1/admin/dtu-catalog/import-bundled',{method:'POST'});await loadNutritionAdmin();});
document.querySelector('#approve-safe-dtu').addEventListener('click',approveSafeDtu);
document.querySelector('#more-products').addEventListener('click', () => showView('products'));
document.querySelector('#more-kitchen').addEventListener('click', () => showView('kitchen'));
document.querySelector('#more-nutrition').addEventListener('click', () => showView('nutrition-admin'));
document.querySelector('#close-nutrition-admin').addEventListener('click', () => showView('more'));
document.querySelector('#more-logout').addEventListener('click', logout);
document.querySelector('#close-inventory-reservations').addEventListener('click',()=>document.querySelector('#inventory-reservation-dialog').close());
document.querySelector('#show-kitchen').addEventListener('click', () => showView('kitchen'));
document.querySelector('#show-nutrition').addEventListener('click', () => showView('nutrition-admin'));
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
document.querySelector('#copy-inventory').addEventListener('click', copyInventory);
document.querySelector('#open-shopping-text').addEventListener('click', openShoppingText);
document.querySelector('#close-shopping-text').addEventListener('click', closeShoppingText);
document.querySelector('#cancel-shopping-text').addEventListener('click', closeShoppingText);
document.querySelector('#shopping-text-dialog').addEventListener('cancel', event => { event.preventDefault(); closeShoppingText(); });
document.querySelector('#shopping-text-input').addEventListener('input', invalidateShoppingText);
document.querySelector('#shopping-text-form').addEventListener('submit', event => { event.preventDefault(); submitShoppingText(); });
document.querySelector('#confirm-shopping-text').addEventListener('click', () => submitShoppingText(true));
document.querySelector('#purchase-edit-shopping').addEventListener('click', purchaseEditedShoppingItem);
document.querySelector('#edit-shopping-minus').addEventListener('click', () => changeShoppingEditorQuantity(-1));
document.querySelector('#edit-shopping-plus').addEventListener('click', () => changeShoppingEditorQuantity(1));
document.querySelector('#edit-shopping-dialog').addEventListener('cancel', event => { event.preventDefault(); closeShoppingEditor(); });
document.querySelector('#edit-shopping-quantity').addEventListener('input', event =>
  updateConversion(event.target, event.target.dataset.unit, document.querySelector('#edit-shopping-conversion')));
document.querySelector('#request-delete-shopping-item').addEventListener('click', deleteShoppingItem);
document.querySelector('#close-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#cancel-edit-shopping').addEventListener('click', closeShoppingEditor);
document.querySelector('#clear-purchased').addEventListener('click', async () => {
  try { await jsonRequest(`${SHOPPING_API}/purchased`, { method: 'DELETE' }); await loadShoppingList(); showToast(t("shoppingList.purchasedCleared")); }
  catch (error) { showToast(t("shoppingList.clearPurchasedFailed", {message: error.message}), 'error'); }
});
document.querySelector('#new-recipe').addEventListener('click', () => openRecipeEditor());
document.querySelector('#plan-recipes').addEventListener('click', openRecipePlan);
document.querySelector('#recipes-empty-add').addEventListener('click', () => openRecipeEditor());
document.querySelector('#close-recipe-detail').addEventListener('click', closeRecipeDetail);
async function reloadCurrentRecipeForPortions(){if(!currentRecipe)return;currentRecipe=await jsonRequest(`${RECIPE_API}/${currentRecipe.id}?portions=${recipePortions}`);renderRecipeDetail();}
document.querySelector('#recipe-portions-down').addEventListener('click', async() => { if (recipePortions > 1) { recipePortions--; try{await reloadCurrentRecipeForPortions();}catch(error){showToast(t("recipes.portionsUpdateFailed", {message: error.message}),'error');} } });
document.querySelector('#recipe-portions-up').addEventListener('click', async() => { recipePortions++; try{await reloadCurrentRecipeForPortions();}catch(error){showToast(t("recipes.portionsUpdateFailed", {message: error.message}),'error');} });
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
document.querySelector('#add-prepared-component').addEventListener('click',openPreparedComponentPicker);
document.querySelector('#cancel-prepared-component').addEventListener('click',closePreparedComponentPicker);
document.querySelector('#save-prepared-component').addEventListener('click',savePreparedComponent);
document.querySelector('#recipe-template-search').addEventListener('input', event => { clearTimeout(recipeSearchTimer); recipeSearchRequestId++; const search=event.target.value; if (!search) searchRecipeTemplates(''); else recipeSearchTimer=setTimeout(()=>searchRecipeTemplates(search),250); });
document.querySelector('#add-recipe-step').addEventListener('click', () => { recipeSteps.push({type:'TEXT',instruction:''}); renderRecipeEditor(); document.querySelector('#recipe-editor-steps textarea:last-of-type')?.focus(); });
document.querySelector('#add-process-step').addEventListener('click', () => openProcessPicker());
document.querySelector('#cooking-process-select').addEventListener('change', selectCookingProcess);
document.querySelector('#cancel-process-step').addEventListener('click', closeProcessPicker);
document.querySelector('#save-process-step').addEventListener('click', addProcessStep);
document.querySelector('#close-recipe-plan').addEventListener('click', closeRecipePlan);
document.querySelector('#calculate-recipe-plan').addEventListener('click', calculateRecipePlan);
document.querySelector('#add-recipe-missing').addEventListener('click', addRecipeMissing);
document.querySelector('#cook-recipe').addEventListener('click', cookCurrentRecipe);
document.querySelector('#show-recipe-library').addEventListener('click',()=>showRecipeSection('recipes'));
document.querySelector('#show-recipe-templates').addEventListener('click',()=>showRecipeSection('templates'));
document.querySelector('#show-meal-plans').addEventListener('click',()=>showRecipeSection('plans'));
document.querySelector('#recipe-template-catalog-search').addEventListener('input',event=>{clearTimeout(recipeTemplateCatalogTimer);recipeTemplateCatalogRequestId++;const query=event.target.value;if(!query)loadRecipeTemplates('');else recipeTemplateCatalogTimer=setTimeout(()=>loadRecipeTemplates(query),250);});
document.querySelector('#close-recipe-template-detail').addEventListener('click',closeRecipeTemplate);
document.querySelector('#recipe-template-portions-down').addEventListener('click',async()=>{if(recipeTemplatePortions>1&&currentRecipeTemplate){recipeTemplatePortions--;try{await loadRecipeTemplateDetail(currentRecipeTemplate.id,recipeTemplatePortions);}catch(error){showToast(t("recipes.portionsUpdateFailed", {message: error.message}),'error');}}});
document.querySelector('#recipe-template-portions-up').addEventListener('click',async()=>{if(currentRecipeTemplate){recipeTemplatePortions++;try{await loadRecipeTemplateDetail(currentRecipeTemplate.id,recipeTemplatePortions);}catch(error){showToast(t("recipes.portionsUpdateFailed", {message: error.message}),'error');}}});
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
  minimize: '<path d="M5 12h14"/>',
  chat: '<path d="M21 11a8 8 0 0 1-8 8H7l-4 3V11a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8Z"/><path d="M7 9h10M7 13h6"/>',
  logout: '<path d="M10 17l5-5-5-5M15 12H3M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5"/>'
};
document.querySelectorAll('[data-icon]').forEach(element => {
  element.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">${iconPaths[element.dataset.icon] || iconPaths.more}</svg>`;
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js')
    .catch(() => showMessage(authError, t("app.offlineUnavailable"))));
}

initialize();

document.querySelector('#more-recipe-import').addEventListener('click', () => showView('recipe-import'));
document.querySelector('#close-recipe-import').addEventListener('click', () => showView('more'));
