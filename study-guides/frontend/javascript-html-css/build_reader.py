#!/usr/bin/env python3
"""Build the deterministic searchable HTML reader from canonical guide.md."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MARKDOWN = ROOT / "guide.md"
OUTPUT = ROOT / "guide.html"

TEMPLATE = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JavaScript Fundamentals Interview Guide</title><script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script><script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script><style>:root{--bg:#f5f7fb;--paper:#fff;--ink:#17243a;--line:#d4deeb;--link:#095fad;--accent:#f3c969}*{box-sizing:border-box}body{background:var(--bg);color:var(--ink);font:16px/1.65 system-ui,sans-serif;margin:0}header{background:#172033;color:#fff;position:sticky;top:0;padding:12px 18px;display:flex;gap:16px;align-items:center;z-index:9}header strong{white-space:nowrap}header input{padding:10px 12px;min-width:120px;max-width:360px;flex:1;border:1px solid #9fb0c9;border-radius:8px}main{max-width:980px;padding:30px;margin:20px auto;background:var(--paper);border-radius:12px;box-shadow:0 4px 24px #1a29431a}a{color:var(--link)}pre{background:#111b2e;color:#eef4ff;padding:15px;border-radius:8px;overflow:auto}code{overflow-wrap:anywhere}table{display:block;overflow-x:auto;border-collapse:collapse}td,th{border:1px solid var(--line);padding:8px;text-align:left}h2{scroll-margin-top:90px;border-bottom:2px solid var(--accent);padding-bottom:4px}blockquote{border-left:4px solid #4d7eb7;padding:10px 14px;background:#edf4ff;margin-left:0}#results{max-width:980px;margin:auto;padding:0 18px}#results a{display:inline-block;padding:6px 18px 6px 0}.mermaid{overflow-x:auto;text-align:center}@media(max-width:680px){header{flex-wrap:wrap}header strong{white-space:normal}main{margin:0;padding:18px;border-radius:0}table{font-size:14px}}@media(prefers-color-scheme:dark){:root{--bg:#08101f;--paper:#121d30;--ink:#e2ebfa;--line:#394a63;--link:#9cc7ff;--accent:#a47b28}blockquote{background:#203450}}</style></head><body><header><strong>JavaScript Fundamentals — Q1–Q56</strong><input id="search" placeholder="Search sections" aria-label="Search section headings"></header><div id="results" aria-live="polite"></div><main id="article"><pre>JavaScript required to render; see canonical guide.md.</pre></main><script>
const markdown=__MARKDOWN__;
if(window.marked){document.getElementById('article').innerHTML=marked.parse(markdown);for(const p of document.querySelectorAll('pre code.language-mermaid')){const d=document.createElement('div');d.className='mermaid';d.textContent=p.textContent;p.parentNode.replaceWith(d)}if(window.mermaid){mermaid.initialize({startOnLoad:false,securityLevel:'strict'});mermaid.run({querySelector:'.mermaid'})}}
document.getElementById('search').oninput=e=>{const q=e.target.value.toLowerCase().trim();const items=[...document.querySelectorAll('h2,h3')].filter(h=>q&&h.textContent.toLowerCase().includes(q)).slice(0,16);const results=document.getElementById('results');results.replaceChildren();for(const h of items){const a=document.createElement('a');a.textContent=h.textContent;a.href='#'+(h.previousElementSibling?.id||h.id);results.append(a)}};
</script></body></html>
"""


def build() -> None:
    markdown = MARKDOWN.read_text(encoding="utf-8")
    payload = json.dumps(markdown, ensure_ascii=False).replace("</", "<\\/")
    OUTPUT.write_text(TEMPLATE.replace("__MARKDOWN__", payload), encoding="utf-8")


if __name__ == "__main__":
    build()
