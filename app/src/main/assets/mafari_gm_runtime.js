/*
 * Mafari userscript runtime.
 * Kotlin (UserscriptEngine.buildSource) substitutes three double-underscore placeholders, each exactly once:
 * the metadata JSON object, the inner function keyword (async for @top-level-await),
 * and the concatenated @require sources + userscript body.
 * This file is intentionally NOT strict-mode: userscripts are frequently sloppy-mode code.
 */
(function () {
  var W = window;
  var META = __MAFARI_META__;

  // ------------------------------------------------------------------ frames / guards
  try { if (META.noframes && W.top !== W.self) return; } catch (e) { if (META.noframes) return; }
  var SEEN = W.__mafariSeen || (W.__mafariSeen = {});
  if (SEEN[META.id]) return;
  SEEN[META.id] = 1;

  var _setTimeout = W.setTimeout.bind(W);
  var _setInterval = W.setInterval.bind(W);
  var _clearInterval = W.clearInterval.bind(W);
  var bridge = null;
  try { bridge = W.MafariGM || null; } catch (e) { bridge = null; }

  function log(level, msg) {
    try { console[level]('[Mafari] ' + META.name + ': ' + msg); } catch (e) {}
  }

  // ------------------------------------------------------------------ URL matching
  function escRe(s) { return s.replace(/[.*+?^${}()|[\]\\\/]/g, '\\$&'); }
  function regexLiteral(p, url) {
    var m = /^\/([\s\S]+)\/([a-z]*)$/.exec(p);
    if (!m) return null;
    try { return new RegExp(m[1], m[2]).test(url); } catch (e) { return false; }
  }
  function globTest(p, url) {
    var r = regexLiteral(p, url);
    if (r !== null) return r;
    if (p === '*') return true;
    var re = new RegExp('^' + p.split('*').map(escRe).join('.*') + '$', 'i');
    return re.test(url) || re.test(url.replace(/#[\s\S]*$/, ''));
  }
  function matchPatternTest(p, href) {
    var r = regexLiteral(p, href);
    if (r !== null) return r;
    if (p === '<all_urls>') return /^(https?|file|ftp):/i.test(href);
    var m = /^(\*|[a-z][a-z0-9+.\-*]*):\/\/([^\/]*)(\/[\s\S]*)?$/i.exec(p);
    if (!m) return globTest(p, href);
    var u;
    try { u = new URL(href); } catch (e) { return false; }
    var proto = u.protocol.slice(0, -1).toLowerCase();
    var sch = m[1].toLowerCase();
    if (sch === '*') {
      if (proto !== 'http' && proto !== 'https') return false;
    } else if (sch.indexOf('*') >= 0) {
      if (!new RegExp('^' + sch.split('*').map(escRe).join('.*') + '$').test(proto)) return false;
    } else if (sch !== proto) {
      return false;
    }
    var hp = m[2].toLowerCase(), port = '';
    var pm = /:(\d+|\*)$/.exec(hp);
    if (pm) { port = pm[1]; hp = hp.slice(0, pm.index); }
    var host = u.hostname.toLowerCase();
    var hostOk;
    if (hp === '*' || hp === '') hostOk = (hp === '*') || host === '';
    else if (hp.indexOf('*.') === 0 && hp.indexOf('*', 1) < 0) {
      var base = hp.slice(2);
      hostOk = host === base || host.slice(-(base.length + 1)) === '.' + base;
    } else if (hp.indexOf('*') >= 0) {
      hostOk = new RegExp('^' + hp.split('*').map(escRe).join('.*') + '$').test(host);
    } else hostOk = host === hp;
    if (!hostOk) return false;
    if (port && port !== '*') {
      var up = u.port || (proto === 'https' ? '443' : proto === 'http' ? '80' : '');
      if (up !== port) return false;
    }
    var pp = m[3] || '/*';
    var re = new RegExp('^' + pp.split('*').map(escRe).join('.*') + '$');
    var target = u.pathname + u.search;
    return re.test(target) || re.test(target + u.hash);
  }
  function anyOf(list, fn, href) {
    for (var i = 0; list && i < list.length; i++) { if (fn(list[i], href)) return true; }
    return false;
  }
  function shouldRun(href) {
    if (anyOf(META.exclude, globTest, href)) return false;
    if (anyOf(META.excludeMatch, matchPatternTest, href)) return false;
    if (META.userMatch && META.userMatch.length) return anyOf(META.userMatch, matchPatternTest, href);
    var hasRules = (META.match && META.match.length) || (META.include && META.include.length);
    if (!hasRules) return true;
    return anyOf(META.match, matchPatternTest, href) || anyOf(META.include, globTest, href);
  }
  if (!shouldRun(W.location.href)) return;

  // ------------------------------------------------------------------ helpers
  function whenRoot(cb) {
    if (document.documentElement) { cb(); return; }
    var mo = new MutationObserver(function () {
      if (document.documentElement) { mo.disconnect(); cb(); }
    });
    mo.observe(document, { childList: true });
  }
  function bytesToB64(bytes) {
    var s = '', CH = 0x8000;
    for (var i = 0; i < bytes.length; i += CH) {
      s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    }
    return btoa(s);
  }
  function b64ToBytes(b64) {
    if (!b64) return new Uint8Array(0);
    var bin = atob(b64), out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  function utf8(str) { return new TextEncoder().encode(str); }
  function decodeText(bytes, charset) {
    try { return new TextDecoder(charset || 'utf-8').decode(bytes); }
    catch (e) { return new TextDecoder('utf-8').decode(bytes); }
  }
  function absUrl(u) {
    try { return new URL(String(u), W.location.href).href; } catch (e) { return String(u); }
  }
  function safeCall(fn, thisArg, args, what) {
    if (typeof fn !== 'function') return;
    try { return fn.apply(thisArg, args); }
    catch (e) { log('error', (what || 'callback') + ' threw: ' + (e && e.message)); }
  }

  // ------------------------------------------------------------------ values
  var mem = {};
  function rawGet(k) {
    try { if (bridge) return bridge.getValue(META.id, String(k)); } catch (e) {}
    return Object.prototype.hasOwnProperty.call(mem, k) ? mem[k] : null;
  }
  function rawSet(k, s) {
    try { if (bridge) { bridge.setValue(META.id, String(k), s); return; } } catch (e) {}
    mem[k] = s;
  }
  function rawDel(k) {
    try { if (bridge) { bridge.deleteValue(META.id, String(k)); return; } } catch (e) {}
    delete mem[k];
  }
  function parseVal(raw, d) {
    if (raw === null || raw === undefined) return d;
    try { return JSON.parse(raw); } catch (e) { return raw; }
  }
  var listeners = {}, listenerSeq = 0, pollTimer = null, lastSeen = {};
  function fireChange(name, oldV, newV, remote) {
    var ids = Object.keys(listeners);
    for (var i = 0; i < ids.length; i++) {
      var l = listeners[ids[i]];
      if (l && l.name === name) safeCall(l.cb, W, [name, oldV, newV, remote], 'value listener');
    }
  }
  function ensurePoll() {
    if (pollTimer) return;
    pollTimer = _setInterval(function () {
      var names = {};
      Object.keys(listeners).forEach(function (id) { names[listeners[id].name] = 1; });
      Object.keys(names).forEach(function (n) {
        var raw = rawGet(n);
        if (raw !== lastSeen[n]) {
          var oldV = parseVal(lastSeen[n], undefined);
          lastSeen[n] = raw;
          fireChange(n, oldV, parseVal(raw, undefined), true);
        }
      });
    }, 1000);
  }
  function GM_getValue(k, d) { return parseVal(rawGet(k), d); }
  function GM_setValue(k, v) {
    var oldRaw = rawGet(k);
    if (v === undefined) { rawDel(k); }
    else {
      var s;
      try { s = JSON.stringify(v); } catch (e) { log('error', 'GM_setValue: value not serializable'); return; }
      if (s === undefined) { rawDel(k); } else { rawSet(k, s); }
    }
    var newRaw = rawGet(k);
    lastSeen[k] = newRaw;
    if (oldRaw !== newRaw) fireChange(k, parseVal(oldRaw, undefined), parseVal(newRaw, undefined), false);
  }
  function GM_deleteValue(k) {
    var oldRaw = rawGet(k);
    rawDel(k);
    lastSeen[k] = null;
    if (oldRaw !== null) fireChange(k, parseVal(oldRaw, undefined), undefined, false);
  }
  function GM_listValues() {
    try { if (bridge) return JSON.parse(bridge.listValues(META.id)); } catch (e) {}
    return Object.keys(mem);
  }
  function GM_getValues(keys) {
    var out = {};
    if (Array.isArray(keys)) keys.forEach(function (k) { out[k] = GM_getValue(k); });
    else if (keys && typeof keys === 'object') Object.keys(keys).forEach(function (k) { out[k] = GM_getValue(k, keys[k]); });
    else GM_listValues().forEach(function (k) { out[k] = GM_getValue(k); });
    return out;
  }
  function GM_setValues(obj) { Object.keys(obj || {}).forEach(function (k) { GM_setValue(k, obj[k]); }); }
  function GM_deleteValues(keys) { (keys || []).forEach(GM_deleteValue); }
  function GM_addValueChangeListener(name, cb) {
    var id = ++listenerSeq;
    listeners[id] = { name: name, cb: cb };
    lastSeen[name] = rawGet(name);
    ensurePoll();
    return id;
  }
  function GM_removeValueChangeListener(id) { delete listeners[id]; }

  // ------------------------------------------------------------------ styles / elements
  function GM_addStyle(css) {
    var st = document.createElement('style');
    st.textContent = css;
    st.setAttribute('data-mafari', META.name);
    whenRoot(function () {
      (document.head || document.documentElement).appendChild(st);
      // CSP may refuse inline <style>; fall back to a constructable stylesheet.
      try {
        if (!st.sheet && document.adoptedStyleSheets !== undefined && typeof CSSStyleSheet === 'function') {
          var sheet = new CSSStyleSheet();
          sheet.replaceSync(css);
          document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
        }
      } catch (e) {}
    });
    return st;
  }
  function GM_addElement(a, b, c) {
    var parent, tag, attrs;
    if (typeof a === 'string') { tag = a; attrs = b; }
    else { parent = a; tag = b; attrs = c; }
    var el = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'textContent' || k === 'innerHTML' || k === 'text') el[k] = attrs[k];
      else el.setAttribute(k, attrs[k]);
    });
    var attach = function () { (parent || document.head || document.documentElement).appendChild(el); };
    if (parent || document.documentElement) attach(); else whenRoot(attach);
    return el;
  }

  // ------------------------------------------------------------------ resources
  function resourceEntry(name) { return META.resources && META.resources[name]; }
  function GM_getResourceText(name) {
    var r = resourceEntry(name);
    return r && r.data ? decodeText(b64ToBytes(r.data)) : null;
  }
  function GM_getResourceURL(name) {
    var r = resourceEntry(name);
    return r && r.data ? 'data:' + (r.mime || 'application/octet-stream') + ';base64,' + r.data : (r ? r.url : null);
  }

  // ------------------------------------------------------------------ xmlHttpRequest
  function headerValue(rawHeaders, name) {
    var re = new RegExp('^' + name + ':\\s*(.*)$', 'im');
    var m = re.exec(rawHeaders || '');
    return m ? m[1].trim() : '';
  }
  function prepareBody(details) {
    var headers = {};
    var src = details.headers || {};
    Object.keys(src).forEach(function (k) { headers[k] = src[k]; });
    function hasHeader(n) { return Object.keys(headers).some(function (k) { return k.toLowerCase() === n; }); }
    var d = details.data;
    var done = function (bytes, ct) {
      if (ct && !hasHeader('content-type')) headers['Content-Type'] = ct;
      return { b64: bytes && bytes.length ? bytesToB64(bytes) : '', headers: headers };
    };
    if (d === undefined || d === null) return Promise.resolve(done(null));
    if (typeof d === 'string') {
      if (details.binary) {
        var b = new Uint8Array(d.length);
        for (var i = 0; i < d.length; i++) b[i] = d.charCodeAt(i) & 0xff;
        return Promise.resolve(done(b));
      }
      return Promise.resolve(done(utf8(d)));
    }
    if (typeof URLSearchParams !== 'undefined' && d instanceof URLSearchParams)
      return Promise.resolve(done(utf8(d.toString()), 'application/x-www-form-urlencoded;charset=UTF-8'));
    if (typeof FormData !== 'undefined' && d instanceof FormData) {
      var req = new Request(W.location.href, { method: 'POST', body: d });
      return req.arrayBuffer().then(function (buf) {
        var ct = req.headers.get('content-type');
        // multipart boundary must match the body we serialized, so it always overrides
        Object.keys(headers).forEach(function (k) { if (k.toLowerCase() === 'content-type') delete headers[k]; });
        return done(new Uint8Array(buf), ct);
      });
    }
    if (typeof Blob !== 'undefined' && d instanceof Blob)
      return d.arrayBuffer().then(function (buf) { return done(new Uint8Array(buf), d.type || ''); });
    if (d instanceof ArrayBuffer) return Promise.resolve(done(new Uint8Array(d)));
    if (ArrayBuffer.isView(d)) return Promise.resolve(done(new Uint8Array(d.buffer, d.byteOffset, d.byteLength)));
    return Promise.resolve(done(utf8(String(d))));
  }
  function pollXhr(reqId, onDone) {
    var delay = 15;
    (function tick() {
      var raw = null;
      try { raw = bridge.xhrPoll(reqId); }
      catch (e) { onDone({ state: 'error', error: String(e) }); return; }
      if (raw) {
        var res;
        try { res = JSON.parse(raw); } catch (e) { res = { state: 'error', error: 'bad bridge response' }; }
        onDone(res);
        return;
      }
      delay = Math.min(delay * 1.5, 150);
      _setTimeout(tick, delay);
    })();
  }
  function fetchFallback(url, method, headers, bodyB64, onDone) {
    var init = { method: method, headers: headers };
    if (bodyB64 && method !== 'GET' && method !== 'HEAD') init.body = b64ToBytes(bodyB64);
    fetch(url, init).then(function (r) {
      return r.arrayBuffer().then(function (buf) {
        var hs = '';
        r.headers.forEach(function (v, k) { hs += k + ': ' + v + '\r\n'; });
        onDone({ state: 'load', status: r.status, statusText: r.statusText, finalUrl: r.url || url, headers: hs, bytes: new Uint8Array(buf) });
      });
    }, function (e) { onDone({ state: 'error', error: String(e && e.message || e) }); });
  }
  function buildResponse(res, details, readyState) {
    var bytes = res.bytes || b64ToBytes(res.body);
    var hdrs = res.headers || '';
    var ct = details.overrideMimeType || headerValue(hdrs, 'content-type');
    var cs = /charset=([^;\s]+)/i.exec(ct || '');
    var rt = String(details.responseType || '').toLowerCase();
    var out = {
      readyState: readyState === undefined ? 4 : readyState,
      status: res.status || 0,
      statusText: res.statusText || '',
      responseHeaders: hdrs,
      finalUrl: res.finalUrl || details.url,
      context: details.context,
      response: null,
      responseText: ''
    };
    var text = null;
    function getText() { if (text === null) text = decodeText(bytes, cs && cs[1]); return text; }
    if (rt === 'arraybuffer') { out.response = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength); }
    else if (rt === 'blob') { out.response = new Blob([bytes], { type: ct || '' }); }
    else if (rt === 'json') { try { out.response = JSON.parse(getText()); } catch (e) { out.response = null; } out.responseText = getText(); }
    else if (rt === 'document') {
      out.responseText = getText();
      try { out.response = new DOMParser().parseFromString(out.responseText, /xml/i.test(ct) ? 'text/xml' : 'text/html'); } catch (e) {}
    } else { out.response = out.responseText = getText(); }
    var xml;
    Object.defineProperty(out, 'responseXML', {
      enumerable: true,
      get: function () {
        if (xml === undefined) {
          try { xml = new DOMParser().parseFromString(getText(), /html/i.test(ct) ? 'text/html' : 'text/xml'); } catch (e) { xml = null; }
        }
        return xml;
      }
    });
    return out;
  }
  function GM_xmlhttpRequest(details) {
    details = details || {};
    var aborted = false, finished = false;
    var reqId = META.id + ':' + Date.now().toString(36) + Math.random().toString(36).slice(2);
    var ctl = {
      abort: function () {
        if (finished || aborted) return;
        aborted = true;
        try { if (bridge) bridge.xhrAbort(reqId); } catch (e) {}
        safeCall(details.onabort, details, [{ readyState: 0, status: 0, finalUrl: details.url, context: details.context }], 'onabort');
        if (details.__reject) details.__reject({ error: 'aborted', abort: true });
      }
    };
    var url = absUrl(details.url);
    var method = String(details.method || 'GET').toUpperCase();
    var base = { readyState: 1, status: 0, finalUrl: url, context: details.context };
    safeCall(details.onloadstart, details, [base], 'onloadstart');
    prepareBody(details).then(function (prep) {
      if (aborted) return;
      var headers = prep.headers;
      if (details.user) {
        headers['Authorization'] = 'Basic ' + btoa(unescape(encodeURIComponent(details.user + ':' + (details.password || ''))));
      }
      if (details.cookie) headers['Cookie'] = details.cookie;
      var onResult = function (res) {
        if (aborted) return;
        finished = true;
        if (res.state === 'abort') return;
        if (res.state === 'load') {
          var resp = buildResponse(res, details, 4);
          safeCall(details.onreadystatechange, details, [resp], 'onreadystatechange');
          safeCall(details.onprogress, details, [{
            lengthComputable: true, loaded: (res.bytes || b64ToBytes(res.body)).length,
            total: (res.bytes || b64ToBytes(res.body)).length, done: 1, position: 0, totalSize: 0,
            finalUrl: resp.finalUrl, context: details.context, status: resp.status, readyState: 4, response: resp.response, responseHeaders: resp.responseHeaders, responseText: resp.responseText, statusText: resp.statusText
          }], 'onprogress');
          safeCall(details.onload, details, [resp], 'onload');
          safeCall(details.onloadend, details, [resp], 'onloadend');
          if (details.__resolve) details.__resolve(resp);
        } else {
          var err = { readyState: 4, status: 0, statusText: '', responseText: '', response: null, responseHeaders: '', finalUrl: url, context: details.context, error: res.error || 'Network error' };
          safeCall(details.onreadystatechange, details, [err], 'onreadystatechange');
          if (res.state === 'timeout') safeCall(details.ontimeout, details, [err], 'ontimeout');
          else safeCall(details.onerror, details, [err], 'onerror');
          safeCall(details.onloadend, details, [err], 'onloadend');
          if (details.__reject) details.__reject(err);
        }
      };
      var useBridge = bridge && typeof bridge.xhrStart === 'function' && /^https?:/i.test(url);
      if (!useBridge) { fetchFallback(url, method, headers, prep.b64, onResult); return; }
      var opts = {
        url: url, method: method, headers: headers, timeout: details.timeout || 0,
        anonymous: !!details.anonymous, ua: navigator.userAgent, lang: navigator.language,
        redirect: details.redirect || 'follow', referrer: W.location.href
      };
      try { bridge.xhrStart(reqId, JSON.stringify(opts), prep.b64); }
      catch (e) { onResult({ state: 'error', error: String(e) }); return; }
      pollXhr(reqId, onResult);
    });
    return ctl;
  }
  function promisifyXhr(details) {
    var ctl;
    var p = new Promise(function (resolve, reject) {
      var d = {};
      Object.keys(details || {}).forEach(function (k) { d[k] = details[k]; });
      d.__resolve = resolve; d.__reject = reject;
      ctl = GM_xmlhttpRequest(d);
    });
    p.abort = function () { if (ctl) ctl.abort(); };
    return p;
  }

  // ------------------------------------------------------------------ misc APIs
  function GM_notification(a, b, c, d) {
    var det = (a && typeof a === 'object') ? a : { text: a, title: b, image: c, onclick: d };
    var title = det.title ? String(det.title) : '';
    var text = det.text !== undefined ? String(det.text) : '';
    try { if (bridge) bridge.toast(title ? title + '：' + text : text); } catch (e) {}
    if (typeof det.ondone === 'function') _setTimeout(function () { safeCall(det.ondone, det, [false], 'ondone'); }, 3500);
    if (typeof b === 'function') _setTimeout(function () { safeCall(b, null, [], 'ondone'); }, 3500);
  }
  function GM_openInTab(url, opts) {
    try { if (bridge) bridge.openTab(absUrl(url)); else W.open(absUrl(url), '_blank'); } catch (e) {}
    var tab = { closed: false, onclose: null, close: function () { tab.closed = true; } };
    return tab;
  }
  function GM_setClipboard(data) {
    var text = String(data);
    try { if (bridge && typeof bridge.copy === 'function') { bridge.copy(text); return; } } catch (e) {}
    try { navigator.clipboard.writeText(text); } catch (e) {
      var ta = document.createElement('textarea');
      ta.value = text; document.documentElement.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch (e2) {}
      ta.remove();
    }
  }
  function GM_download(a, b) {
    var det = (a && typeof a === 'object') ? a : { url: a, name: b };
    var url = absUrl(det.url);
    var name = det.name || '';
    var ctl = { abort: function () {} };
    try {
      if (/^(blob|data):/i.test(url) || !bridge || typeof bridge.download !== 'function') {
        var an = document.createElement('a');
        an.href = url; an.download = name || '';
        document.documentElement.appendChild(an); an.click(); an.remove();
      } else {
        bridge.download(url, String(name));
      }
    } catch (e) { safeCall(det.onerror, det, [{ error: 'not_succeeded', details: String(e) }], 'onerror'); }
    return ctl;
  }
  var menu = W.__mafariMenu;
  if (!menu) {
    menu = { seq: 0, cmds: {} };
    Object.defineProperty(W, '__mafariMenu', { value: menu, configurable: true });
    Object.defineProperty(W, '__mafariMenuList', {
      configurable: true,
      value: function () {
        return JSON.stringify(Object.keys(menu.cmds).map(function (id) {
          return { id: id, title: menu.cmds[id].title, script: menu.cmds[id].script };
        }));
      }
    });
    Object.defineProperty(W, '__mafariMenuRun', {
      configurable: true,
      value: function (id) {
        var c = menu.cmds[id];
        if (!c) return;
        var ev; try { ev = new MouseEvent('click'); } catch (e) { ev = {}; }
        safeCall(c.fn, W, [ev], 'menu command');
      }
    });
  }
  function GM_registerMenuCommand(name, fn, opts) {
    var id = META.id + ':' + (++menu.seq);
    var title = String(name);
    if (opts && typeof opts === 'object' && opts.title) title = String(opts.title);
    menu.cmds[id] = { title: title, fn: fn, script: META.name };
    return id;
  }
  function GM_unregisterMenuCommand(id) { delete menu.cmds[id]; }

  var tabObj = {};
  function GM_getTab(cb) { safeCall(cb, null, [tabObj]); }
  function GM_saveTab(o) { tabObj = o || {}; }
  function GM_getTabs(cb) { var t = {}; t[0] = tabObj; safeCall(cb, null, [t]); }

  function cookieHost(url) { try { return new URL(url).hostname; } catch (e) { return W.location.hostname; } }
  var GM_cookie = {
    list: function (details, cb) {
      details = details || {};
      var url = details.url || W.location.href;
      var out = [];
      try {
        var s = bridge && typeof bridge.cookies === 'function' ? bridge.cookies(url) : document.cookie;
        String(s || '').split(/;\s*/).forEach(function (kv) {
          if (!kv) return;
          var i = kv.indexOf('=');
          var n = i < 0 ? kv : kv.slice(0, i), v = i < 0 ? '' : kv.slice(i + 1);
          if (details.name && details.name !== n) return;
          out.push({ name: n, value: v, domain: cookieHost(url), hostOnly: true, path: '/', secure: /^https:/.test(url), httpOnly: false, session: true });
        });
      } catch (e) {}
      safeCall(cb, null, [out, undefined]);
    },
    set: function (details, cb) {
      try {
        var url = details.url || W.location.href;
        var c = details.name + '=' + (details.value || '') + '; path=' + (details.path || '/');
        if (details.domain) c += '; domain=' + details.domain;
        if (details.secure) c += '; secure';
        if (details.expirationDate) c += '; expires=' + new Date(details.expirationDate * 1000).toUTCString();
        if (bridge && typeof bridge.setCookie === 'function') bridge.setCookie(url, c); else document.cookie = c;
        safeCall(cb, null, [undefined]);
      } catch (e) { safeCall(cb, null, [String(e)]); }
    },
    delete: function (details, cb) {
      try {
        var url = details.url || W.location.href;
        var c = details.name + '=; path=' + (details.path || '/') + '; expires=Thu, 01 Jan 1970 00:00:00 GMT';
        if (bridge && typeof bridge.setCookie === 'function') bridge.setCookie(url, c); else document.cookie = c;
        safeCall(cb, null, [undefined]);
      } catch (e) { safeCall(cb, null, [String(e)]); }
    }
  };

  if (META.grant && META.grant.indexOf('window.onurlchange') >= 0 && !W.__mafariUrlWatch) {
    W.__mafariUrlWatch = true;
    try { W.onurlchange = null; } catch (e) {}
    var lastHref = W.location.href;
    var notify = function () {
      var now = W.location.href;
      if (now === lastHref) return;
      lastHref = now;
      var ev = new Event('urlchange');
      ev.url = now;
      try { W.dispatchEvent(ev); } catch (e) {}
      if (typeof W.onurlchange === 'function') safeCall(W.onurlchange, W, [ev], 'onurlchange');
    };
    ['pushState', 'replaceState'].forEach(function (m) {
      var orig = W.history[m];
      W.history[m] = function () { var r = orig.apply(this, arguments); notify(); return r; };
    });
    W.addEventListener('popstate', notify);
    W.addEventListener('hashchange', notify);
  }

  // ------------------------------------------------------------------ GM_info / GM object
  var m = META.meta || {};
  var GM_info = {
    scriptHandler: 'Tampermonkey',
    version: '5.3.3',
    mafari: true,
    sandboxMode: 'raw',
    isIncognito: false,
    isFirstPartyIsolation: false,
    downloadMode: 'native',
    scriptWillUpdate: false,
    scriptMetaStr: META.metaBlock || '',
    script: {
      name: META.name,
      namespace: META.namespace || '',
      version: META.version || '',
      description: META.description || '',
      author: META.author || '',
      homepage: META.homepage || '',
      icon: META.icon || '',
      matches: META.match || [],
      includes: META.include || [],
      excludes: META.exclude || [],
      'exclude-match': META.excludeMatch || [],
      grant: META.grant || [],
      require: META.require || [],
      resources: Object.keys(META.resources || {}).map(function (n) { return { name: n, url: META.resources[n].url }; }),
      'run-at': META.runAt,
      noframes: !!META.noframes,
      uuid: META.id,
      unwrap: false,
      options: { run_at: META.runAt, noframes: !!META.noframes, override: {} },
      metadata: m
    }
  };
  GM_info.script.name_i18n = {};

  function p(fn) {
    return function () {
      var args = arguments, self = this;
      return new Promise(function (resolve, reject) {
        try { resolve(fn.apply(self, args)); } catch (e) { reject(e); }
      });
    };
  }
  var GM = {
    info: GM_info,
    getValue: p(GM_getValue), setValue: p(GM_setValue), deleteValue: p(GM_deleteValue),
    listValues: p(GM_listValues), getValues: p(GM_getValues), setValues: p(GM_setValues), deleteValues: p(GM_deleteValues),
    addValueChangeListener: p(GM_addValueChangeListener), removeValueChangeListener: p(GM_removeValueChangeListener),
    addStyle: p(GM_addStyle), addElement: p(GM_addElement),
    getResourceText: p(GM_getResourceText), getResourceUrl: p(GM_getResourceURL), getResourceURL: p(GM_getResourceURL),
    xmlHttpRequest: promisifyXhr, xmlhttpRequest: promisifyXhr,
    notification: p(GM_notification), openInTab: p(GM_openInTab), setClipboard: p(GM_setClipboard),
    download: GM_download,
    registerMenuCommand: p(GM_registerMenuCommand), unregisterMenuCommand: p(GM_unregisterMenuCommand),
    getTab: p(function () { return tabObj; }), saveTab: p(GM_saveTab), getTabs: p(function () { var t = {}; t[0] = tabObj; return t; }),
    cookie: {
      list: function (d) { return new Promise(function (res, rej) { GM_cookie.list(d, function (l, e) { e ? rej(e) : res(l); }); }); },
      set: function (d) { return new Promise(function (res, rej) { GM_cookie.set(d, function (e) { e ? rej(e) : res(); }); }); },
      delete: function (d) { return new Promise(function (res, rej) { GM_cookie.delete(d, function (e) { e ? rej(e) : res(); }); }); }
    },
    log: function () { try { console.log.apply(console, arguments); } catch (e) {} }
  };

  var userScript = function (
    GM_info, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_getValues, GM_setValues, GM_deleteValues,
    GM_addValueChangeListener, GM_removeValueChangeListener, GM_addStyle, GM_addElement,
    GM_getResourceText, GM_getResourceURL, GM_xmlhttpRequest, GM_notification, GM_openInTab,
    GM_setClipboard, GM_download, GM_registerMenuCommand, GM_unregisterMenuCommand,
    GM_getTab, GM_saveTab, GM_getTabs, GM_cookie, GM_log, GM, unsafeWindow, define, module, exports
  ) {
    return (__MAFARI_FN__ () {
__MAFARI_CODE__
    }).call(unsafeWindow);
  };

  function execute() {
    var result;
    try {
      result = userScript.call(W,
        GM_info, GM_getValue, GM_setValue, GM_deleteValue, GM_listValues, GM_getValues, GM_setValues, GM_deleteValues,
        GM_addValueChangeListener, GM_removeValueChangeListener, GM_addStyle, GM_addElement,
        GM_getResourceText, GM_getResourceURL, GM_xmlhttpRequest, GM_notification, GM_openInTab,
        GM_setClipboard, GM_download, GM_registerMenuCommand, GM_unregisterMenuCommand,
        GM_getTab, GM_saveTab, GM_getTabs, GM_cookie, function () { try { console.log.apply(console, arguments); } catch (e) {} },
        GM, W, undefined, undefined, undefined);
    } catch (err) {
      log('error', (err && err.stack) || (err && err.message) || err);
      return;
    }
    if (result && typeof result.then === 'function') {
      result.then(null, function (err) { log('error', (err && err.stack) || (err && err.message) || err); });
    }
  }

  // ------------------------------------------------------------------ run-at scheduling
  function waitBody(cb) {
    if (document.body) { cb(); return; }
    var mo = new MutationObserver(function () {
      if (document.body) { mo.disconnect(); cb(); }
    });
    mo.observe(document.documentElement || document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', function () { mo.disconnect(); cb(); }, { once: true });
  }
  (function schedule() {
    var ra = META.runAt || 'document-idle';
    var go = (function () { var done = false; return function () { if (done) return; done = true; execute(); }; })();
    if (ra === 'document-start') { go(); }
    else if (ra === 'document-body') { waitBody(go); }
    else if (ra === 'document-end') {
      if (document.readyState !== 'loading') go();
      else document.addEventListener('DOMContentLoaded', go, { once: true });
    } else {
      if (document.readyState !== 'loading') _setTimeout(go, 0);
      else document.addEventListener('DOMContentLoaded', function () { _setTimeout(go, 0); }, { once: true });
    }
  })();
})();
