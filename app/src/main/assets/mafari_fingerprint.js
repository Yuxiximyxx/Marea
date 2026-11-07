/*
 * Mafari 指纹保护（去广告 → 指纹保护）。document-start 注入所有框架。
 *
 * 配置由 Kotlin 侧 AdBlockBridge.fp(host, topHost) 提供，格式 "掩码:种子"：
 *   掩码 1 = Canvas，2 = WebGL，4 = Audio，8 = Fonts；0 = 本站不处理（总开关关闭 / 站点白名单）。
 *
 * 做法是「农场化」（farbling）：不禁用 API，而是在读出指纹的那一刻加极小的噪声 / 换掉可识别的值，
 * 让同一设备每次读出的指纹都不一样，无法被用来跨站、跨会话追踪；正常绘图 / 播放不受影响。
 */
(function () {
  'use strict';
  try {
    if (window.__mafariFp) return;
    if (typeof MafariAdblock === 'undefined' || typeof MafariAdblock.fp !== 'function') return;

    var host = location.hostname;
    var topHost = host;
    try {
      var ao = location.ancestorOrigins;
      if (ao && ao.length) topHost = new URL(ao[ao.length - 1]).hostname;
    } catch (e) {}

    var cfg = '';
    try { cfg = String(MafariAdblock.fp(host, topHost)); } catch (e) { return; }
    var parts = cfg.split(':');
    var MASK = parseInt(parts[0], 10) | 0;
    var SEED = (parseInt(parts[1], 10) | 0) >>> 0;
    if (!MASK) return;
    try { Object.defineProperty(window, '__mafariFp', { value: 1 }); } catch (e) { window.__mafariFp = 1; }

    var CANVAS = 1, WEBGL = 2, AUDIO = 4, FONTS = 8;
    var MAX_PIXELS = 4000000;

    /* ---------------- 工具 ---------------- */

    var nativeToString = Function.prototype.toString;
    var fakeSrc = new WeakMap();

    function rngFrom(seed) {
      var a = seed >>> 0;
      return function () {
        a = (a + 0x6D2B79F5) | 0;
        var t = Math.imul(a ^ (a >>> 15), 1 | a);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
      };
    }

    // 每次读取都用新的随机序列：同一画布连续读两次结果也不同
    var counter = 0;
    function nextRng() {
      counter = (counter + 1) | 0;
      return rngFrom((SEED ^ Math.imul(counter, 0x9E3779B1)) + ((Math.random() * 4294967296) | 0));
    }

    // 按字符串取稳定哈希：同一会话同一站点里结果固定，换站点 / 重启应用后变化
    function hash(str) {
      var h = (2166136261 ^ SEED) >>> 0;
      for (var i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 16777619);
      }
      return h >>> 0;
    }

    /** 用我们的函数替换原型上的方法，并让它们的 toString() 看起来仍是原生函数 */
    function hook(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.value !== 'function') return;
        var orig = d.value;
        var fn = make(orig);
        try { Object.defineProperty(fn, 'name', { value: name, configurable: true }); } catch (e) {}
        try { Object.defineProperty(fn, 'length', { value: orig.length, configurable: true }); } catch (e) {}
        fakeSrc.set(fn, nativeToString.call(orig));
        d.value = fn;
        Object.defineProperty(obj, name, d);
      } catch (e) {}
    }

    function hookGetter(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.get !== 'function') return;
        var orig = d.get;
        var fn = make(orig);
        try { Object.defineProperty(fn, 'name', { value: 'get ' + name, configurable: true }); } catch (e) {}
        fakeSrc.set(fn, nativeToString.call(orig));
        d.get = fn;
        Object.defineProperty(obj, name, d);
      } catch (e) {}
    }

    var patchedToString = function toString() {
      var s = fakeSrc.get(this);
      return s !== undefined ? s : nativeToString.call(this);
    };
    fakeSrc.set(patchedToString, nativeToString.call(nativeToString));

    /* ---------------- Canvas / WebGL 像素噪声 ---------------- */

    /** 在 RGBA 数据里随机挑一些不透明像素，把某个颜色通道 ±1（不动 alpha） */
    function noisePixels(data, pixels) {
      if (!data || pixels < 64 || pixels > MAX_PIXELS || data.length < pixels * 4) return;
      var r = nextRng();
      var tries = 32 + Math.min(4000, (pixels / 100) | 0);
      for (var k = 0; k < tries; k++) {
        var i = ((r() * pixels) | 0) * 4;
        if (data[i + 3] === 0) continue;
        var c = i + ((r() * 3) | 0);
        var v = data[c];
        data[c] = v === 0 ? 1 : v === 255 ? 254 : (r() < 0.5 ? v - 1 : v + 1);
      }
    }

    function noiseFloats(arr, step, rel) {
      var r = nextRng();
      for (var i = 0; i < arr.length; i += step) {
        var x = arr[i];
        if (x !== 0 && isFinite(x)) arr[i] = x + x * (r() - 0.5) * rel;
      }
    }

    var WEBGL_GPUS = [
      ['Google Inc. (Qualcomm)', 'ANGLE (Qualcomm, Adreno (TM) 650, OpenGL ES 3.2)'],
      ['Google Inc. (ARM)', 'ANGLE (ARM, Mali-G78, OpenGL ES 3.2)'],
      ['Google Inc. (Qualcomm)', 'ANGLE (Qualcomm, Adreno (TM) 730, OpenGL ES 3.2)'],
      ['Google Inc. (ARM)', 'ANGLE (ARM, Mali-G710, OpenGL ES 3.2)']
    ];

    var COMMON_FONTS = {
      'arial': 1, 'helvetica': 1, 'times new roman': 1, 'times': 1, 'courier new': 1, 'courier': 1,
      'georgia': 1, 'verdana': 1, 'tahoma': 1, 'trebuchet ms': 1, 'impact': 1, 'roboto': 1,
      'noto sans': 1, 'noto serif': 1, 'droid sans': 1, 'sans-serif': 1, 'serif': 1, 'monospace': 1,
      'system-ui': 1, 'cursive': 1, 'fantasy': 1
    };

    var installed = new WeakSet();

    /* ---------------- 安装到某个窗口（主窗口 + 同源 iframe） ---------------- */

    function install(W) {
      if (installed.has(W)) return;
      installed.add(W);

      try { W.Function.prototype.toString = patchedToString; } catch (e) {}

      var CP = W.HTMLCanvasElement && W.HTMLCanvasElement.prototype;
      var C2 = W.CanvasRenderingContext2D && W.CanvasRenderingContext2D.prototype;

      /* ===== Canvas / WebGL 画布导出 ===== */
      if ((MASK & (CANVAS | WEBGL)) && CP && C2) {
        var ctxType = new WeakMap();
        var origGetContext = CP.getContext;
        var origGetImageData = C2.getImageData;
        var origPutImageData = C2.putImageData;
        var origDrawImage = C2.drawImage;

        hook(CP, 'getContext', function (orig) {
          return function getContext(type) {
            var c = orig.apply(this, arguments);
            if (c) { try { ctxType.set(this, String(type)); } catch (e) {} }
            return c;
          };
        });

        // 2D 画布受 Canvas 开关管，WebGL 画布受 WebGL 开关管
        var farbleCanvas = function (canvas) {
          var t = ctxType.get(canvas) || '2d';
          return /webgl|experimental/i.test(t) ? (MASK & WEBGL) !== 0 : (MASK & CANVAS) !== 0;
        };

        var noisyCopy = function (src, w, h) {
          var t = W.document.createElement('canvas');
          t.width = w;
          t.height = h;
          var c = origGetContext.call(t, '2d');
          origDrawImage.call(c, src, 0, 0);
          var img = origGetImageData.call(c, 0, 0, w, h);
          noisePixels(img.data, w * h);
          origPutImageData.call(c, img, 0, 0);
          return t;
        };

        hook(CP, 'toDataURL', function (orig) {
          return function toDataURL() {
            try {
              var w = this.width, h = this.height;
              if (w > 0 && h > 0 && w * h <= MAX_PIXELS && farbleCanvas(this)) {
                return orig.apply(noisyCopy(this, w, h), arguments);
              }
            } catch (e) {} // 画布被污染等情况：交给原函数照常报错
            return orig.apply(this, arguments);
          };
        });

        hook(CP, 'toBlob', function (orig) {
          return function toBlob() {
            try {
              var w = this.width, h = this.height;
              if (w > 0 && h > 0 && w * h <= MAX_PIXELS && farbleCanvas(this)) {
                return orig.apply(noisyCopy(this, w, h), arguments);
              }
            } catch (e) {}
            return orig.apply(this, arguments);
          };
        });

        if (MASK & CANVAS) {
          hook(C2, 'getImageData', function (orig) {
            return function getImageData() {
              var img = orig.apply(this, arguments);
              try { if (img && img.data) noisePixels(img.data, img.width * img.height); } catch (e) {}
              return img;
            };
          });

          var OC = W.OffscreenCanvasRenderingContext2D && W.OffscreenCanvasRenderingContext2D.prototype;
          hook(OC, 'getImageData', function (orig) {
            return function getImageData() {
              var img = orig.apply(this, arguments);
              try { if (img && img.data) noisePixels(img.data, img.width * img.height); } catch (e) {}
              return img;
            };
          });

          var OCV = W.OffscreenCanvas && W.OffscreenCanvas.prototype;
          hook(OCV, 'convertToBlob', function (orig) {
            return function convertToBlob() {
              try {
                var w = this.width, h = this.height;
                if (w > 0 && h > 0 && w * h <= MAX_PIXELS) {
                  var t = new W.OffscreenCanvas(w, h);
                  var c = t.getContext('2d');
                  c.drawImage(this, 0, 0);
                  var img = c.getImageData(0, 0, w, h); // 这里会经过上面的 getImageData 加噪
                  c.putImageData(img, 0, 0);
                  return orig.apply(t, arguments);
                }
              } catch (e) {}
              return orig.apply(this, arguments);
            };
          });
        }
      }

      /* ===== WebGL ===== */
      if (MASK & WEBGL) {
        var gpu = WEBGL_GPUS[SEED % WEBGL_GPUS.length];
        ['WebGLRenderingContext', 'WebGL2RenderingContext'].forEach(function (n) {
          var P = W[n] && W[n].prototype;
          if (!P) return;
          hook(P, 'getParameter', function (orig) {
            return function getParameter(p) {
              // UNMASKED_VENDOR_WEBGL / UNMASKED_RENDERER_WEBGL：真实显卡型号是最强的 WebGL 指纹
              if (p === 0x9245) return gpu[0];
              if (p === 0x9246) return gpu[1];
              return orig.apply(this, arguments);
            };
          });
          hook(P, 'readPixels', function (orig) {
            return function readPixels(x, y, w, h, format, type, pixels) {
              var r = orig.apply(this, arguments);
              try {
                if (format === 0x1908 && type === 0x1401 && pixels && pixels.length >= w * h * 4) {
                  noisePixels(pixels, w * h);
                }
              } catch (e) {}
              return r;
            };
          });
        });
      }

      /* ===== Audio ===== */
      if (MASK & AUDIO) {
        var seen = new WeakMap(); // AudioBuffer -> 已加过噪的声道
        hook(W.AudioBuffer && W.AudioBuffer.prototype, 'getChannelData', function (orig) {
          return function getChannelData(ch) {
            var d = orig.apply(this, arguments);
            try {
              var done = seen.get(this);
              if (!done) { done = {}; seen.set(this, done); }
              if (!done[ch] && d && d.length <= MAX_PIXELS) {
                done[ch] = 1;
                noiseFloats(d, 4, 2e-5); // 相对 ±1e-5（约 -100 dB），听不出来
              }
            } catch (e) {}
            return d;
          };
        });
        hook(W.AudioBuffer && W.AudioBuffer.prototype, 'copyFromChannel', function (orig) {
          return function copyFromChannel(dest) {
            var r = orig.apply(this, arguments);
            try { if (dest && dest.length <= MAX_PIXELS) noiseFloats(dest, 4, 2e-5); } catch (e) {}
            return r;
          };
        });
        var AN = W.AnalyserNode && W.AnalyserNode.prototype;
        ['getFloatFrequencyData', 'getFloatTimeDomainData'].forEach(function (m) {
          hook(AN, m, function (orig) {
            return function () {
              var r = orig.apply(this, arguments);
              try {
                var a = arguments[0];
                if (a) {
                  var rnd = nextRng();
                  for (var i = 0; i < a.length; i += 2) if (isFinite(a[i])) a[i] += (rnd() - 0.5) * 0.02;
                }
              } catch (e) {}
              return r;
            };
          });
        });
        ['getByteFrequencyData', 'getByteTimeDomainData'].forEach(function (m) {
          hook(AN, m, function (orig) {
            return function () {
              var r = orig.apply(this, arguments);
              try {
                var a = arguments[0];
                if (a) {
                  var rnd = nextRng();
                  for (var i = 0; i < a.length; i += 8) {
                    var v = a[i];
                    a[i] = v <= 0 ? 1 : v >= 255 ? 254 : (rnd() < 0.5 ? v - 1 : v + 1);
                  }
                }
              } catch (e) {}
              return r;
            };
          });
        });
      }

      /* ===== Fonts ===== */
      if (MASK & FONTS) {
        // 1) canvas measureText：宽度等乘以 (1±0.03%)。系数由「字体串+文本」决定，同一会话里同一次测量结果稳定，
        //    但「候选字体」与「基准字体」的系数不同 → 探测脚本看到所有字体都「有差异」，无法枚举已安装字体。
        var wrapMetrics = function (ctx, text, m) {
          var k = hash(String(ctx.font) + '|' + String(text));
          var f = 1 + (((k % 2001) - 1000) / 1000) * 3e-4;
          return new Proxy(m, {
            get: function (t, p) {
              var v = t[p];
              return typeof v === 'number' ? v * f : v;
            }
          });
        };
        [C2, W.OffscreenCanvasRenderingContext2D && W.OffscreenCanvasRenderingContext2D.prototype].forEach(function (P) {
          hook(P, 'measureText', function (orig) {
            return function measureText(text) {
              var m = orig.apply(this, arguments);
              try { return wrapMetrics(this, text, m); } catch (e) { return m; }
            };
          });
        });

        // 2) DOM 探测：元素带内联 font-family 且文字很短（典型的「测宽」探针）时，给 offsetWidth/Height、
        //    getBoundingClientRect 加按字体串决定的微小偏移，同样让「不存在的字体」也显得有差异。
        var probeKey = function (el) {
          try {
            var ff = el.style && el.style.fontFamily;
            if (!ff) return null;
            var t = el.textContent;
            if (t && t.length > 120) return null;
            return ff + '|' + (t || '');
          } catch (e) { return null; }
        };
        var HE = W.HTMLElement && W.HTMLElement.prototype;
        hookGetter(HE, 'offsetWidth', function (orig) {
          return function () {
            var v = orig.call(this);
            var k = probeKey(this);
            return k === null ? v : Math.max(0, v + (hash('w' + k) % 3) - 1);
          };
        });
        hookGetter(HE, 'offsetHeight', function (orig) {
          return function () {
            var v = orig.call(this);
            var k = probeKey(this);
            return k === null ? v : Math.max(0, v + (hash('h' + k) % 3) - 1);
          };
        });
        hook(W.Element && W.Element.prototype, 'getBoundingClientRect', function (orig) {
          return function getBoundingClientRect() {
            var r = orig.apply(this, arguments);
            try {
              var k = probeKey(this);
              if (k === null) return r;
              var dw = (((hash('bw' + k) % 2001) - 1000) / 1000) * 0.4;
              var dh = (((hash('bh' + k) % 2001) - 1000) / 1000) * 0.4;
              return new W.DOMRect(r.x, r.y, Math.max(0, r.width + dw), Math.max(0, r.height + dh));
            } catch (e) { return r; }
          };
        });

        // 3) FontFace + local()：通过「能否加载本机字体」枚举字体。纯 local() 来源的字体，只对常见字体返回成功。
        try {
          var OrigFontFace = W.FontFace;
          if (OrigFontFace) {
            var localOnly = new WeakMap();
            var origLoad = OrigFontFace.prototype.load;
            W.FontFace = new Proxy(OrigFontFace, {
              construct: function (target, args, newTarget) {
                var f = Reflect.construct(target, args, newTarget);
                try {
                  var src = args[1];
                  if (typeof src === 'string' && /local\s*\(/i.test(src) && !/url\s*\(/i.test(src)) {
                    var names = [];
                    src.replace(/local\s*\(\s*(['"]?)([^'")]+)\1\s*\)/ig, function (_, q, n) { names.push(n.trim().toLowerCase()); return ''; });
                    localOnly.set(f, names);
                  }
                } catch (e) {}
                return f;
              }
            });
            hook(OrigFontFace.prototype, 'load', function (orig) {
              return function load() {
                var names = localOnly.get(this);
                if (names && !names.some(function (n) { return COMMON_FONTS[n] === 1; })) {
                  return Promise.reject(new DOMException('A network error occurred.', 'NetworkError'));
                }
                return orig.apply(this, arguments);
              };
            });
          }
        } catch (e) {}

        // 4) Local Font Access API
        try {
          if (typeof W.queryLocalFonts === 'function') {
            var deny = function queryLocalFonts() {
              return Promise.reject(new DOMException('Permission denied', 'NotAllowedError'));
            };
            fakeSrc.set(deny, nativeToString.call(W.queryLocalFonts));
            W.queryLocalFonts = deny;
          }
        } catch (e) {}
      }

      // 页面自己创建同源 iframe 再去读指纹是常见绕法：iframe 的 contentWindow / contentDocument 被取用时，同样装上保护
      var IF = W.HTMLIFrameElement && W.HTMLIFrameElement.prototype;
      hookGetter(IF, 'contentWindow', function (orig) {
        return function () {
          var w = orig.call(this);
          adopt(w);
          return w;
        };
      });
      hookGetter(IF, 'contentDocument', function (orig) {
        return function () {
          var d = orig.call(this);
          try { if (d) adopt(d.defaultView); } catch (e) {}
          return d;
        };
      });
    }

    function adopt(w) {
      try {
        if (!w || w === window) return;
        // 跨源框架访问属性会抛 SecurityError，直接跳过
        if (w.__mafariFp) return;
        Object.defineProperty(w, '__mafariFp', { value: 1 });
        install(w);
      } catch (e) {}
    }

    install(window);
  } catch (e) {}
})();
