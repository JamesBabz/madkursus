const PRODUCT_API = '/v1/products';

const categoryLabels = {
  MEAT: 'Kød', VEGETABLE: 'Grøntsag', FRUIT: 'Frugt', DAIRY: 'Mejeri',
  DRY_GOODS: 'Tørvarer', SPICE: 'Krydderi', OTHER: 'Andet'
};
const unitLabels = { GRAM: 'Gram', MILLILITER: 'Milliliter', PIECE: 'Styk' };
const categoryIcons = {
  MEAT: '🍗', VEGETABLE: '🥕', FRUIT: '🍎', DAIRY: '🥛',
  DRY_GOODS: '🌾', SPICE: '🌿', OTHER: '🍽️'
};

const formPanel = document.querySelector('#product-form-panel');
const form = document.querySelector('#product-form');
const openFormButton = document.querySelector('#open-form');
const saveButton = document.querySelector('#save-product');
const list = document.querySelector('#product-list');
const loading = document.querySelector('#loading-message');
const emptyState = document.querySelector('#empty-state');
const errorMessage = document.querySelector('#error-message');
const successMessage = document.querySelector('#success-message');

function setFormOpen(open) {
  formPanel.hidden = !open;
  openFormButton.setAttribute('aria-expanded', String(open));
  if (open) document.querySelector('#name').focus();
}

function showMessage(element, message) {
  element.textContent = message;
  element.hidden = !message;
}

function apiError(response, body) {
  const detail = body?.message || body?.errors?.[0];
  return new Error(detail || `Serveren svarede med status ${response.status}.`);
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
    const response = await fetch(PRODUCT_API, { headers: { Accept: 'application/json' } });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw apiError(response, body);
    list.replaceChildren(...body.map(createProductCard));
    emptyState.hidden = body.length !== 0;
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

  const data = new FormData(form);
  const payload = {
    name: data.get('name').trim(),
    category: data.get('category'),
    defaultUnit: data.get('defaultUnit')
  };

  try {
    const response = await fetch(PRODUCT_API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload)
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw apiError(response, body);
    form.reset();
    setFormOpen(false);
    await loadProducts();
    showMessage(successMessage, `${body?.name || payload.name} blev tilføjet.`);
  } catch (error) {
    showMessage(errorMessage, `Produktet kunne ikke gemmes. ${error.message}`);
  } finally {
    saveButton.disabled = false;
    saveButton.textContent = 'Gem produkt';
  }
}

openFormButton.addEventListener('click', () => setFormOpen(formPanel.hidden));
document.querySelector('#close-form').addEventListener('click', () => setFormOpen(false));
document.querySelector('#empty-add').addEventListener('click', () => setFormOpen(true));
document.querySelector('#refresh-products').addEventListener('click', loadProducts);
form.addEventListener('submit', createProduct);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/service-worker.js')
    .catch(() => showMessage(errorMessage, 'Appen virker, men offline-understøttelse kunne ikke startes.')));
}

loadProducts();
