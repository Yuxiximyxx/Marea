// 缓存 URL.createObjectURL 产生的 Blob，供原生 extractBlob 直接读取，
// 避免对 blob: 再 fetch（WebView 里常报 Failed to fetch / CORS）。
(function () {
  if (window.__mafariBlobCacheInstalled) return;
  window.__mafariBlobCacheInstalled = true;
  var cache = Object.create(null);
  window.__mafariBlobCache = cache;
  try {
    var origCreate = URL.createObjectURL;
    if (origCreate) {
      URL.createObjectURL = function (obj) {
        var url = origCreate.apply(URL, arguments);
        try {
          if (obj && (obj instanceof Blob || (typeof File !== 'undefined' && obj instanceof File))) {
            cache[url] = obj;
          }
        } catch (e) {}
        return url;
      };
    }
    // revoke 后下载回调可能仍会带着该 URL，缓存里先不删
    var origRevoke = URL.revokeObjectURL;
    if (origRevoke) {
      URL.revokeObjectURL = function (url) {
        return origRevoke.apply(URL, arguments);
      };
    }
  } catch (e) {}
})();
