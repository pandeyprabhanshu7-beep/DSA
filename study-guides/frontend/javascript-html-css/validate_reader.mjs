#!/usr/bin/env node

import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname, join} from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));
const markdown = readFileSync(join(root, 'guide.md'), 'utf8');
const html = readFileSync(join(root, 'guide.html'), 'utf8');

const payloadMatch = html.match(/const markdown=(.*);\nif\(window\.marked\)/s);
if (!payloadMatch) throw new Error('embedded Markdown payload is missing');
const embedded = JSON.parse(payloadMatch[1].replace(/<\\\//g, '</'));
if (embedded !== markdown) throw new Error('embedded Markdown differs from guide.md');

const anchors = [...markdown.matchAll(/<a id="([^"]+)"><\/a>/g)].map(match => match[1]);
if (new Set(anchors).size !== anchors.length) throw new Error('duplicate explicit anchor');
for (const expected of ['q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'q7', 'q8', 'q9', 'q10', 'q11', 'q12', 'q13', 'q14', 'q15', 'q16', 'q17', 'q18', 'q19', 'q20', 'q21', 'q22', 'q23', 'q24', 'q25', 'q26', 'q27', 'q28', 'q29', 'q30', 'sources', 'next']) {
  if (!anchors.includes(expected)) throw new Error(`missing anchor: ${expected}`);
}

for (const target of [...markdown.matchAll(/\]\(#([^)]+)\)/g)].map(match => match[1])) {
  if (!anchors.includes(target)) throw new Error(`unresolved internal link: #${target}`);
}

const fences = markdown.match(/^\x60\x60\x60/gm) ?? [];
if (fences.length % 2 !== 0) throw new Error('unbalanced fenced code block');

for (const relative of [...markdown.matchAll(/\]\((?!https?:|#)([^)]+)\)/g)].map(match => match[1])) {
  if (relative !== 'examples.mjs') throw new Error(`unexpected relative link: ${relative}`);
}

const inlineScripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(match => match[1]);
if (inlineScripts.length !== 1) throw new Error('expected exactly one inline script');
new Function(inlineScripts[0]);

console.log(JSON.stringify({
  payloadEqual: true,
  markdownCharacters: markdown.length,
  explicitAnchors: anchors.length,
  fencedBlocks: fences.length / 2,
  mermaidDiagrams: (markdown.match(/^\x60\x60\x60mermaid$/gm) ?? []).length,
  inlineJavaScriptParses: true
}));
