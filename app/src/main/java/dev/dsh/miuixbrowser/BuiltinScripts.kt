package dev.dsh.miuixbrowser

/** 内置脚本：不进 prefs，按需一键加入脚本列表 */
data class BuiltinScript(
    val name: String,
    val description: String,
    val code: String,
)

const val ADBLOCK_SCRIPT_NAME = "全网查询去广告规则集"

private val ADBLOCK_RULES_JS = """
// ==UserScript==
// @name         全网查询去广告规则集
// @namespace    mafari.builtin
// @version      1.0.0
// @description  通用广告位与弹窗清理规则，按选择器隐藏常见广告容器
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  if (window.__mafariAdRules) return;
  window.__mafariAdRules = true;

  var SELECTORS = [
    '.adsbygoogle',
    'ins.adsbygoogle',
    '[id^="ad-"]',
    '[id^="ads"]',
    '[id^="banner-ad"]',
    '[class^="ad-"]',
    '[class*=" ad-"]',
    '[class*="advert"]',
    '[class*="ad-banner"]',
    '[class*="banner-ad"]',
    '[class*="sponsor"]',
    'iframe[src*="doubleclick.net"]',
    'iframe[src*="googlesyndication"]',
    'iframe[src*="adservice"]',
    'iframe[src*="cpro.baidu.com"]',
    'a[href*="doubleclick.net"]',
    'div[data-ad]',
    'div[data-ads]'
  ];

  function sweep(root) {
    var doc = root || document;
    for (var i = 0; i < SELECTORS.length; i++) {
      var list = [];
      try { list = doc.querySelectorAll(SELECTORS[i]); } catch (e) { continue; }
      for (var j = 0; j < list.length; j++) {
        var el = list[j];
        if (!el) continue;
        if (el.dataset && el.dataset.mafariAdHidden) continue;
        if (el.dataset) el.dataset.mafariAdHidden = '1';
        el.style.setProperty('display', 'none', 'important');
        el.style.setProperty('visibility', 'hidden', 'important');
      }
    }
  }

  function start() {
    sweep(document);
    try {
      var observer = new MutationObserver(function (records) {
        for (var i = 0; i < records.length; i++) {
          var nodes = records[i].addedNodes;
          for (var j = 0; j < nodes.length; j++) {
            var node = nodes[j];
            if (node && node.nodeType === 1) sweep(node.parentNode || node);
          }
        }
      });
      observer.observe(document.documentElement || document, { childList: true, subtree: true });
    } catch (e) {}
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }
})();
"""

val BUILTIN_SCRIPTS = listOf(
    BuiltinScript(
        name = ADBLOCK_SCRIPT_NAME,
        description = "内置通用去广告规则，隐藏常见广告位与广告 iframe，适合资讯 / 视频站点",
        code = ADBLOCK_RULES_JS,
    ),
)
