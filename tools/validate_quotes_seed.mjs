import fs from 'node:fs';
import { authorKey, canonicalAuthor, isProbablyEnglish, MAX_QUOTE_LENGTH, MAX_TAGS, quoteKey } from './quote_seed_rules.mjs';

const seedPath = process.argv[2] || 'app/src/main/res/raw/quotes_seed.json';
const translationsPath = process.argv[3] || 'app/src/main/res/raw/quotes_de.json';
const quotes = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
const errors = [];
const seen = new Map();
const authorSpellings = new Map();

for (const [index, item] of quotes.entries()) {
  const label = `row ${index + 1}`;
  if (!item.quote?.trim()) errors.push(`${label}: missing quote`);
  if (!item.author?.trim()) errors.push(`${label}: missing author`);
  if (!isProbablyEnglish(item.quote)) errors.push(`${label}: quote is not confidently English: ${item.quote}`);
  if (item.quote.length > MAX_QUOTE_LENGTH) errors.push(`${label}: quote exceeds ${MAX_QUOTE_LENGTH} characters`);
  if (!Array.isArray(item.tags)) errors.push(`${label}: tags must be an array`);
  if (Array.isArray(item.tags) && item.tags.length > MAX_TAGS) errors.push(`${label}: more than ${MAX_TAGS} tags`);
  if (Array.isArray(item.tags) && new Set(item.tags).size !== item.tags.length) errors.push(`${label}: duplicate tags`);

  if (item.author && canonicalAuthor(item.author) !== item.author) {
    errors.push(`${label}: author is not canonical: ${item.author} (expected ${canonicalAuthor(item.author)})`);
  }
  const spelling = authorSpellings.get(authorKey(item.author));
  if (spelling && spelling !== item.author) errors.push(`${label}: author spelled differently elsewhere: ${item.author} / ${spelling}`);
  else authorSpellings.set(authorKey(item.author), item.author);

  const key = quoteKey(item.quote);
  if (seen.has(key)) errors.push(`${label}: duplicate quote (first seen in row ${seen.get(key)}): ${item.quote}`);
  else seen.set(key, index + 1);
}

const translated = new Map();
for (const [index, item] of JSON.parse(fs.readFileSync(translationsPath, 'utf8')).entries()) {
  const label = `translation row ${index + 1}`;
  if (!item.quote_de?.trim()) errors.push(`${label}: empty translation: ${item.quote}`);
  if (translated.has(item.quote)) errors.push(`${label}: duplicate translation: ${item.quote}`);
  translated.set(item.quote, item.quote_de);
}
const seedQuotes = new Set(quotes.map((item) => item.quote));
for (const quote of translated.keys()) {
  if (!seedQuotes.has(quote)) errors.push(`translation for unknown seed quote: ${quote}`);
}
for (const quote of seedQuotes) {
  if (!translated.has(quote)) errors.push(`missing German translation: ${quote}`);
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}

console.log(`Validated ${quotes.length} quotes by ${authorSpellings.size} authors: English-only, unique, canonical author names, <= ${MAX_TAGS} tags, ${translated.size} German translations.`);
