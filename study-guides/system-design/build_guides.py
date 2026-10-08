#!/usr/bin/env python3
"""Build deterministic, searchable HTML from canonical system-design Markdown."""

from __future__ import annotations

import base64
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "fundamentals" / "guide.md"
TARGET = ROOT / "fundamentals" / "guide.html"


def render_markdown(markdown_text: str) -> str:
    completed = subprocess.run(
        ["pandoc", "--from=gfm", "--to=html5", "--wrap=none"],
        input=markdown_text,
        text=True,
        capture_output=True,
        check=True,
    )
    return completed.stdout


def search_index(markdown_text: str, rendered: str) -> list[dict[str, str]]:
    rendered_headings = re.findall(
        r'<h([12]) id="([^"]+)">(.*?)</h\1>', rendered, flags=re.DOTALL
    )
    markdown_sections = list(
        re.finditer(r"^(#{1,2})\s+(.+?)\s*$", markdown_text, flags=re.MULTILINE)
    )
    if len(rendered_headings) != len(markdown_sections):
        raise RuntimeError(
            f"heading mismatch: rendered={len(rendered_headings)} markdown={len(markdown_sections)}"
        )

    index: list[dict[str, str]] = []
    for position, (rendered_heading, source_heading) in enumerate(
        zip(rendered_headings, markdown_sections, strict=True)
    ):
        level, anchor, rendered_title = rendered_heading
        start = source_heading.end()
        end = markdown_sections[position + 1].start() if position + 1 < len(markdown_sections) else len(markdown_text)
        plain_title = html.unescape(re.sub(r"<[^>]+>", "", rendered_title))
        plain_body = re.sub(r"[`*_>#|\[\]()]", " ", markdown_text[start:end])
        plain_body = re.sub(r"\s+", " ", plain_body).strip()
        index.append({"level": level, "id": anchor, "title": plain_title, "text": plain_body})
    return index


def build() -> None:
    markdown_bytes = SOURCE.read_bytes()
    markdown_text = markdown_bytes.decode("utf-8")
    rendered = render_markdown(markdown_text)
    index = search_index(markdown_text, rendered)
    payload = base64.b64encode(markdown_bytes).decode("ascii")
    digest = hashlib.sha256(markdown_bytes).hexdigest()
    index_json = json.dumps(index, ensure_ascii=False).replace("</", "<\\/")

    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="source-sha256" content="{digest}">
<title>System Design Fundamentals</title>
<style>
:root{{--bg:#f7f8fb;--paper:#fff;--ink:#172033;--muted:#5c667a;--accent:#335eea;--line:#dfe3ec;--code:#111827;--code-ink:#e5e7eb}}
*{{box-sizing:border-box}} html{{scroll-behavior:smooth}} body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.65 system-ui,-apple-system,Segoe UI,sans-serif}}
.layout{{display:grid;grid-template-columns:minmax(240px,310px) minmax(0,920px);gap:2rem;max-width:1280px;margin:auto;padding:1rem}}
aside{{position:sticky;top:1rem;align-self:start;max-height:calc(100vh - 2rem);overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:1rem}}
.brand{{font-weight:800;margin-bottom:.7rem}} input{{width:100%;padding:.72rem;border:1px solid var(--line);border-radius:9px;font:inherit}}
#results{{list-style:none;padding:0;margin:.7rem 0}} #results li{{margin:.45rem 0}} #results a{{text-decoration:none;color:var(--accent);font-size:.92rem}}
#search-status{{color:var(--muted);font-size:.82rem;margin:.45rem 0}} main{{min-width:0;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:clamp(1rem,4vw,3rem)}}
h1,h2,h3,h4{{line-height:1.25;scroll-margin-top:1rem}} h1{{border-bottom:2px solid var(--line);padding-bottom:.35rem}} h2{{margin-top:2.4rem}} a{{color:#264fd2}} table{{border-collapse:collapse;width:100%;display:block;overflow-x:auto}} th,td{{border:1px solid var(--line);padding:.55rem;text-align:left;vertical-align:top}}
pre{{background:var(--code);color:var(--code-ink);padding:1rem;border-radius:10px;overflow:auto}} code{{font:0.9em ui-monospace,SFMono-Regular,Consolas,monospace}} :not(pre)>code{{background:#edf0f6;padding:.12rem .3rem;border-radius:4px}}
blockquote{{margin:1rem 0;padding:.35rem 1rem;border-left:4px solid var(--accent);background:#f0f4ff}} img,svg{{max-width:100%;height:auto}} .mermaid{{overflow:auto;text-align:center}}
.toplink{{display:inline-block;margin-bottom:1rem;font-size:.9rem}} mark{{background:#fff3a3}}
@media(max-width:820px){{.layout{{display:block;padding:.5rem}} aside{{position:relative;max-height:none;margin-bottom:.75rem}} main{{padding:1rem}} #results{{max-height:12rem;overflow:auto}}}}
@media print{{aside{{display:none}} .layout{{display:block}} main{{border:0}}}}
</style>
</head>
<body>
<div class="layout">
<aside>
  <div class="brand">System Design Fundamentals</div>
  <label for="guide-search">Search this guide</label>
  <input id="guide-search" type="search" placeholder="Try: cache stampede, Raft, gRPC" autocomplete="off">
  <div id="search-status">Type to search headings and section text.</div>
  <ul id="results"></ul>
  <a class="toplink" href="#{index[0]['id']}">Back to top</a>
</aside>
<main id="content">{rendered}</main>
</div>
<script id="source-markdown" type="application/octet-stream">{payload}</script>
<script id="search-index" type="application/json">{index_json}</script>
<script>
const input=document.querySelector('#guide-search'), results=document.querySelector('#results'), status=document.querySelector('#search-status');
const index=JSON.parse(document.querySelector('#search-index').textContent);
function escapeHtml(value){{return value.replace(/[&<>"']/g,c=>({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}}[c]));}}
function runSearch(){{
  const q=input.value.trim().toLocaleLowerCase(); results.replaceChildren();
  if(!q){{status.textContent='Type to search headings and section text.';return;}}
  const matches=index.filter(x=>(x.title+' '+x.text).toLocaleLowerCase().includes(q)).slice(0,30);
  status.textContent=matches.length?`${{matches.length}} matching sections (first 30 max).`:'No matching section.';
  for(const item of matches){{const li=document.createElement('li'),a=document.createElement('a');a.href='#'+item.id;a.innerHTML=escapeHtml(item.title);li.append(a);results.append(li);}}
}}
input.addEventListener('input',runSearch); input.addEventListener('keydown',e=>{{if(e.key==='Enter'&&results.firstElementChild)results.querySelector('a').click();}});
</script>
<script type="module">
try {{
  const {{default: mermaid}} = await import('https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs');
  document.querySelectorAll('pre.mermaid').forEach(pre=>{{const div=document.createElement('div');div.className='mermaid';div.textContent=pre.textContent;pre.replaceWith(div);}});
  mermaid.initialize({{startOnLoad:false,securityLevel:'strict',theme:'neutral'}}); await mermaid.run({{querySelector:'.mermaid'}});
}} catch(error) {{ console.warn('Mermaid unavailable; source diagrams remain readable.', error); }}
</script>
</body>
</html>
"""
    TARGET.write_text(page, encoding="utf-8")
    print(f"built {TARGET.relative_to(ROOT)} from {SOURCE.relative_to(ROOT)} sha256={digest}")


if __name__ == "__main__":
    build()
