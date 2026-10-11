#!/usr/bin/env python3
"""Build the deterministic searchable HTML reader from canonical guide.md."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MARKDOWN = ROOT / "guide.md"
OUTPUT = ROOT / "guide.html"

TEMPLATE = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Angular Interview Deep Study</title><script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script><script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script><style>:root{--bg:#f3f6fa;--paper:white;--ink:#17263b;--line:#d2dcea;--link:#145db7}body{background:var(--bg);color:var(--ink);font:16px/1.65 system-ui,sans-serif;margin:0}header{background:#101f3c;color:white;position:sticky;top:0;padding:12px 18px;display:flex;gap:16px;align-items:center;z-index:9}header input{padding:9px;min-width:110px;max-width:300px;flex:1;border-radius:7px}main{max-width:920px;padding:26px;margin:18px auto;background:var(--paper);border-radius:11px}a{color:var(--link)}pre{background:#101a2d;color:#ebf3ff;padding:14px;border-radius:7px;overflow:auto}table{display:block;overflow-x:auto;border-collapse:collapse}td,th{border:1px solid var(--line);padding:8px}h2{scroll-margin-top:90px}blockquote{border-left:4px solid #4b7ec9;padding:10px;background:#eaf1ff}#results{max-width:920px;margin:auto;padding:0 16px}#results a{display:inline-block;padding:5px 15px 5px 0}.mermaid{overflow-x:auto}@media(max-width:650px){header{flex-wrap:wrap}main{margin:0;padding:16px;border-radius:0}}@media(prefers-color-scheme:dark){:root{--bg:#081020;--paper:#121d30;--ink:#e1ebfd;--line:#36465e;--link:#9cbeff}blockquote{background:#253956}}</style></head><body><header><strong>Angular 22 — Interview Foundations</strong><input id="search" placeholder="Search topics" aria-label="Search section headings"></header><div id="results"></div><main id="article"><pre>JavaScript required to render; see canonical guide.md.</pre></main><script>
const markdown=__MARKDOWN__;
if(window.marked){document.getElementById('article').innerHTML=marked.parse(markdown);for(const p of document.querySelectorAll('pre code.language-mermaid')){const d=document.createElement('div');d.className='mermaid';d.textContent=p.textContent;p.parentNode.replaceWith(d)}if(window.mermaid){mermaid.initialize({startOnLoad:false,securityLevel:'strict'});mermaid.run({querySelector:'.mermaid'})}}
document.getElementById('search').oninput=e=>{const q=e.target.value.toLowerCase();const items=[...document.querySelectorAll('h2,h3')].filter(h=>q&&h.textContent.toLowerCase().includes(q)).slice(0,14);const results=document.getElementById('results');results.replaceChildren();for(const h of items){const a=document.createElement('a');a.textContent=h.textContent;a.href='#'+(h.previousElementSibling?.id||h.id);results.append(a)}};
</script></body></html>
"""


def build() -> None:
    markdown = MARKDOWN.read_text(encoding="utf-8")
    payload = json.dumps(markdown, ensure_ascii=False).replace("</", "<\\/")
    OUTPUT.write_text(TEMPLATE.replace("__MARKDOWN__", payload), encoding="utf-8")


if __name__ == "__main__":
    build()
