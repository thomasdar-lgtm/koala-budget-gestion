const CACHE_VERSION = '1.81';
const CACHE_NAME = 'kbg-v' + CACHE_VERSION;
const ASSETS = ['./', './index.html', './manifest.json', './icons/icon-192.png', './icons/icon-512.png'];

/* mise en cache un par un : un fichier manquant ne bloque plus l'installation */
self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_NAME)
      .then(c => Promise.all(ASSETS.map(u => c.add(u).catch(() => null))))
      .catch(() => null)
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('message', e => {
  if (e.data === 'skipWaiting' || (e.data && e.data.type === 'SKIP_WAITING')) self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

/* réseau d'abord, cache en secours (hors ligne) */
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;
  const isShell = e.request.mode === 'navigate' ||
                  /\/(index\.html|manifest\.json)$/.test(url.pathname) ||
                  url.pathname.endsWith('/');
  const req = isShell ? new Request(e.request.url, { cache: 'reload', credentials: 'same-origin' }) : e.request;
  e.respondWith(
    fetch(req)
      .then(r => {
        if (r.ok) { const copy = r.clone(); caches.open(CACHE_NAME).then(c => c.put(e.request, copy)); }
        return r;
      })
      .catch(() => caches.match(e.request).then(m => m || caches.match('./index.html')))
  );
});
