import fs from 'node:fs';
import { isProbablyEnglish, MAX_QUOTE_LENGTH, MAX_TAGS, quoteKey } from './quote_seed_rules.mjs';

const seedPath = process.argv[2] || 'app/src/main/res/raw/quotes_seed.json';
const quotes = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
const errors = [];
const seen = new Map();

for (const [index, item] of quotes.entries()) {
  const label = `row ${index + 1}`;
  if (!item.quote?.trim()) errors.push(`${label}: missing quote`);
  if (!item.author?.trim()) errors.push(`${label}: missing author`);
  if (!isProbablyEnglish(item.quote)) errors.push(`${label}: quote is not confidently English: ${item.quote}`);
  if (item.quote.length > MAX_QUOTE_LENGTH) errors.push(`${label}: quote exceeds ${MAX_QUOTE_LENGTH} characters`);
  if (!Array.isArray(item.tags)) errors.push(`${label}: tags must be an array`);
  if (Array.isArray(item.tags) && item.tags.length > MAX_TAGS) errors.push(`${label}: more than ${MAX_TAGS} tags`);
  if (Array.isArray(item.tags) && new Set(item.tags).size !== item.tags.length) errors.push(`${label}: duplicate tags`);

  const key = quoteKey(item.quote);
  if (seen.has(key)) errors.push(`${label}: duplicate quote (first seen in row ${seen.get(key)}): ${item.quote}`);
  else seen.set(key, index + 1);
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

console.log(`Validated ${quotes.length} quotes: English-only, unique, <= ${MAX_TAGS} tags.`);
