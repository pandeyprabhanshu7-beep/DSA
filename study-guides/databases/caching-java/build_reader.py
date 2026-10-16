#!/usr/bin/env python3
"""Build deterministic, responsive, searchable HTML from guide.md."""

from __future__ import annotations

import base64
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "guide.md"
TARGET = ROOT / "guide.html"


def render(markdown: str) -> str:
    return subprocess.run(
        ["pandoc", "--from=gfm", "--to=html5", "--wrap=none"],
        input=markdown,
        text=True,
        capture_output=True,
        check=True,
    ).stdout


def build() -> None:
    source_bytes = SOURCE.read_bytes()
    markdown = source_bytes.decode("utf-8")
    article = render(markdown)
    headings = re.findall(r'<h([1-3]) id="([^"]+)">(.*?)</h\1>', article, re.DOTALL)
    index = [
        {"level": level, "id": anchor, "title": html.unescape(re.sub(r"<[^>]+>", "", title))}
        for level, anchor, title in headings
    ]
    digest = hashlib.sha256(source_bytes).hexdigest()
    payload = base64.b64encode(source_bytes).decode("ascii")
    index_json = json.dumps(index, ensure_ascii=False).replace("</", "<\\/")
    page = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="source-sha256" content="{digest}"><title>Database Internals, Caching, and Java</title>
<style>
:root{{--bg:#eef3f6;--paper:#fff;--ink:#15252d;--muted:#52646d;--line:#cfdae0;--accent:#12656f;--code:#102027;--code-ink:#eaf6f7}}
*{{box-sizing:border-box}}html{{scroll-behavior:smooth}}body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.68 system-ui,-apple-system,Segoe UI,sans-serif}}
.layout{{display:grid;grid-template-columns:minmax(230px,300px) minmax(0,940px);gap:1.4rem;max-width:1280px;margin:auto;padding:1rem}}
aside{{position:sticky;top:1rem;align-self:start;max-height:calc(100vh - 2rem);overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:1rem}}
.brand{{font-weight:800;margin-bottom:.75rem}}input{{width:100%;padding:.7rem;border:1px solid var(--line);border-radius:8px;font:inherit}}
#results{{list-style:none;padding:0;margin:.7rem 0}}#results li{{margin:.42rem 0}}#results a,a{{color:var(--accent)}}#search-status{{color:var(--muted);font-size:.85rem;margin-top:.5rem}}
main{{min-width:0;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:clamp(1rem,4vw,3rem)}}h1,h2,h3{{line-height:1.25;scroll-margin-top:1rem}}h2{{margin-top:2.3rem;border-bottom:2px solid #7fb5b7;padding-bottom:.25rem}}
table{{border-collapse:collapse;width:100%;display:block;overflow-x:auto}}th,td{{border:1px solid var(--line);padding:.55rem;text-align:left;vertical-align:top}}pre{{background:var(--code);color:var(--code-ink);padding:1rem;border-radius:9px;overflow:auto}}code{{font:0.9em ui-monospace,SFMono-Regular,Consolas,monospace}}:not(pre)>code{{background:#e6eff1;padding:.12rem .3rem;border-radius:4px}}blockquote{{margin:1rem 0;padding:.4rem 1rem;border-left:4px solid var(--accent);background:#edf8f7}}.mermaid{{overflow:auto;text-align:center}}
@media(max-width:820px){{.layout{{display:block;padding:.45rem}}aside{{position:relative;max-height:none;margin-bottom:.7rem}}main{{padding:1rem}}}}@media(prefers-color-scheme:dark){{:root{{--bg:#071418;--paper:#102127;--ink:#e5f0f1;--muted:#a9bec1;--line:#365159;--accent:#7bd4d3;--code:#050c0f;--code-ink:#e9f5f5}}:not(pre)>code{{background:#22383d}}blockquote{{background:#173237}}}}@media print{{aside{{display:none}}.layout{{display:block}}main{{border:0}}}}
</style></head><body><div class="layout"><aside><div class="brand">Database Internals + Java</div><label for="search">Search sections</label><input id="search" type="search" placeholder="Try: WAL, idempotency, replica"><div id="search-status">Type to filter section headings.</div><ul id="results"></ul></aside><main id="content">{article}</main></div>
<script id="source-markdown" type="application/octet-stream">{payload}</script><script id="search-index" type="application/json">{index_json}</script>
<script>const input=document.querySelector('#search'),results=document.querySelector('#results'),status=document.querySelector('#search-status'),index=JSON.parse(document.querySelector('#search-index').textContent);function search(){{const q=input.value.trim().toLocaleLowerCase();results.replaceChildren();if(!q){{status.textContent='Type to filter section headings.';return}}const found=index.filter(x=>x.title.toLocaleLowerCase().includes(q)).slice(0,24);status.textContent=found.length?`${{found.length}} matching sections.`:'No matching section.';for(const item of found){{const li=document.createElement('li'),a=document.createElement('a');a.href='#'+item.id;a.textContent=item.title;li.append(a);results.append(li)}}}}input.addEventListener('input',search);</script>
<script type="module">try{{const{{default:mermaid}}=await import('https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs');document.querySelectorAll('pre.mermaid').forEach(pre=>{{const div=document.createElement('div');div.className='mermaid';div.textContent=pre.textContent;pre.replaceWith(div)}});mermaid.initialize({{startOnLoad:false,securityLevel:'strict',theme:'neutral'}});await mermaid.run({{querySelector:'.mermaid'}})}}catch(error){{console.warn('Mermaid unavailable; diagram source remains readable.',error)}}</script>
</body></html>'''
    TARGET.write_text(page, encoding="utf-8")
    print(f"built {TARGET.name} sha256={digest} headings={len(index)}")


if __name__ == "__main__":
    build()
