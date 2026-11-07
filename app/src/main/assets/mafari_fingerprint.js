/*
 * Mafari 指纹保护（去广告 → 指纹保护）。document-start 注入所有框架。
 *
 * 配置由 Kotlin 侧 AdBlockBridge.fp(host, topHost) 提供，格式 "掩码:种子"：
 *   掩码 1 = Canvas，2 = WebGL，4 = Audio，8 = Fonts；0 = 本站不处理（总开关关闭 / 站点白名单）。
 *
 * WebGL 只对 readPixels / WebGL 画布导出加噪，不再伪装显卡型号（固定型号与真实参数不一致，反而更独特）。
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

    /**
     * 用 Proxy 包装原生函数：name / length / 无 prototype / 不可 new 等属性与原生完全一致，
     * toString() 仍返回原生源码。（以前用普通 function 包装，带 prototype、可被 new，会被识别为「被篡改」）
     */
    function wrap(orig, impl) {
      var p = new Proxy(orig, {
        apply: function (t, thisArg, args) { return impl.apply(thisArg, args); }
      });
      try { fakeSrc.set(p, nativeToString.call(orig)); } catch (e) {}
      return p;
    }

    function hook(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.value !== 'function') return;
        d.value = wrap(d.value, make(d.value));
        Object.defineProperty(obj, name, d);
      } catch (e) {}
    }

    function hookGetter(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.get !== 'function') return;
        d.get = wrap(d.get, make(d.get));
        Object.defineProperty(obj, name, d);
      } catch (e) {}
    }

    /** 每个窗口用自己的 Function.prototype.toString 做 Proxy；被我们包装过的函数返回其原生源码 */
    function patchToString(W) {
      try {
        var ts = W.Function.prototype.toString;
        var p = new Proxy(ts, {
          apply: function (t, thisArg, args) {
            var s = (thisArg !== null && (typeof thisArg === 'function' || typeof thisArg === 'object')) ? fakeSrc.get(thisArg) : undefined;
            return s !== undefined ? s : Reflect.apply(t, thisArg, args);
          }
        });
        fakeSrc.set(p, nativeToString.call(ts));
        var d = Object.getOwnPropertyDescriptor(W.Function.prototype, 'toString');
        d.value = p;
        Object.defineProperty(W.Function.prototype, 'toString', d);
      } catch (e) {}
    }

    /* ---------------- Canvas / WebGL 像素噪声 ---------------- */

    /** 按（种子, 画布坐标）得到稳定的 32 位哈希：同一站点同一会话里，同一像素永远得到同样的噪声 */
    function pxHash(x, y) {
      var h = (SEED ^ Math.imul(x + 1, 0x9E3779B1) ^ Math.imul(y + 1, 0x85EBCA6B)) >>> 0;
      h ^= h >>> 15; h = Math.imul(h, 0x2C1B3C6D);
      h ^= h >>> 12; h = Math.imul(h, 0x297A2D39);
      h ^= h >>> 15;
      return h >>> 0;
    }

    /** 画面几乎是纯色（空白 / 纯色填充）：不加噪，避免被「纯色读回校验」识别为篡改 */
    function isFlat(data, n) {
      var seen = 0, c0 = -1, c1 = -1, c2 = -1;
      for (var i = 0; i < n; i++) {
        var o = i * 4;
        var c = ((data[o] << 24) | (data[o + 1] << 16) | (data[o + 2] << 8) | data[o + 3]) >>> 0;
        if (c === c0 || c === c1 || c === c2) continue;
        if (seen === 0) c0 = c; else if (seen === 1) c1 = c; else c2 = c;
        if (++seen > 3) return false;
      }
      return true;
    }

    /**
     * 确定性加噪：约 1/64 的像素把一个颜色通道 ±1（不动 alpha）。
     * 噪声只取决于「种子 + 像素在整张画布里的坐标」，所以同一画布重复读取、getImageData / toDataURL / toBlob
     * 之间、分块读取结果都一致——不会再出现「Canvas 不稳定」。(ox, oy) 是该块数据在画布里的左上角。
     * changed 不为空时，记录被修改的像素下标。返回是否有修改。
     */
    function noisePixels(data, w, h, ox, oy, changed) {
      var n = w * h;
      if (!data || !(w > 0) || !(h > 0) || n < 256 || n > MAX_PIXELS || data.length < n * 4) return false;
      if (isFlat(data, n)) return false;
      var any = false;
      for (var i = 0; i < n; i++) {
        var o = i * 4;
        if (data[o + 3] === 0) continue;
        var hv = pxHash(ox + (i % w), oy + ((i / w) | 0));
        if ((hv & 63) !== 0) continue;
        var c = o + (((hv >>> 6) % 3));
        var v = data[c];
        data[c] = v === 0 ? 1 : v === 255 ? 254 : ((hv >>> 8) & 1 ? v - 1 : v + 1);
        any = true;
        if (changed) changed.push(i);
      }
      return any;
    }

    function noiseFloats(arr, step, rel) {
      var r = nextRng();
      for (var i = 0; i < arr.length; i += step) {
        var x = arr[i];
        if (x !== 0 && isFinite(x)) arr[i] = x + x * (r() - 0.5) * rel;
      }
    }

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

      patchToString(W);

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
            if (c) { try { if (!ctxType.has(this)) ctxType.set(this, String(type)); } catch (e) {} }
            return c;
          };
        });

        // 2D 画布受 Canvas 开关管，WebGL 画布受 WebGL 开关管
        var farbleCanvas = function (canvas) {
          var t = ctxType.get(canvas) || '2d';
          return /webgl|experimental/i.test(t) ? (MASK & WEBGL) !== 0 : (MASK & CANVAS) !== 0;
        };

        /**
         * 导出用：复制画布，仅把被改动的像素逐个写回（1x1 putImageData），其余像素由 drawImage 原样拷贝，
         * 不做整幅 get/putImageData 往返（半透明像素预乘往返会产生额外误差）。
         * 画面是纯色 / 没有任何像素被改动时返回 null，调用方直接用原画布导出。
         */
        var noisyCopy = function (src, w, h, make2D, getData, putData, draw, mkImageData) {
          var c = make2D();
          draw.call(c, src, 0, 0);
          var img = getData.call(c, 0, 0, w, h);
          var changed = [];
          if (!noisePixels(img.data, w, h, 0, 0, changed)) return null;
          for (var k = 0; k < changed.length; k++) {
            var i = changed[k], o = i * 4;
            var one = mkImageData();
            one.data[0] = img.data[o]; one.data[1] = img.data[o + 1];
            one.data[2] = img.data[o + 2]; one.data[3] = img.data[o + 3];
            putData.call(c, one, i % w, (i / w) | 0);
          }
          return c.canvas;
        };

        var copyHtml = function (canvas, w, h) {
          var t;
          return noisyCopy(canvas, w, h, function () {
            t = W.document.createElement('canvas');
            t.width = w; t.height = h;
            return origGetContext.call(t, '2d');
          }, origGetImageData, origPutImageData, origDrawImage, function () { return new W.ImageData(1, 1); });
        };

        var exporter = function (orig) {
          return function () {
            try {
              var w = this.width, h = this.height;
              if (w > 0 && h > 0 && w * h <= MAX_PIXELS && farbleCanvas(this)) {
                var t = copyHtml(this, w, h);
                if (t) return orig.apply(t, arguments);
              }
            } catch (e) {} // 画布被污染等情况：交给原函数照常报错
            return orig.apply(this, arguments);
          };
        };
        hook(CP, 'toDataURL', function (orig) { var f = exporter(orig); return function toDataURL() { return f.apply(this, arguments); }; });
        hook(CP, 'toBlob', function (orig) { var f = exporter(orig); return function toBlob() { return f.apply(this, arguments); }; });

        if (MASK & CANVAS) {
          var gid = function (orig) {
            return function getImageData(sx, sy, sw, sh) {
              var img = orig.apply(this, arguments);
              try {
                if (img && img.data) {
                  var ox = Math.floor(+sx || 0), oy = Math.floor(+sy || 0);
                  if (sw < 0) ox += Math.floor(sw);
                  if (sh < 0) oy += Math.floor(sh);
                  noisePixels(img.data, img.width, img.height, ox, oy, null);
                }
              } catch (e) {}
              return img;
            };
          };
          hook(C2, 'getImageData', gid);

          var OC = W.OffscreenCanvasRenderingContext2D && W.OffscreenCanvasRenderingContext2D.prototype;
          var origOGetImageData = OC && OC.getImageData;
          var origOPutImageData = OC && OC.putImageData;
          var origODrawImage = OC && OC.drawImage;
          hook(OC, 'getImageData', gid);

          var OCV = W.OffscreenCanvas && W.OffscreenCanvas.prototype;
          if (OC && OCV) {
            hook(OCV, 'convertToBlob', function (orig) {
              return function convertToBlob() {
                try {
                  var w = this.width, h = this.height;
                  if (w > 0 && h > 0 && w * h <= MAX_PIXELS) {
                    var t = noisyCopy(this, w, h, function () {
                      return new W.OffscreenCanvas(w, h).getContext('2d');
                    }, origOGetImageData, origOPutImageData, origODrawImage, function () { return new W.ImageData(1, 1); });
                    if (t) return orig.apply(t, arguments);
                  }
                } catch (e) {}
                return orig.apply(this, arguments);
              };
            });
          }
        }
      }

      /* ===== WebGL ===== */
      if (MASK & WEBGL) {
        ['WebGLRenderingContext', 'WebGL2RenderingContext'].forEach(function (n) {
          var P = W[n] && W[n].prototype;
          if (!P) return;
          hook(P, 'readPixels', function (orig) {
            return function readPixels(x, y, w, h, format, type, pixels) {
              var r = orig.apply(this, arguments);
              try {
                if (format === 0x1908 && type === 0x1401 && pixels && pixels.length >= w * h * 4) {
                  noisePixels(pixels, w, h, x, y, null);
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
        // 1) canvas measureText（不再改动 DOM 的 offsetWidth / getBoundingClientRect，避免影响页面布局）：宽度等乘以 (1±0.03%)。系数由「字体串+文本」决定，同一会话里同一次测量结果稳定，
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

        // 2) FontFace + local()：通过「能否加载本机字体」枚举字体。纯 local() 来源的字体，只对常见字体返回成功。
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

        // 3) Local Font Access API
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
