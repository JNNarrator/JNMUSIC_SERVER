// 从注册作用域自动推导部署路径（兼容 /music/ 等 context-path）
const BASE_PATH = new URL(self.registration.scope).pathname;
const CACHE_NAME = 'jnmusic-v2';
const STATIC_ASSETS = [
  BASE_PATH,
  `${BASE_PATH}index.html`,
  `${BASE_PATH}favicon.svg`
];

// 安装事件 - 预缓存静态资源
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    }).catch((err) => {
      console.error('[SW] 预缓存失败，降级继续:', err);
    })
  );
  self.skipWaiting();
});

// 激活事件 - 清理旧缓存
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => caches.delete(name))
      );
    }).catch(() => {})
  );
  self.clients.claim();
});

// 请求拦截 - Network First 策略
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // API 请求使用 Network First — 基于部署路径
  if (url.pathname.startsWith(`${BASE_PATH}api/`)) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          // 成功则返回响应
          return response;
        })
        .catch(() => {
          // 失败则尝试从缓存获取
          return caches.match(request);
        })
    );
    return;
  }

  // 静态资源使用 Cache First
  if (
    request.destination === 'style' ||
    request.destination === 'script' ||
    request.destination === 'image' ||
    request.destination === 'font'
  ) {
    event.respondWith(
      caches.match(request).then((cached) => {
        if (cached) {
          // 后台更新缓存
          fetch(request).then((response) => {
            if (response.ok) {
              caches.open(CACHE_NAME).then((cache) => {
                cache.put(request, response);
              });
            }
          });
          return cached;
        }
        // 缓存中没有则请求网络
        return fetch(request).then((response) => {
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(request, clone);
            });
          }
          return response;
        });
      })
    );
    return;
  }

  // 其他请求使用 Network First
  event.respondWith(
    fetch(request)
      .then((response) => {
        return response;
      })
      .catch(() => {
        return caches.match(request).then((cached) => {
          return cached || caches.match(`${BASE_PATH}index.html`);
        });
      })
  );
});
