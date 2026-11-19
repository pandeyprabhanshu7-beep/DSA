#!/usr/bin/env python3
"""Build deterministic searchable readers for focused advanced-tree guides."""

from __future__ import annotations

import base64
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent
GUIDES = (
    (
        ROOT / "tree-selection-decision-matrix.md",
        ROOT / "tree-selection-decision-matrix.html",
        "Advanced Trees — Requirement-First Selection Matrix",
        "Advanced Trees",
        "Try: heap, range, B+ tree",
    ),
    (
        ROOT / "faang-problem-ladder.md",
        ROOT / "faang-problem-ladder.html",
        "Advanced Trees — FAANG Problem Ladder",
        "Tree Problem Ladder",
        "Try: ancestor bounds, kth, Fenwick",
    ),
)


def source_sections(markdown: str) -> list[tuple[int, str, int, int]]:
    sections: list[tuple[int, str, int, int]] = []
    in_fence = False
    offset = 0
    for line in markdown.splitlines(keepends=True):
        if re.match(r"^\s*(```|~~~)", line):
            in_fence = not in_fence
        elif not in_fence:
            match = re.match(r"^(#{1,2})\s+(.+?)\s*$", line.rstrip("\r\n"))
            if match:
                sections.append((len(match.group(1)), match.group(2), offset, offset + len(line)))
        offset += len(line)
    return sections


def build_one(source: Path, target: Path, title_text: str, brand: str, placeholder: str) -> None:
    source_bytes = source.read_bytes()
    markdown = source_bytes.decode("utf-8")
    rendered = subprocess.run(
        ["pandoc", "--from=gfm", "--to=html5", "--wrap=none"],
        input=markdown,
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    headings = re.findall(r'<h([12]) id="([^"]+)">(.*?)</h\1>', rendered, re.DOTALL)
    sections = source_sections(markdown)
    if len(headings) != len(sections):
        raise RuntimeError(f"heading mismatch: rendered={len(headings)} source={len(sections)}")

    index = []
    for i, ((level, anchor, rendered_title), (_, _, _, body_start)) in enumerate(zip(headings, sections, strict=True)):
        body_end = sections[i + 1][2] if i + 1 < len(sections) else len(markdown)
        title = html.unescape(re.sub(r"<[^>]+>", "", rendered_title))
        body = re.sub(r"[`*_>#|\[\]()]", " ", markdown[body_start:body_end])
        body = re.sub(r"\s+", " ", body).strip()
        index.append({"level": level, "id": anchor, "title": title, "text": body})

    digest = hashlib.sha256(source_bytes).hexdigest()
    payload = base64.b64encode(source_bytes).decode("ascii")
    index_json = json.dumps(index, ensure_ascii=False).replace("</", "<\\/")
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="source-sha256" content="{digest}">
<title>{html.escape(title_text)}</title>
<style>
:root{{--bg:#f4f7fb;--paper:#fff;--ink:#172033;--muted:#5c667a;--accent:#1261a0;--line:#d3dce9;--code:#101c30;--code-ink:#eef4ff}}
*{{box-sizing:border-box}}html{{scroll-behavior:smooth}}body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.68 system-ui,-apple-system,Segoe UI,sans-serif}}
.layout{{display:grid;grid-template-columns:minmax(240px,310px) minmax(0,1040px);gap:1.4rem;max-width:1380px;margin:auto;padding:1rem}}
aside{{position:sticky;top:1rem;align-self:start;max-height:calc(100vh - 2rem);overflow:auto;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:1rem}}
main{{min-width:0;background:var(--paper);border:1px solid var(--line);border-radius:14px;padding:clamp(1rem,4vw,3rem)}}
.brand{{font-weight:800;margin-bottom:.7rem}}input{{width:100%;padding:.7rem;border:1px solid var(--line);border-radius:9px;font:inherit}}
#results{{list-style:none;padding:0;margin:.7rem 0}}#results li{{margin:.42rem 0}}#results a{{color:var(--accent);text-decoration:none;font-size:.92rem}}#search-status{{color:var(--muted);font-size:.82rem;margin:.45rem 0}}
h1,h2,h3{{line-height:1.25;scroll-margin-top:1rem}}h1{{border-bottom:2px solid var(--line);padding-bottom:.35rem}}h2{{margin-top:2.3rem}}a{{color:var(--accent)}}
table{{border-collapse:collapse;width:100%;display:block;overflow-x:auto}}th,td{{border:1px solid var(--line);padding:.55rem;text-align:left;vertical-align:top}}
pre{{background:var(--code);color:var(--code-ink);padding:1rem;border-radius:10px;overflow:auto}}code{{font:0.9em ui-monospace,SFMono-Regular,Consolas,monospace}}:not(pre)>code{{background:#edf2f7;padding:.12rem .3rem;border-radius:4px}}
blockquote{{margin:1rem 0;padding:.4rem 1rem;border-left:4px solid var(--accent);background:#eaf2fa}}.mermaid{{overflow:auto;text-align:center}}
@media(max-width:820px){{.layout{{display:block;padding:.5rem}}aside{{position:relative;max-height:none;margin-bottom:.75rem}}main{{padding:1rem}}#results{{max-height:12rem;overflow:auto}}}}
@media print{{aside{{display:none}}.layout{{display:block}}main{{border:0}}}}
</style>
</head>
<body>
<div class="layout">
<aside><div class="brand">{html.escape(brand)}</div><label for="guide-search">Search this guide</label><input id="guide-search" type="search" placeholder="{html.escape(placeholder)}" autocomplete="off"><div id="search-status">Type to search headings and section text.</div><ul id="results"></ul><a href="#{index[0]['id']}">Back to top</a></aside>
<main id="content">{rendered}</main>
</div>
<script id="source-markdown" type="application/octet-stream">{payload}</script>
<script id="search-index" type="application/json">{index_json}</script>
<script>
const input=document.querySelector('#guide-search'),results=document.querySelector('#results'),status=document.querySelector('#search-status');
const index=JSON.parse(document.querySelector('#search-index').textContent);
function escapeHtml(value){{return value.replace(/[&<>"']/g,c=>({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}}[c]));}}
function search(){{const q=input.value.trim().toLocaleLowerCase();results.replaceChildren();if(!q){{status.textContent='Type to search headings and section text.';return;}}const matches=index.filter(x=>(x.title+' '+x.text).toLocaleLowerCase().includes(q)).slice(0,30);status.textContent=matches.length?`${{matches.length}} matching sections (first 30 max).`:'No matching section.';for(const item of matches){{const li=document.createElement('li'),a=document.createElement('a');a.href='#'+item.id;a.innerHTML=escapeHtml(item.title);li.append(a);results.append(li);}}}}
input.addEventListener('input',search);input.addEventListener('keydown',event=>{{if(event.key==='Enter'&&results.firstElementChild)results.querySelector('a').click();}});
</script>
<script type="module">
try{{const{{default:mermaid}}=await import('https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs');document.querySelectorAll('pre.mermaid').forEach(pre=>{{const div=document.createElement('div');div.className='mermaid';div.textContent=pre.textContent;pre.replaceWith(div);}});mermaid.initialize({{startOnLoad:false,securityLevel:'strict',theme:'neutral'}});await mermaid.run({{querySelector:'.mermaid'}});}}catch(error){{console.warn('Mermaid unavailable; source diagrams remain readable.',error);}}
</script>
</body>
</html>
"""
    target.write_text(page, encoding="utf-8")
    print(f"built {target.name} sha256={digest} search_entries={len(index)}")


def build() -> None:
    for guide in GUIDES:
        build_one(*guide)


if __name__ == "__main__":
    build()
