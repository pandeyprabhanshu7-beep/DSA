#!/usr/bin/env node

import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const markdown = readFileSync(join(root, 'guide.md'));
const html = readFileSync(join(root, 'guide.html'), 'utf8');
const source = Buffer.from(html.match(/<script id="source-markdown"[^>]*>([^<]+)<\/script>/)?.[1] ?? '', 'base64');
if (!source.equals(markdown)) throw new Error('embedded Markdown differs from guide.md');

const digest = createHash('sha256').update(markdown).digest('hex');
if (!html.includes(`<meta name="source-sha256" content="${digest}">`)) throw new Error('source hash metadata mismatch');

const ids = [...html.matchAll(/\sid="([^"]+)"/g)].map(match => match[1]);
if (new Set(ids).size !== ids.length) throw new Error('duplicate HTML id');
for (const target of [...html.matchAll(/href="#([^"]+)"/g)].map(match => match[1])) {
  if (!ids.includes(target)) throw new Error(`missing fragment target: #${target}`);
}

const indexText = html.match(/<script id="search-index"[^>]*>([\s\S]*?)<\/script>/)?.[1];
if (!indexText) throw new Error('search index missing');
const index = JSON.parse(indexText);
if (index.length < 12) throw new Error('search index unexpectedly small');
for (const item of index) if (!ids.includes(item.id)) throw new Error(`search target missing: ${item.id}`);

const allowedRelative = new Set([
  'ConsistencyContracts.java',
  '../../local-project-notes/advanced-trees/b-plus-tree-database-index.md'
]);
for (const relative of [...markdown.toString('utf8').matchAll(/\]\((?!https?:|#)([^)]+)\)/g)].map(match => match[1])) {
  if (!allowedRelative.has(relative)) throw new Error(`unexpected relative link: ${relative}`);
}
const fences = markdown.toString('utf8').match(/^```/gm) ?? [];
if (fences.length % 2) throw new Error('unbalanced Markdown fence');
if (!html.includes("securityLevel:'strict'")) throw new Error('Mermaid strict mode missing');

console.log(JSON.stringify({
  payloadEqual: true,
  sourceSha256: digest,
  markdownCharacters: markdown.toString('utf8').length,
  htmlIds: ids.length,
  searchEntries: index.length,
  fencedBlocks: fences.length / 2,
  mermaidDiagrams: (markdown.toString('utf8').match(/^```mermaid$/gm) ?? []).length
}));
