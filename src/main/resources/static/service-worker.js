const CACHE_NAME = 'madkursus-shell-v69';
const APP_SHELL = [
  '/js/locales/da.js?v=69',
  '/js/i18n.js?v=55',
  '/js/recipe-import.js?v=55',
  '/js/feedback.js?v=69',
  '/js/vendor/marked.umd.js?v=55',
  '/js/vendor/purify.min.js?v=55',
  '/js/chat-markdown.js?v=55',
  '/js/ai-chat.js?v=55',
  '/',
  '/index.html',
  '/css/app.css?v=68',
  '/js/dialog-viewport.js?v=59',
  '/js/app.js?v=68',
  '/manifest.json',
  '/icons/icon.svg',
  '/icons/maskable-icon.svg'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
  )));
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);

  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/v1/')) return;

  event.respondWith(caches.match(request).then(cached => cached || fetch(request).then(response => {
    if (response.ok) {
      const copy = response.clone();
      caches.open(CACHE_NAME).then(cache => cache.put(request, copy));
    }
    return response;
  })));
});
