/*
 * Mafari Fingerprint Protection Engine 2.1
 *
 * 注入顺序：AdBlock 完成后、页面脚本执行前；每个 frame 均应独立执行 document-start 注入。
 *
 * 兼容配置：MafariAdblock.fp(host, topHost) -> "掩码:种子"
 *   1 = Canvas
 *   2 = WebGL
 *   4 = Audio
 *   8 = Fonts
 *   0 = 当前 frame 不保护
 *
 * 安全边界：
 * - 本脚本不能保护一个可被网页直接调用、且无来源校验的 @JavascriptInterface 密钥接口。
 * - Kotlin 侧应使用受限的 origin-aware 消息通道提供配置；不要让网页可任意查询其他 host 的 seed。
 * - 本脚本不声称覆盖 Worker、WebGL2 PBO、所有 CSS 字体探测或 OfflineAudioContext 的 getChannelData 路径。
 */
(function () {
  'use strict';

  try {
    if (window.__mafariFp2) return;
    if (typeof MafariAdblock === 'undefined' ||
        typeof MafariAdblock.fp !== 'function') return;

    var host = location.hostname;
    var topHost = host;

    try {
      var ao = location.ancestorOrigins;
      if (ao && ao.length) {
        topHost = new URL(ao[ao.length - 1]).hostname || host;
      }
    } catch (_) {}

    var cfg = '';
    try {
      cfg = String(MafariAdblock.fp(host, topHost));
    } catch (_) {
      return;
    }

    var parts = cfg.split(':');
    var MASK = Number.parseInt(parts[0], 10);
    var seedValue = Number.parseInt(parts[1], 10);

    if (!Number.isFinite(MASK)) MASK = 0;
    if (!Number.isFinite(seedValue)) seedValue = 0;

    MASK = MASK & 15;
    var SEED = seedValue >>> 0;

    // 标记关闭状态，避免后续同源 iframe adopt 使用父 frame 配置覆盖本站策略。
    try {
      Object.defineProperty(window, '__mafariFp2', {
        value: 1, configurable: false, enumerable: false, writable: false
      });
    } catch (_) {
      window.__mafariFp2 = 1;
    }

    if (!MASK) return;

    var CANVAS = 1;
    var WEBGL = 2;
    var AUDIO = 4;
    var FONTS = 8;
    var MAX_PIXELS = 4000000;

    var nativeToString = Function.prototype.toString;
    var fakeSrc = new WeakMap();

    function mix32(x) {
      x = Math.imul(x ^ (x >>> 16), 0x7feb352d);
      x = Math.imul(x ^ (x >>> 15), 0x846ca68b);
      return (x ^ (x >>> 16)) >>> 0;
    }

    function hashString(str, initial) {
      var h = (initial === undefined ? SEED : initial) >>> 0;
      for (var i = 0; i < str.length; i++) {
        h ^= str.charCodeAt(i);
        h = Math.imul(h, 16777619);
      }
      return mix32(h);
    }

    // 不使用 Math.random：同一 frame / 站点 / API 输出保持确定性。
    function keyedValue(label, a, b, c) {
      var h = hashString(String(label), SEED ^ 0x9e3779b9);
      h = mix32(h ^ (a >>> 0));
      h = mix32(h ^ (b >>> 0));
      h = mix32(h ^ (c >>> 0));
      return h;
    }

    function unitNoise(label, a, b, c) {
      return keyedValue(label, a, b, c) / 4294967296;
    }

    function wrap(orig, impl) {
      var proxy = new Proxy(orig, {
        apply: function (target, thisArg, args) {
          return impl.apply(thisArg, args);
        }
      });
      try { fakeSrc.set(proxy, nativeToString.call(orig)); } catch (_) {}
      return proxy;
    }

    function hook(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.value !== 'function') return;
        d.value = wrap(d.value, make(d.value));
        Object.defineProperty(obj, name, d);
      } catch (_) {}
    }

    function hookGetter(obj, name, make) {
      try {
        if (!obj) return;
        var d = Object.getOwnPropertyDescriptor(obj, name);
        if (!d || typeof d.get !== 'function') return;
        d.get = wrap(d.get, make(d.get));
        Object.defineProperty(obj, name, d);
      } catch (_) {}
    }

    function patchToString(W) {
      try {
        var proto = W.Function.prototype;
        var original = proto.toString;
        var proxy = new Proxy(original, {
          apply: function (target, thisArg, args) {
            var source;
            try {
              if (thisArg && (typeof thisArg === 'function' ||
                              typeof thisArg === 'object')) {
                source = fakeSrc.get(thisArg);
              }
            } catch (_) {}
            return source !== undefined
              ? source
              : Reflect.apply(target, thisArg, args);
          }
        });
        fakeSrc.set(proxy, nativeToString.call(original));
        var d = Object.getOwnPropertyDescriptor(proto, 'toString');
        if (d) {
          d.value = proxy;
          Object.defineProperty(proto, 'toString', d);
        }
      } catch (_) {}
    }

    /* ---------------- Canvas / WebGL 像素扰动 ---------------- */

    var canvasIds = new WeakMap();
    var nextCanvasId = 1;

    function canvasId(canvas) {
      var id = canvasIds.get(canvas);
      if (!id) {
        id = nextCanvasId++;
        canvasIds.set(canvas, id);
      }
      return id;
    }

    // 轻量、确定性、内容相关的像素扰动。不会修改原始画布，只修改读回副本。
    function perturbPixels(data, width, height, originX, originY, id, label, changedIndexes) {
      var count = width * height;
      if (!data || width <= 0 || height <= 0 ||
          count < 64 || count > MAX_PIXELS ||
          data.length < count * 4) return false;

      var changed = false;
      for (var i = 0; i < count; i++) {
        var offset = i * 4;
        var alpha = data[offset + 3];
        if (alpha === 0) continue;

        var x = (originX + (i % width)) | 0;
        var y = (originY + ((i / width) | 0)) | 0;

        // 将像素原值纳入输入，避免不同图像共享完全相同的噪声图。
        var color = (data[offset] |
                    (data[offset + 1] << 8) |
                    (data[offset + 2] << 16) |
                    (alpha << 24)) >>> 0;

        var h = keyedValue(label, id ^ x, y, color);
        // 约 1/128 像素修改一个 RGB 通道，幅度最多 1。
        if ((h & 127) !== 0) continue;

        var channel = offset + ((h >>> 7) % 3);
        var value = data[channel];
        if (value === 0) data[channel] = 1;
        else if (value === 255) data[channel] = 254;
        else data[channel] = (h & 0x100) ? value - 1 : value + 1;

        changed = true;
        if (changedIndexes) changedIndexes.push(i);
      }
      return changed;
    }

    var htmlCanvasType = new WeakMap();
    var offscreenType = new WeakMap();

    function isWebGLType(type) {
      return /webgl|experimental/i.test(String(type || ''));
    }

    function shouldProtectCanvas(type) {
      return isWebGLType(type) ? !!(MASK & WEBGL) : !!(MASK & CANVAS);
    }

    function copyNoisyCanvas(source, width, height, create2DContext,
                             getImageData, putImageData, drawImage,
                             makeImageData, id, label) {
      var ctx = create2DContext();
      if (!ctx) return null;

      drawImage.call(ctx, source, 0, 0);
      var image = getImageData.call(ctx, 0, 0, width, height);
      var changed = [];

      if (!perturbPixels(
        image.data, width, height, 0, 0, id, label, changed
      )) return null;

      // 仅写回变更像素，避免整幅透明图像进行预乘 / 反预乘往返。
      for (var k = 0; k < changed.length; k++) {
        var index = changed[k];
        var off = index * 4;
        var x = index % width;
        var y = (index / width) | 0;
        var one = makeImageData();
        one.data[0] = image.data[off];
        one.data[1] = image.data[off + 1];
        one.data[2] = image.data[off + 2];
        one.data[3] = image.data[off + 3];
        putImageData.call(ctx, one, x, y);
      }

      return ctx.canvas || null;
    }

    var CP = window.HTMLCanvasElement && HTMLCanvasElement.prototype;
    var C2 = window.CanvasRenderingContext2D &&
             CanvasRenderingContext2D.prototype;

    if (CP && C2 && (MASK & (CANVAS | WEBGL))) {
      var origGetContext = CP.getContext;
      var origGetImageData = C2.getImageData;
      var origPutImageData = C2.putImageData;
      var origDrawImage = C2.drawImage;

      hook(CP, 'getContext', function (orig) {
        return function getContext(type) {
          var result = Reflect.apply(orig, this, arguments);
          if (result && !htmlCanvasType.has(this)) {
            htmlCanvasType.set(this, String(type || '2d'));
          }
          return result;
        };
      });

      if (MASK & CANVAS) {
        hook(C2, 'getImageData', function (orig) {
          return function getImageData(sx, sy, sw, sh) {
            var image = Reflect.apply(orig, this, arguments);
            try {
              var x = Number(sx) || 0;
              var y = Number(sy) || 0;
              if (Number(sw) < 0) x += Number(sw);
              if (Number(sh) < 0) y += Number(sh);
              perturbPixels(
                image.data, image.width, image.height,
                Math.trunc(x), Math.trunc(y),
                canvasId(this.canvas), 'canvas-pixels'
              );
            } catch (_) {}
            return image;
          };
        });
      }

      function canvasExporter(methodName) {
        hook(CP, methodName, function (orig) {
          return function () {
            try {
              var width = this.width;
              var height = this.height;
              var type = htmlCanvasType.get(this) || '2d';

              if (width > 0 && height > 0 &&
                  width * height <= MAX_PIXELS &&
                  shouldProtectCanvas(type)) {
                var id = canvasId(this);
                var noisy = copyNoisyCanvas(
                  this, width, height,
                  function () {
                    var temp = document.createElement('canvas');
                    temp.width = width;
                    temp.height = height;
                    return origGetContext.call(temp, '2d');
                  },
                  origGetImageData, origPutImageData, origDrawImage,
                  function () { return new ImageData(1, 1); },
                  id, 'canvas-pixels'
                );
                if (noisy) return Reflect.apply(orig, noisy, arguments);
              }
            } catch (_) {
              // 保留原生异常行为，例如 tainted canvas 的 SecurityError。
            }
            return Reflect.apply(orig, this, arguments);
          };
        });
      }

      canvasExporter('toDataURL');
      canvasExporter('toBlob');
    }

    /* ---------------- OffscreenCanvas ---------------- */

    var OCP = window.OffscreenCanvas && OffscreenCanvas.prototype;
    var OC2 = window.OffscreenCanvasRenderingContext2D &&
              OffscreenCanvasRenderingContext2D.prototype;

    if (OCP && (MASK & (CANVAS | WEBGL))) {
      var origOffscreenGetContext = OCP.getContext;
      hook(OCP, 'getContext', function (orig) {
        return function getContext(type) {
          var result = Reflect.apply(orig, this, arguments);
          if (result && !offscreenType.has(this)) {
            offscreenType.set(this, String(type || '2d'));
          }
          return result;
        };
      });

      if (OC2 && (MASK & CANVAS)) {
        hook(OC2, 'getImageData', function (orig) {
          return function getImageData(sx, sy, sw, sh) {
            var image = Reflect.apply(orig, this, arguments);
            try {
              var x = Number(sx) || 0;
              var y = Number(sy) || 0;
              if (Number(sw) < 0) x += Number(sw);
              if (Number(sh) < 0) y += Number(sh);
              perturbPixels(
                image.data, image.width, image.height,
                Math.trunc(x), Math.trunc(y),
                canvasId(this.canvas), 'offscreen-pixels'
              );
            } catch (_) {}
            return image;
          };
        });
      }

      hook(OCP, 'convertToBlob', function (orig) {
        return function convertToBlob() {
          try {
            var width = this.width;
            var height = this.height;
            var type = offscreenType.get(this) || '2d';

            if (width > 0 && height > 0 &&
                width * height <= MAX_PIXELS &&
                shouldProtectCanvas(type)) {
              var source = this;
              var id = canvasId(source);
              var noisy = copyNoisyCanvas(
                source, width, height,
                function () {
                  return new OffscreenCanvas(width, height).getContext('2d');
                },
                function (x, y, w, h) {
                  return Reflect.apply(origOCGetImageData, this, [x, y, w, h]);
                },
                function (image, x, y) {
                  return Reflect.apply(origOCPutImageData, this, [image, x, y]);
                },
                function (src, x, y) {
                  return Reflect.apply(origOCDrawImage, this, [src, x, y]);
                },
                function () { return new ImageData(1, 1); },
                id, 'offscreen-pixels'
              );
              if (noisy) return Reflect.apply(orig, noisy, arguments);
            }
          } catch (_) {}
          return Reflect.apply(orig, this, arguments);
        };
      });
    }

    /* ---------------- WebGL readPixels ---------------- */

    function patchWebGL(W, name) {
      var P = W[name] && W[name].prototype;
      if (!P) return;

      hook(P, 'readPixels', function (orig) {
        return function readPixels(x, y, width, height,
                                   format, type, pixels, dstOffset) {
          var result = Reflect.apply(orig, this, arguments);

          try {
            // 仅处理可直接访问的 RGBA / UNSIGNED_BYTE TypedArray。
            // WebGL2 的 PBO（pixels 为数值偏移）需配合 getBufferSubData 单独处理。
            if (format !== 0x1908 || type !== 0x1401 ||
                !pixels || typeof pixels === 'number' ||
                !ArrayBuffer.isView(pixels) ||
                width <= 0 || height <= 0 ||
                width * height > MAX_PIXELS) return result;

            var gl = this;
            var offset = Number(dstOffset) || 0;
            var rowLength = width;
            var skipRows = 0;
            var skipPixels = 0;
            var alignment = 4;

            if (typeof gl.PACK_ROW_LENGTH === 'number') {
              rowLength = gl.getParameter(gl.PACK_ROW_LENGTH) || width;
              skipRows = gl.getParameter(gl.PACK_SKIP_ROWS) || 0;
              skipPixels = gl.getParameter(gl.PACK_SKIP_PIXELS) || 0;
              alignment = gl.getParameter(gl.PACK_ALIGNMENT) || 4;
            }

            var rowBytes = rowLength * 4;
            var stride = Math.ceil(rowBytes / alignment) * alignment;
            var start = offset + skipRows * stride + skipPixels * 4;
            var required = start + (height - 1) * stride + width * 4;

            if (required > pixels.length) return result;

            var id = canvasId(gl.canvas);
            for (var row = 0; row < height; row++) {
              var rowStart = start + row * stride;
              var rowView = pixels.subarray(rowStart, rowStart + width * 4);
              perturbPixels(
                rowView, width, 1, x, y + row, id, 'webgl-read'
              );
            }
          } catch (_) {}
          return result;
        };
      });
    }

    if (MASK & WEBGL) {
      patchWebGL(window, 'WebGLRenderingContext');
      patchWebGL(window, 'WebGL2RenderingContext');
    }

    /* ---------------- Audio：只扰动读出副本 ---------------- */

    if (MASK & AUDIO) {
      var ABP = window.AudioBuffer && AudioBuffer.prototype;

      // 不包装 getChannelData：它返回 AudioBuffer 内部声道数据，
      // 修改它会改变后续音频处理 / 播放，不符合“读指纹不影响播放”的目标。
      hook(ABP, 'copyFromChannel', function (orig) {
        return function copyFromChannel(destination, channelNumber, bufferOffset) {
          var result = Reflect.apply(orig, this, arguments);
          try {
            if (destination && destination.length <= MAX_PIXELS) {
              var start = Number(bufferOffset) || 0;
              var channel = Number(channelNumber) || 0;
              for (var i = 0; i < destination.length; i += 4) {
                var v = destination[i];
                if (!Number.isFinite(v) || v === 0) continue;
                var n = unitNoise('audio-copy', channel, start + i, 0) - 0.5;
                destination[i] = v + n * 2e-7;
              }
            }
          } catch (_) {}
          return result;
        };
      });

      var AN = window.AnalyserNode && AnalyserNode.prototype;

      function perturbAnalyserArray(array, label, scale, stride) {
        if (!array || !array.length) return;
        for (var i = 0; i < array.length; i += stride) {
          var value = array[i];
          if (!Number.isFinite(value)) continue;
          var n = unitNoise(label, i, array.length, 0) - 0.5;
          if (array instanceof Uint8Array ||
              array instanceof Uint8ClampedArray) {
            array[i] = Math.max(0, Math.min(255, value + (n < 0 ? -1 : 1)));
          } else {
            array[i] = value + n * scale;
          }
        }
      }

      [
        ['getFloatFrequencyData', 'audio-freq-float', 0.02, 2],
        ['getFloatTimeDomainData', 'audio-time-float', 1e-7, 4],
        ['getByteFrequencyData', 'audio-freq-byte', 1, 8],
        ['getByteTimeDomainData', 'audio-time-byte', 1, 8]
      ].forEach(function (entry) {
        hook(AN, entry[0], function (orig) {
          return function () {
            var result = Reflect.apply(orig, this, arguments);
            try {
              perturbAnalyserArray(
                arguments[0], entry[1], entry[2], entry[3]
              );
            } catch (_) {}
            return result;
          };
        });
      });
    }

    /* ---------------- Fonts ---------------- */

    if (MASK & FONTS) {
      var COMMON_FONTS = Object.create(null);
      [
        'arial', 'helvetica', 'times new roman', 'times', 'courier new',
        'courier', 'georgia', 'verdana', 'tahoma', 'trebuchet ms',
        'impact', 'roboto', 'noto sans', 'noto serif', 'droid sans',
        'sans-serif', 'serif', 'monospace', 'system-ui', 'cursive', 'fantasy'
      ].forEach(function (name) { COMMON_FONTS[name] = true; });

      function wrapTextMetrics(ctx, text, metrics) {
        var key = hashString(String(ctx.font) + '|' + String(text));
        var factor = 1 + (((key % 2001) - 1000) / 1000) * 3e-4;
        return new Proxy(metrics, {
          get: function (target, prop, receiver) {
            var value = Reflect.get(target, prop, receiver);
            return typeof value === 'number' ? value * factor : value;
          }
        });
      }

      [C2, OC2].forEach(function (P) {
        hook(P, 'measureText', function (orig) {
          return function measureText(text) {
            var metrics = Reflect.apply(orig, this, arguments);
            try { return wrapTextMetrics(this, text, metrics); }
            catch (_) { return metrics; }
          };
        });
      });

      // FontFace(local()) 保护：只对纯 local() 来源做限制，不拦截包含网络 URL 的字体。
      try {
        var OriginalFontFace = window.FontFace;
        if (OriginalFontFace) {
          var localOnly = new WeakMap();

          var FontFaceProxy = new Proxy(OriginalFontFace, {
            construct: function (target, args, newTarget) {
              var face = Reflect.construct(target, args, newTarget);
              try {
                var source = args[1];
                if (typeof source === 'string' &&
                    /local\s*\(/i.test(source) &&
                    !/url\s*\(/i.test(source)) {
                  var names = [];
                  source.replace(
                    /local\s*\(\s*(['"]?)([^'")]+)\1\s*\)/ig,
                    function (_, quote, name) {
                      names.push(name.trim().toLowerCase());
                      return _;
                    }
                  );
                  localOnly.set(face, names);
                }
              } catch (_) {}
              return face;
            }
          });
          // 保持 FontFace.prototype.constructor === FontFace，并隐藏代理构造器源码差异。
          fakeSrc.set(FontFaceProxy, nativeToString.call(OriginalFontFace));
          try {
            Object.defineProperty(OriginalFontFace.prototype, 'constructor', {
              value: FontFaceProxy, configurable: true, writable: true
            });
          } catch (_) {}
          window.FontFace = FontFaceProxy;

          hook(OriginalFontFace.prototype, 'load', function (orig) {
            return function load() {
              var names = localOnly.get(this);
              if (names && !names.some(function (name) {
                return COMMON_FONTS[name] === true;
              })) {
                return Promise.reject(
                  new DOMException('Local font access is restricted.',
                                   'NotAllowedError')
                );
              }
              return Reflect.apply(orig, this, arguments);
            };
          });
        }
      } catch (_) {}

      // Local Font Access API 实际挂在 Navigator.prototype，而不是 window。
      try {
        var NavigatorPrototype = window.Navigator && Navigator.prototype;
        hook(NavigatorPrototype, 'queryLocalFonts', function (orig) {
          return function queryLocalFonts() {
            return Promise.reject(
              new DOMException('Local font access is restricted.',
                               'NotAllowedError')
            );
          };
        });
      } catch (_) {}
    }

    patchToString(window);

    // 不再从父 frame 向 iframe 复制 MASK / SEED。
    // 每个 frame 必须由 document-start 注入器独立调用原生配置接口，
    // 由 Kotlin 根据 frame URL 与顶层站点计算自己的配置。
  } catch (_) {
    // 指纹保护不得阻断网页加载。
  }
})();
