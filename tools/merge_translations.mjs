import fs from 'node:fs';
import path from 'node:path';

// Merges translation batches ([{quote, quote_de}]) into res/raw/quotes_de.json in seed order.
// Usage: node tools/merge_translations.mjs <batch-dir> [seed.json] [quotes_de.json]
const batchDir = process.argv[2];
const seedPath = process.argv[3] || 'app/src/main/res/raw/quotes_seed.json';
const outPath = process.argv[4] || 'app/src/main/res/raw/quotes_de.json';

if (!batchDir) {
  console.error('Usage: node tools/merge_translations.mjs <batch-dir> [seed.json] [quotes_de.json]');
  process.exit(1);
}

const seed = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
const seedQuotes = new Set(seed.map((item) => item.quote));
const translations = new Map();
const errors = [];

const files = fs.readdirSync(batchDir).filter((name) => name.endsWith('.json')).sort();
for (const file of files) {
  const rows = JSON.parse(fs.readFileSync(path.join(batchDir, file), 'utf8'));
  for (const [index, row] of rows.entries()) {
    const label = `${file} row ${index + 1}`;
    const german = row.quote_de?.trim();
    if (!seedQuotes.has(row.quote)) errors.push(`${label}: not a seed quote: ${row.quote}`);
    else if (!german) errors.push(`${label}: empty translation`);
    else if (translations.has(row.quote)) errors.push(`${label}: duplicate translation: ${row.quote}`);
    else translations.set(row.quote, german);
  }
}

for (const item of seed) {
  if (!translations.has(item.quote)) errors.push(`missing translation: ${item.quote}`);
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

const lines = seed.map((item) => `  ${JSON.stringify({ quote: item.quote, quote_de: translations.get(item.quote) })}`);
fs.writeFileSync(outPath, `[\n${lines.join(',\n')}\n]\n`);
console.log(`Merged ${translations.size} translations from ${files.length} files into ${outPath}.`);
