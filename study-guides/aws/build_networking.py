#!/usr/bin/env python3
# Requires Python Markdown: python -m pip install markdown
from pathlib import Path
from html import escape
import re,markdown,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent/'networking';assets=root/'assets';dg=assets/'diagrams/networking';dg.mkdir(parents=True,exist_ok=True)
diagrams=[
('00-roadmap','From laptop to AWS',[('Name and address','DNS resolves a name; IP identifies a destination.'),('Local network','Wi-Fi/Ethernet delivers a frame to the next hop.'),('Interconnected networks','Routers forward by prefix across independent networks.'),('Application service','Transport, TLS and HTTP complete the request.')]),
('01-layers','Encapsulation and forwarding',[('Application','HTTP message is carried inside TLS records.'),('Transport','TCP segment adds source and destination ports.'),('Network','IP packet adds addresses; routers choose a next hop.'),('Link','Frame uses local-link addresses; rebuilt at each router.')]),
('02-home-wifi','Home Wi-Fi packet path',[('Laptop','192.168.1.20:51514 sends to server port 443.'),('Home router','PAT maps the private source to a WAN address/port.'),('ISP routers','Forward toward the destination prefix.'),('HTTPS server','Replies to the translated source; NAT reverses mapping.')]),
('03-mobile-data','Cellular access and Internet',[('Phone','Radio connection to base station; app sends IP traffic.'),('Radio access network','Carries user traffic into the operator network.'),('Operator user plane','UPF in 5G / gateway in 4G forwards; NAT may occur.'),('Internet service','IPv4 or IPv6 destination serves the application.')]),
('04-ip-nat','Two translation boundaries',[('Private client','192.168.1.20:51514'),('Home PAT','100.64.1.2:62001 (shared ISP address in this example)'),('Carrier NAT','198.51.100.10:40020 (documentation address)'),('Remote server','203.0.113.80:443; response traverses mappings backwards')]),
('05-routing-bgp','Routing: control plane and data plane',[('Autonomous systems','Operators manage their own routing policy.'),('BGP control plane','Advertises reachability and path attributes between peers.'),('Local forwarding table','Selected routes program next hops; longest prefix wins.'),('IP data plane','Packets follow installed routes, not a BGP session per request.')]),
('06-dns-tcp-tls-http','HTTPS timeline (TCP example)',[('DNS','Resolve hostname; cache may avoid a network query.'),('TCP','SYN, SYN-ACK, ACK establish transport.'),('TLS','Negotiate keys and authenticate server certificate.'),('HTTP','Request and response use encrypted transport; HTTP/3 uses QUIC.')]),
('07-firewalls','Controls at different scopes',[('Subnet boundary','NACL: stateless allow/deny; check both directions.'),('Resource interface','Security group: stateful allow rules.'),('HTTP service','WAF examines supported HTTP request properties.'),('Application','Authentication and authorization still decide access.')]),
('08-legacy-dc','Legacy application path',[('Public edge','DNS and Internet-facing router select an entry path.'),('DMZ / reverse proxy','Firewall and load balancer accept the public request.'),('Private application tier','Backend receives a separate proxy connection.'),('Database tier','Restricted internal traffic; no direct public database access.')]),
('09-aws-ingress','AWS application ingress',[('Client and public endpoint','DNS points to an Internet-facing ALB.'),('Public subnets','ALB receives allowed HTTPS traffic via Internet connectivity.'),('Private application subnet','ALB initiates a separate connection to healthy targets.'),('Private database subnet','Application initiates permitted database traffic.')]),
('10-aws-egress','Public NAT gateway IPv4 egress',[('Private workload','Private subnet default route points to public NAT gateway.'),('Public NAT gateway','Translates source to its private IPv4 address / mapped port.'),('Internet gateway','Maps NAT private address to its associated Elastic IP.'),('Internet destination','Response returns through IGW and NAT mapping to workload.')]),
('11-troubleshooting','Prove the failed layer',[('Name and destination','Check DNS result, IP family and intended endpoint.'),('Reachability and policy','Inspect route associations, SG and both NACL directions.'),('Transport and encryption','Test listener, TCP/QUIC handshake and TLS certificate.'),('Application evidence','Inspect health checks, access logs and request errors.')])]
for name,title,rows in diagrams:
 parts=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 900 580" role="img" aria-labelledby="title desc"><title id="title">{escape(title)}</title><desc id="desc">{escape("; ".join(a+": "+b for a,b in rows))}</desc><rect width="900" height="580" rx="24" fill="#0f172a"/><text x="36" y="52" fill="#f8fafc" font-family="sans-serif" font-size="27">{escape(title)}</text>']
 for i,(label,detail) in enumerate(rows):
  y=82+i*117
  parts.append(f'<rect x="36" y="{y}" width="828" height="91" rx="14" fill="#1e293b" stroke="#38bdf8"/><text x="56" y="{y+31}" fill="#7dd3fc" font-family="sans-serif" font-size="22">{i+1}. {escape(label)}</text>')
  # deterministic wrap with conservative width
  import textwrap
  for j,line in enumerate(textwrap.wrap(detail,87)):
   parts.append(f'<text x="56" y="{y+59+j*22}" fill="#e2e8f0" font-family="sans-serif" font-size="17">{escape(line)}</text>')
  if i<3:parts.append(f'<path d="M450 {y+94} v16 m-6 -6 l6 6 l6 -6" fill="none" stroke="#38bdf8" stroke-width="3"/>')
 parts.append('</svg>');p=dg/(name+'.svg');p.write_text(''.join(parts));ET.parse(p)
