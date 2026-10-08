from pathlib import Path
import hashlib, html, re
import markdown
root = Path(__file__).resolve().parent
style = 'body{font:17px/1.7 system-ui;color:#172033;background:#f4f7fb;margin:0}main{max-width:1040px;margin:auto;padding:24px}article{background:white;padding:28px;border-radius:16px}a{color:#1261a0}h1,h2,h3{line-height:1.25}img{max-width:100%;height:auto}pre{background:#101c30;color:#eef4ff;padding:18px;overflow:auto;border-radius:10px}table{display:block;overflow:auto;border-collapse:collapse}th,td{border:1px solid #d3dce9;padding:10px;text-align:left}details{background:#eaf2fa;padding:12px}.toc ul{max-height:280px;overflow:auto}input{padding:10px;width:min(100%,420px)}@media(max-width:600px){main{padding:8px}article{padding:16px;font-size:16px}}'
for path in root.rglob('*.md'):
    source = path.read_text()
    text = re.sub(r'\]\(([^)\s]+)\.md(#[^)]*)?\)', lambda m: '](' + m[1] + '.html' + (m[2] or '') + ')', source)
    parser = markdown.Markdown(extensions=['tables', 'fenced_code', 'toc'])
    content = parser.convert(text)
    title = next((line.lstrip('# ') for line in source.splitlines() if line.startswith('# ')), path.stem)
    digest = hashlib.sha256(source.encode()).hexdigest()
    doc = '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="source-sha256" content="'+digest+'"><title>'+html.escape(title)+'</title><style>'+style+'</style></head><body><main><article><input id="filter" aria-label="Filter section links" placeholder="Filter section links"><details open class="toc"><summary>Sections</summary>'+parser.toc+'</details>'+content+'</article></main><script>document.querySelector("#filter").oninput=e=>{let q=e.target.value.toLowerCase();document.querySelectorAll(".toc li").forEach(li=>li.hidden=!li.textContent.toLowerCase().includes(q))}</script></body></html>'
    path.with_suffix('.html').write_text(doc)
