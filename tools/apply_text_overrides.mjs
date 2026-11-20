// Offline build-time script (NOT shipped in the app).
// Applies TEXT_OVERRIDES and BLOCKED_AUTHORS from quote_seed_rules.mjs to the committed seed, its
// German translations and the curated tags, without downloading the source dataset again. The three
// files are keyed by the exact English text, so they are rewritten together. Safe to re-run.
//
// Usage: node tools/apply_text_overrides.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BLOCKED_AUTHORS, TEXT_OVERRIDES } from './quote_seed_rules.mjs';

const toolsDir = path.dirname(fileURLToPath(import.meta.url));
const rawDir = path.join(toolsDir, '..', 'app', 'src', 'main', 'res', 'raw');
const seedPath = path.join(rawDir, 'quotes_seed.json');
const translationsPath = path.join(rawDir, 'quotes_de.json');
const tagsPath = path.join(toolsDir, 'quote_tags.json');

const seed = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
const blocked = new Set(seed.filter((item) => BLOCKED_AUTHORS.has(item.author)).map((item) => item.quote));
const corrected = (quote) => TEXT_OVERRIDES.get(quote) ?? quote;

const nextSeed = seed
  .filter((item) => !blocked.has(item.quote))
  .map((item) => ({ ...item, quote: corrected(item.quote) }));
fs.writeFileSync(seedPath, JSON.stringify(nextSeed, null, 1) + '\n');

// quotes_de.json and quote_tags.json are written one entry per line; rewrite them line by line so
// the diff only shows the entries that changed.
function rewriteLines(file, keyOf, rekey) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  const out = [];
  for (const line of lines) {
    const key = keyOf(line);
    if (key === null) out.push(line);
    else if (!blocked.has(key)) out.push(rekey(line, key, corrected(key)));
  }
  // Removing the last entry of an object or array leaves a trailing comma behind.
  const text = out.join('\n').replace(/,(\n[\]}])/g, '$1');
  fs.writeFileSync(file, text);
}

rewriteLines(
  translationsPath,
  (line) => (line.startsWith('  {"quote":') ? JSON.parse(line.trim().replace(/,$/, '')).quote : null),
  (line, key, next) => {
    const entry = JSON.parse(line.trim().replace(/,$/, ''));
    return `  ${JSON.stringify({ quote: next, quote_de: entry.quote_de })}${line.trimEnd().endsWith(',') ? ',' : ''}`;
  },
);
rewriteLines(
  tagsPath,
  (line) => {
    const match = /^ {2}("(?:[^"\\]|\\.)*"): /.exec(line);
    return match ? JSON.parse(match[1]) : null;
  },
  (line, key, next) => line.replace(JSON.stringify(key), JSON.stringify(next)),
);

console.log(`Applied ${TEXT_OVERRIDES.size} text corrections; removed ${blocked.size} quote(s) by blocked authors.`);