p=root/'AWS_Networking_Foundations_Bootcamp.md';s=p.read_text()
exercise='''\n## Worked packet trace: home PAT, CGNAT and return traffic\n\nThis is a simplified **IPv4/TCP teaching example**, not a capture from a real ISP. Addresses 198.51.100.0/24 and 203.0.113.0/24 are documentation ranges. 100.64.0.0/10 is shared address space for carrier NAT; it is distinct from RFC 1918 private space. Port mappings below are illustrative and implementation-dependent.\n\nA laptop opens HTTPS to `203.0.113.80:443`. It uses `192.168.1.20:51514`. Its home router has ISP-facing address `100.64.1.2`. The carrier translates that shared address to `198.51.100.10`.\n\n| Observation point | TCP source | TCP destination | What changed? |\n|---|---|---|---|\n| Laptop to home router | 192.168.1.20:51514 | 203.0.113.80:443 | Original tuple |\n| Home router to ISP | 100.64.1.2:62001 | 203.0.113.80:443 | Home PAT translates source address/port |\n| Carrier to Internet | 198.51.100.10:40020 | 203.0.113.80:443 | CGNAT translates source again |\n| Server response toward carrier | 203.0.113.80:443 | 198.51.100.10:40020 | Source/destination swap for response |\n| Carrier response toward home | 203.0.113.80:443 | 100.64.1.2:62001 | Carrier reverses its destination mapping |\n| Home response toward laptop | 203.0.113.80:443 | 192.168.1.20:51514 | Home reverses its destination mapping |\n\nThe home router retains a mapping for the laptop flow; the carrier retains a second mapping for the router flow. Translation rewrites relevant IP/transport checksums. Link-layer headers are rebuilt at router hops, while TCP sequence numbers belong to the same end-to-end connection in this basic NAT example. A reverse proxy differs: it terminates one transport connection and opens another.\n\n**Why an unsolicited inbound SYN usually fails:** it has no corresponding NAT mapping; forwarding a port on the home router alone does not create a carrier-side mapping. Mapping expiry can also explain why a long-idle connection stops receiving traffic. NAT translation is not a replacement for firewall policy or application authentication.\n\n### Map the idea into AWS without conflating the two designs\n\nFor a standard public-NAT IPv4 Internet path, a private workload routes to a public NAT gateway; the NAT maps its source to the NAT gateway's private address, and the Internet gateway maps that address to the associated Elastic IP. The public subnet needs an Internet gateway route, and the workload subnet needs the NAT route. Return traffic follows the established translations. Security groups track allowed flows; NACLs are stateless and must permit both directions, including required return destination ports.\n\n**Check your understanding:** which source IP does the Internet server observe? In the home example: 198.51.100.10. In the AWS Internet-egress example: the NAT gateway's Elastic IP. Does either setup make the original private client directly reachable by unsolicited traffic? No.\n\nSources: [RFC 6598 shared address space](https://www.rfc-editor.org/rfc/rfc6598), [RFC 5737 documentation addresses](https://www.rfc-editor.org/rfc/rfc5737), [AWS NAT gateway behavior](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-nat-gateway.html), [AWS security groups](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html), [AWS NACLs](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-network-acls.html).\n\n'''
marker='\n# Routing, ISPs, Autonomous Systems, BGP, Peering & Transit'
assert marker in s
if '## Worked packet trace: home PAT, CGNAT and return traffic' not in s:
 s=s.replace(marker,'\n'+exercise+marker,1)
