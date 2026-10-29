#!/usr/bin/env python3
"""Build deterministic, searchable HTML from canonical AI Markdown."""

from __future__ import annotations

import base64
import gzip
import hashlib
import html
import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parent
GUIDES = (
    (
        ROOT / "foundations-rag-cag" / "guide.md",
        ROOT / "foundations-rag-cag" / "guide.html",
        "AI, RAG, CAG and Deep Learning",
        "AI, RAG, CAG and Deep Learning",
        "Try: embeddings, hybrid retrieval, cache key",
    ),
    (
        ROOT / "platform" / "guide.md",
        ROOT / "platform" / "guide.html",
        "AI Platform Engineering",
        "AI Platform Engineering",
        "Try: authority, fine-tuning, idempotency",
    ),
)


def render_markdown(markdown_text: str) -> str:
    completed = subprocess.run(
        ["pandoc", "--from=gfm", "--to=html5", "--wrap=none"],
        input=markdown_text,
        text=True,
        capture_output=True,
        check=True,
    )
    rendered = completed.stdout
    # The source workbook carries explicit anchors immediately before many
    # headings. Pandoc also assigns the same ID to the heading; retain the
    # semantic heading and remove only the redundant empty anchor.
    rendered = re.sub(
        r'<p><a id="([^"]+)"></a></p>\s*(<h[1-6] id="\1">)', r"\2", rendered
    )
    return rendered


def search_index(markdown_text: str, rendered: str) -> list[dict[str, str]]:
    rendered_headings = re.findall(
        r'<h([12]) id="([^"]+)">(.*?)</h\1>', rendered, flags=re.DOTALL
    )
    # A line beginning with # inside a fenced example is code, not a heading.
    # Keep source offsets so the resulting section text still comes from the
    # exact canonical Markdown payload.
    markdown_sections = []
    in_fence = False
    offset = 0
    for line in markdown_text.splitlines(keepends=True):
        if re.match(r"^\s*(```|~~~)", line):
            in_fence = not in_fence
        elif not in_fence:
            match = re.match(r"^(#{1,2})\s+(.+?)\s*$", line.rstrip("\r\n"))
            if match:
                markdown_sections.append((match, offset, offset + len(line)))
        offset += len(line)
    if len(rendered_headings) != len(markdown_sections):
        raise RuntimeError(
            f"heading mismatch: rendered={len(rendered_headings)} markdown={len(markdown_sections)}"
        )

    index: list[dict[str, str]] = []
    for position, (rendered_heading, source_heading) in enumerate(
        zip(rendered_headings, markdown_sections, strict=True)
    ):
        level, anchor, rendered_title = rendered_heading
        _, _, source_end = source_heading
        start = source_end
        end = markdown_sections[position + 1][1] if position + 1 < len(markdown_sections) else len(markdown_text)
        plain_title = html.unescape(re.sub(r"<[^>]+>", "", rendered_title))
        plain_body = re.sub(r"[`*_>#|\[\]()]", " ", markdown_text[start:end])
        plain_body = re.sub(r"\s+", " ", plain_body).strip()
        index.append({"level": level, "id": anchor, "title": plain_title, "text": plain_body})
    return index


def build_one(source: Path, target: Path, title: str, brand: str, placeholder: str) -> None:
    markdown_bytes = source.read_bytes()
    markdown_text = markdown_bytes.decode("utf-8")
    rendered = render_markdown(markdown_text)
    index = search_index(markdown_text, rendered)
    # Keep large standalone readers below common Git/API transfer ceilings
    # without losing exact source recovery. gzip with mtime=0 is deterministic.
    if len(markdown_bytes) > 250_000:
        payload_bytes = gzip.compress(markdown_bytes, mtime=0)
        payload_attribute = ' data-encoding="gzip+base64"'
    else:
        payload_bytes = markdown_bytes
        payload_attribute = ""
    payload = base64.b64encode(payload_bytes).decode("ascii")
    digest = hashlib.sha256(markdown_bytes).hexdigest()
    index_json = json.dumps(index, ensure_ascii=False).replace("</", "<\\/")

    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="source-sha256" content="{digest}">
<title>{html.escape(title)}</title>
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
  <div class="brand">{html.escape(brand)}</div>
  <label for="guide-search">Search this guide</label>
  <input id="guide-search" type="search" placeholder="{html.escape(placeholder)}" autocomplete="off">
  <div id="search-status">Type to search headings and section text.</div>
  <ul id="results"></ul>
  <a class="toplink" href="#{index[0]['id']}">Back to top</a>
</aside>
<main id="content">{rendered}</main>
</div>
<script id="source-markdown" type="application/octet-stream"{payload_attribute}>{payload}</script>
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
    target.write_text(page, encoding="utf-8")
    print(f"built {target.relative_to(ROOT)} from {source.relative_to(ROOT)} sha256={digest}")


def build() -> None:
    for source, target, title, brand, placeholder in GUIDES:
        build_one(source, target, title, brand, placeholder)


if __name__ == "__main__":
    build()
