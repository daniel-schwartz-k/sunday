// service-worker.js
const CACHE_NAME = 'sunday-app-cache-v6';
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
  event.respondWith(
    caches.match(event.request).then(response => {
      return response || fetch(event.request);
    })
  );
});