# Locate chapter boundaries outside code fences.
lines=s.splitlines(True);fenced=False;starts=[];offset=0
for line in lines:
 if line.startswith('```'):fenced=not fenced
 if not fenced and line.startswith('# ') and not line.startswith('# AWS Networking Foundations'):starts.append((offset,line[2:].strip()))
 offset+=len(line)
names=['00-networking-roadmap','01-layers-frames-packets-ports','02-home-wifi-to-internet','03-mobile-data-to-internet','04-ip-subnetting-nat-cgnat','05-routing-isp-bgp-peering','06-dns-tcp-tls-http','07-ingress-egress-firewalls-dmz','08-legacy-datacenter-networking','09-aws-internet-vpc-packet-flow','10-network-troubleshooting-labs','11-networking-cheatsheet']
assert len(starts)==len(names),(len(starts),starts)
chapters=list(zip(names,[title for _,title in starts]))
# Resolve seeded references to real canonical files. Chapter paths live beside bootcamp.
def fix(md):
 md=re.sub(r'md/networking/([^)\s]+)',r'\1',md)
 md=md.replace('md/modules/vpc-networking.md','09-aws-internet-vpc-packet-flow.md')
 md=md.replace('md/modules/','../modules/')
 md=md.replace('[Course home](index.md)','[Course home](../index.md)')
 md=md.replace('[Separate networking lecture index](../index.md)','[Separate networking lecture index](index.md)')
 md=md.replace('(networking/index.md)','(index.md)')
 # Specific index links in source
 md=md.replace('(md/networking/index.md)','(index.md)')
 return md
s=fix(s)
# Remaining unpromoted module references become clear prose instead of dead links.
s=re.sub(r'\[([^\]]+)\]\((?:\.\./modules/[^)]+)\)',r'\1 (module promotion pending)',s)
p.write_text(s)
# Recompute boundaries after link repairs changed offsets.
lines=s.splitlines(True);fenced=False;starts=[];offset=0
for line in lines:
 if line.startswith('```'):fenced=not fenced
 if not fenced and line.startswith('# ') and not line.startswith('# AWS Networking Foundations'):starts.append((offset,line[2:].strip()))
 offset+=len(line)
