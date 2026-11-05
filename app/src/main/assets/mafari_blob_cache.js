// 缓存 URL.createObjectURL 产生的 Blob，供原生 extractBlob 直接读取，
// 避免对 blob: 再 fetch（WebView 里常报 Failed to fetch / CORS）。
// 缓存有上限：revoke 后延迟删除，数量超限时淘汰最早的，避免长期占用内存。
(function () {
  if (window.__mafariBlobCacheInstalled) return;
  window.__mafariBlobCacheInstalled = true;
  var MAX_ENTRIES = 64;
  var REVOKE_GRACE_MS = 30000;
  var cache = Object.create(null);
  var order = [];
  window.__mafariBlobCache = cache;

  function drop(url) {
    if (!(url in cache)) return;
    delete cache[url];
    var i = order.indexOf(url);
    if (i >= 0) order.splice(i, 1);
  }

  try {
    var origCreate = URL.createObjectURL;
    if (origCreate) {
      URL.createObjectURL = function (obj) {
        var url = origCreate.apply(URL, arguments);
        try {
          if (obj && (obj instanceof Blob || (typeof File !== 'undefined' && obj instanceof File))) {
            cache[url] = obj;
            order.push(url);
            while (order.length > MAX_ENTRIES) {
              delete cache[order.shift()];
            }
          }
        } catch (e) {}
        return url;
      };
    }
    // revoke 后下载回调可能仍会带着该 URL，所以延迟一段时间再从缓存删除
    var origRevoke = URL.revokeObjectURL;
    if (origRevoke) {
      URL.revokeObjectURL = function (url) {
        try {
          setTimeout(function () { drop(url); }, REVOKE_GRACE_MS);
        } catch (e) {}
        return origRevoke.apply(URL, arguments);
      };
    }
  } catch (e) {}
})();
