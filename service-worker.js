// service-worker.js
const CACHE_NAME = 'sunday-app-cache-v7';
const APP_SHELL_PATHS = [
  '/sunday/',
  '/sunday/index.html',
  '/sunday/app.js',
  '/sunday/styles.css',
  '/sunday/models/task.js',
  '/sunday/models/taskList.js',
  '/sunday/manifest.webmanifest',
];
const ASSETS_TO_CACHE = [
  '/sunday/',
  '/sunday/index.html',
  '/sunday/styles.css',
  '/sunday/app.js',
  '/sunday/models/task.js',
  '/sunday/models/taskList.js',
  '/sunday/banners/dark-sunday.png',
  '/sunday/banners/sunday-light.png',
  '/sunday/manifest.webmanifest',
  '/sunday/vendor/playpen-sans/playpen-sans.css',
  '/sunday/vendor/playpen-sans/playpen-sans-latin-400.woff2',
  '/sunday/vendor/playpen-sans/playpen-sans-latin-ext-400.woff2',
  '/sunday/vendor/quill/quill.snow.css',
  '/sunday/vendor/quill/quill.js',
  '/sunday/vendor/highlightjs/base16-dracula.min.css',
  '/sunday/vendor/highlightjs/github.min.css',
  '/sunday/vendor/highlightjs/highlight.min.js',
  '/sunday/vendor/dragdroptouch/drag-drop-touch.esm.min.js',
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      Promise.all(ASSETS_TO_CACHE.map(url => cache.add(url).catch(() => {})))
    )
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;

  const requestUrl = new URL(event.request.url);
  const isSameOrigin = requestUrl.origin === self.location.origin;
  const requestPath = requestUrl.pathname;
  const isDocument = event.request.mode === 'navigate';
  const isAppShellAsset = APP_SHELL_PATHS.includes(requestPath);

  // Always prefer network for navigations and core app shell assets so users get fresh code.
  if (isSameOrigin && (isDocument || isAppShellAsset)) {
    event.respondWith(
      fetch(event.request)
        .then(async response => {
          const cache = await caches.open(CACHE_NAME);
          cache.put(event.request, response.clone()).catch(() => {});
          return response;
        })
        .catch(async () => {
          const cached = await caches.match(event.request);
          if (cached) return cached;
          if (isDocument) {
            return (await caches.match('/sunday/index.html')) || Response.error();
          }
          return Response.error();
        })
    );
    return;
  }

  // Cache-first for static/vendor resources, fallback to network and backfill cache.
  event.respondWith(
    caches.match(event.request).then(async response => {
      if (response) return response;
      const networkResponse = await fetch(event.request);
      if (isSameOrigin) {
        const cache = await caches.open(CACHE_NAME);
        cache.put(event.request, networkResponse.clone()).catch(() => {});
      }
      return networkResponse;
    })
  );
});