css='''*{box-sizing:border-box}body{margin:0;background:#f4f7fb;color:#172033;font:17px/1.7 system-ui,sans-serif}a{color:#145cb0}.layout{display:grid;grid-template-columns:290px minmax(0,1fr)}aside{position:sticky;top:0;height:100vh;overflow:auto;background:#fff;padding:24px;border-right:1px solid #d6dfeb}aside a{display:block;padding:7px 0}input{width:100%;padding:10px;border:1px solid #a8b6ca;border-radius:8px}main{max-width:1100px;padding:30px 40px;min-width:0}article{background:#fff;padding:30px;border-radius:18px}h1,h2,h3{line-height:1.25;scroll-margin-top:18px}h1{margin-top:50px}img{max-width:100%;height:auto}pre{background:#0f172a;color:#e2e8f0;padding:20px;overflow:auto;border-radius:10px}code{font-family:monospace}table{display:block;overflow:auto;border-collapse:collapse}td,th{border:1px solid #d6dfeb;padding:10px;min-width:130px;text-align:left}th{background:#edf4fd}blockquote{border-left:4px solid #1c78c4;padding:8px 18px;background:#edf4fd}.pager{display:flex;justify-content:space-between;gap:18px;margin:22px 0}.toc{border:1px solid #d6dfeb;padding:16px}.toc ul{max-height:300px;overflow:auto}@media(max-width:800px){.layout{display:block}aside{position:static;height:auto}aside nav{max-height:210px;overflow:auto}main{padding:12px}article{padding:18px;font-size:16px}}@media print{aside,input,.pager{display:none}.layout{display:block}main{padding:0}article{padding:0}}'''
(assets/'site.css').write_text(css)
(assets/'site.js').write_text("document.querySelector('#navSearch')?.addEventListener('input',e=>{const q=e.target.value.toLowerCase();document.querySelectorAll('aside nav a').forEach(a=>a.hidden=!a.textContent.toLowerCase().includes(q))});")
def html_doc(md,title,previous=None,next_=None):
 # HTML crosslinks use matching reader editions.
 md=re.sub(r'\]\(([^)\s]+)\.md(#[^)]*)?\)',lambda m:']('+m[1]+'.html'+(m[2] or '')+')',md)
 content=markdown.markdown(md,extensions=['fenced_code','tables','toc','sane_lists'],extension_configs={'toc':{'toc_depth':'1-2'}})
 toc=markdown.Markdown(extensions=['fenced_code','tables','toc']);toc.convert(md)
 nav=''.join('<a href="'+name+'.html">'+escape(t)+'</a>' for name,t in chapters)
 pager='<div class="pager">'+('<a href="'+previous+'.html">← Previous chapter</a>' if previous else '<a href="../index.html">Course home</a>')+('<a href="'+next_+'.html">Next chapter →</a>' if next_ else '<a href="index.html">Chapter index</a>')+'</div>'
 return '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>'+escape(title)+'</title><link rel="stylesheet" href="assets/site.css"></head><body><div class="layout"><aside><h2>Networking bootcamp</h2><input id="navSearch" aria-label="Filter chapter titles" placeholder="Filter chapters"><nav><a href="../index.html">AWS course home</a><a href="index.html">Chapter index</a>'+nav+'</nav></aside><main>'+pager+'<article><details class="toc"><summary>On this page</summary>'+toc.toc+'</details>'+content+'</article>'+pager+'</main></div><script src="assets/site.js"></script></body></html>'
for i,(name,title) in enumerate(chapters):
 start=starts[i][0];end=starts[i+1][0] if i+1<len(starts) else len(s);part=s[start:end]
 prev=names[i-1] if i else None;nxt=names[i+1] if i+1<len(names) else None
 part+='\n\n'+('[← Previous]('+prev+'.md) · ' if prev else '')+'[Chapter index](index.md)'+(' · [Next →]('+nxt+'.md)' if nxt else '')+'\n'
 (root/(name+'.md')).write_text(part);(root/(name+'.html')).write_text(html_doc(part,title,prev,nxt))
(root/'AWS_Networking_Foundations_Bootcamp.html').write_text(html_doc(s,'AWS Networking Foundations Bootcamp'))
index='# Networking chapter index\n\n[Full AWS course](../index.md) · [Combined bootcamp](AWS_Networking_Foundations_Bootcamp.md)\n\n'+ '\n'.join(f'{i+1}. [{title}]({name}.md)' for i,(name,title) in enumerate(chapters))+'\n'
(root/'index.md').write_text(index);(root/'index.html').write_text(html_doc(index,'Networking chapter index'))
print('Repaired combined pair; created 12 chapter pairs, 12 SVGs and local CSS/JS.')
