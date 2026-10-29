// Offline build-time script (NOT shipped in the app).
// Replaces the noisy source tags in the seed with curated tags from tools/quote_tags.json
// and adds tags to the anonymous quotes.
//
// The category is frozen first: each seed quote gets an explicit "category" computed from its
// ORIGINAL tags with the CategoryMapper port, so retagging never moves a quote to another
// category. A quote that already has a "category" keeps it, which makes re-runs safe.
//
// Every tag must be in tools/tag_vocabulary.json and every quote needs 3-5 tags.
//
// Usage: node tools/apply_quote_tags.mjs [--check]
//   --check  validate only, write nothing
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { CATEGORIES, mapCategory } from './category_mapper.mjs';

const MIN_TAGS = 3;
const MAX_TAGS = 5;

const toolsDir = path.dirname(fileURLToPath(import.meta.url));
const rawDir = path.join(toolsDir, '..', 'app', 'src', 'main', 'res', 'raw');
const seedPath = path.join(rawDir, 'quotes_seed.json');
const anonymousPath = path.join(rawDir, 'anonymous_quotes.json');
const tagsPath = path.join(toolsDir, 'quote_tags.json');
const vocabularyPath = path.join(toolsDir, 'tag_vocabulary.json');
const checkOnly = process.argv.includes('--check');

const readJson = (file) => JSON.parse(fs.readFileSync(file, 'utf8'));
const seed = readJson(seedPath);
const anonymous = readJson(anonymousPath);
const quoteTags = readJson(tagsPath);
const vocabulary = new Set(readJson(vocabularyPath).map((entry) => entry.en));
const errors = [];

function tagsFor(quote, label) {
  const tags = quoteTags[quote];
  if (!Array.isArray(tags)) {
    errors.push(`${label}: no tags in quote_tags.json: ${quote}`);
    return null;
  }
  if (tags.length < MIN_TAGS || tags.length > MAX_TAGS) errors.push(`${label}: needs ${MIN_TAGS}-${MAX_TAGS} tags, has ${tags.length}: ${quote}`);
  if (new Set(tags).size !== tags.length) errors.push(`${label}: duplicate tags: ${quote}`);
  for (const tag of tags) if (!vocabulary.has(tag)) errors.push(`${label}: tag not in vocabulary: ${tag}`);
  return tags;
}

const categoryCounts = new Map();
const newSeed = seed.map((item, index) => {
  const label = `seed row ${index + 1}`;
  const category = item.category ?? mapCategory(item.tags ?? [], item.quote);
  if (!CATEGORIES.includes(category)) errors.push(`${label}: unknown category ${category}`);
  categoryCounts.set(category, (categoryCounts.get(category) ?? 0) + 1);
  return { quote: item.quote, author: item.author, tags: tagsFor(item.quote, label) ?? item.tags, category };
});

const newAnonymous = anonymous.map((item, index) => ({
  quote: item.quote,
  quote_de: item.quote_de,
  tags: tagsFor(item.quote, `anonymous row ${index + 1}`) ?? item.tags,
}));

const known = new Set([...seed, ...anonymous].map((item) => item.quote));
for (const quote of Object.keys(quoteTags)) {
  if (!known.has(quote)) errors.push(`quote_tags.json: unknown quote: ${quote}`);
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  console.error(`${errors.length} problem(s); nothing written.`);
  process.exit(1);
}

const usedTags = new Set([...newSeed, ...newAnonymous].flatMap((item) => item.tags));
const summary = [...categoryCounts].sort((a, b) => b[1] - a[1]).map(([name, count]) => `${name} ${count}`).join(', ');
if (!checkOnly) {
  fs.writeFileSync(seedPath, JSON.stringify(newSeed, null, 1) + '\n');
  const lines = newAnonymous.map((item) => `  ${JSON.stringify(item)}`);
  fs.writeFileSync(anonymousPath, `[\n${lines.join(',\n')}\n]\n`);
}
console.log(`${checkOnly ? 'Checked' : 'Tagged'} ${newSeed.length} seed + ${newAnonymous.length} anonymous quotes with ${usedTags.size}/${vocabulary.size} vocabulary tags.`);
console.log(`Categories: ${summary}`);
