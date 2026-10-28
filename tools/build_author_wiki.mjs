// Builds app/src/main/res/raw/author_wiki.json: maps every seed author to a verified
// English Wikipedia person article (title, thumbnail, short descriptions, dewiki title).
// Usage: node tools/build_author_wiki.mjs [seed.json] [output.json]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [, , seedArg, outArg] = process.argv;
const seedPath = seedArg ?? path.join(ROOT, 'app/src/main/res/raw/quotes_seed.json');
const outPath = outArg ?? path.join(ROOT, 'app/src/main/res/raw/author_wiki.json');

const USER_AGENT = 'QuoteAppSeedBuilder/2.0 (https://github.com/Brokoli5191/Quote)';
const BATCH_SIZE = 50;
const REQUEST_GAP_MS = 300;
const MAX_RETRIES = 6;

// Author (exactly as in the seed) -> exact enwiki title, or null to skip the author.
// Covers ambiguous names, pen names, co-author credits and non-person/fictional authors.
// Spelling variants are fixed in the seed itself (AUTHOR_ALIASES in quote_seed_rules.mjs).
const OVERRIDES = {
  'Anonymous': null,
  'Narcotics Anonymous': null,
  'Walt Disney Company': null,
  'Simon and Garfunkel': null,
  'Lance Armstrong and Sally Jenkins': null,
  'P.C. Cast and Kristin Cast': null,
  'Lemony Snicket': null,
  'C. JoyBell C.': null,
  'H.N. Turteltaub': 'Harry Turtledove',
  'Lao Tzu': 'Laozi',
  'Dalai Lama XIV': '14th Dalai Lama',
  'St. Augustine': 'Augustine of Hippo',
  'St. Francis of Assisi': 'Francis of Assisi',
  'Francis Bacon': 'Francis Bacon',
  'Seneca': 'Seneca the Younger',
  'Cicero': 'Cicero',
  'Horace': 'Horace',
  'Colette': 'Colette',
  'Stendhal': 'Stendhal',
  'Madonna': 'Madonna',
  'Novalis': 'Novalis',
  'Virgil': 'Virgil',
  'Richard Evans': null,
  'Linda Grayson': null,
  'Terry Johnson': null,
  'Paul Terry': null,
  'Mark A. Cooper': null,
  'Joe Klaas': null,
  'Sarah Williams': 'Sarah Williams (poet)',
  'Lisa Schroeder': null,
  'Katie McGarry': null,
  'Marthe Troly-Curtin': null,
  'Christopher Moore': 'Christopher Moore (author)',
  'David Nicholls': 'David Nicholls (writer)',
  'Donald Miller': 'Donald Miller (author)',
  'Karl Kraus': 'Karl Kraus (writer)',
  'William Nicholson': 'William Nicholson (writer)',
  'François de La Rochefoucauld': 'François de La Rochefoucauld (writer)',
  'David Mitchell': 'David Mitchell (author)',
  'Alexandre Dumas': 'Alexandre Dumas',
  'Gordon A. Eadie': null,
  'Allen Saunders': 'Allen Saunders',
  'Glen Cook': 'Glen Cook',
  'John Anster': 'John Anster',
  'Edward J. Stieglitz': null,
  'Evans G. Valens': null,
  'Olin Miller': null,
  'Reginald Vincent Holmes': null,
  'Santosh Kalwar': null,
  'Roy T. Bennett': null,
  'Steve Maraboli': null,
  'Jess C. Scott': null,
  'José N. Harris': null,
  'John A. Shedd': null,
  'Irina Dunn': 'Irina Dunn',
  'Robert Orben': 'Robert Orben',
};

const NON_PERSON = /\b(disambiguation|fictional|wikimedia|list of|surname|given name|family name|band|duo|company|album|song|magazine|organization|organisation|pen name|pseudonym)\b|^(\w+ )?(film|novel|book|television series|play)\b/i;
const PERSON_HINT = /\b(\d{3,4}|bc|bce|ad|century|born|died)\b|\b(writer|author|novelist|poet|playwright|dramatist|essayist|philosopher|physicist|scientist|mathematician|chemist|biologist|astronomer|inventor|engineer|politician|president|prime minister|statesman|stateswoman|king|queen|emperor|monarch|activist|singer|songwriter|musician|composer|rapper|actor|actress|comedian|humorist|satirist|director|filmmaker|producer|painter|artist|sculptor|designer|cartoonist|illustrator|journalist|broadcaster|presenter|historian|economist|psychologist|psychiatrist|psychoanalyst|sociologist|theologian|priest|nun|monk|saint|pastor|minister|preacher|rabbi|religious|spiritual|leader|teacher|educator|scholar|critic|lawyer|judge|justice|businessman|businesswoman|entrepreneur|executive|industrialist|investor|aviator|astronaut|athlete|cyclist|coach|general|soldier|military|diplomat|revolutionary|nurse|physician|explorer|naturalist|primatologist|conservationist|environmentalist|nobel|laureate|lyricist|guitarist|entertainer|personality|model|socialite|fashion|columnist|speaker|motivational|evangelist|missionary|mystic|sage|lama|guru|monk|yogi|disciple|founder|reformer|feminist|abolitionist|suffragist|humanitarian|scientist|computer|programmer|scientist|stoic|orator|blogger|chef|restaurateur|screenwriter|lexicographer|polymath|prophet|samurai)\b/i;

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function apiGet(host, params) {
  const url = `https://${host}/w/api.php?` + new URLSearchParams({ format: 'json', formatversion: '2', ...params });
  for (let attempt = 0; ; attempt++) {
    await sleep(REQUEST_GAP_MS);
    let res;
    try {
      res = await fetch(url, { headers: { 'User-Agent': USER_AGENT, 'Api-User-Agent': USER_AGENT } });
    } catch (err) {
      if (attempt >= MAX_RETRIES) throw err;
      await sleep(2000 * 2 ** attempt);
      continue;
    }
    if (res.status === 429 || res.status >= 500) {
      if (attempt >= MAX_RETRIES) throw new Error(`${host}: HTTP ${res.status} after ${attempt} retries`);
      const retryAfter = Number(res.headers.get('retry-after'));
      await sleep(Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1000 : 2000 * 2 ** attempt);
      continue;
    }
    if (!res.ok) throw new Error(`${host}: HTTP ${res.status}`);
    return res.json();
  }
}

function chunks(list, size) {
  const out = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

// Follows normalized/redirect hops so each requested title maps to its final page.
function resolveTitles(query, titles) {
  const hop = new Map();
  for (const n of query.normalized ?? []) hop.set(n.from, n.to);
  for (const r of query.redirects ?? []) hop.set(r.from, r.to);
  const pages = new Map((query.pages ?? []).map(p => [p.title, p]));
  const result = new Map();
  for (const title of titles) {
    let t = title;
    for (let i = 0; i < 5 && hop.has(t); i++) t = hop.get(t);
    result.set(title, pages.get(t) ?? null);
  }
  return result;
}

function candidateTitle(author) {
  if (Object.hasOwn(OVERRIDES, author)) return OVERRIDES[author];
  let name = author.trim();
  // "douglas adams" -> "Douglas Adams"; titles are case-sensitive after the first letter.
  if (name === name.toLowerCase()) name = name.replace(/(^|[\s.-])(\p{L})/gu, (m, p, c) => p + c.toUpperCase());
  return name;
}

function rejectReason(page) {
  if (!page || page.missing || page.invalid) return 'missing';
  if (page.ns !== 0) return 'namespace';
  if (page.pageprops && Object.hasOwn(page.pageprops, 'disambiguation')) return 'disambiguation';
  const desc = page.description ?? '';
  if (!desc) return 'no description';
  if (NON_PERSON.test(desc)) return `non-person description: ${desc}`;
  if (!PERSON_HINT.test(desc)) return `no person hint: ${desc}`;
  return null;
}

// The API now hands out thumb.wikimedia.org URLs with tracking params; the same path is
// served by upload.wikimedia.org. SVG page images are logos/signatures, not portraits.
function thumbnailUrl(source) {
  if (!source) return null;
  const url = new URL(source);
  if (url.hostname === 'thumb.wikimedia.org') url.hostname = 'upload.wikimedia.org';
  url.search = '';
  if (url.hostname !== 'upload.wikimedia.org' || /\.svg(\.png)?$/i.test(url.pathname)) return null;
  return url.toString();
}

function sortKeys(value) {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(k => [k, sortKeys(value[k])]));
  }
  return value;
}

async function main() {
  const seed = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
  const authors = [...new Set(seed.map(q => q.author).filter(Boolean))].sort();
  const titleOf = new Map(authors.map(a => [a, candidateTitle(a)]));
  const titles = [...new Set([...titleOf.values()].filter(Boolean))];

  const pageOf = new Map();
  for (const batch of chunks(titles, BATCH_SIZE)) {
    const data = await apiGet('en.wikipedia.org', {
      action: 'query', redirects: '1',
      prop: 'pageimages|description|pageprops|langlinks',
      piprop: 'thumbnail', pithumbsize: '330', lllang: 'de', lllimit: 'max',
      titles: batch.join('|'),
    });
    for (const [t, p] of resolveTitles(data.query ?? {}, batch)) pageOf.set(t, p);
  }

  const matched = {};
  const rejected = [];
  for (const author of authors) {
    const title = titleOf.get(author);
    if (!title) { rejected.push([author, 'override: skipped']); continue; }
    const page = pageOf.get(title);
    const reason = rejectReason(page);
    if (reason) { rejected.push([author, reason]); continue; }
    const thumb = thumbnailUrl(page.thumbnail?.source);
    matched[author] = {
      title: page.title,
      image: thumb,
      en: page.description,
      de: null,
      dewiki: page.langlinks?.find(l => l.lang === 'de')?.title ?? null,
    };
  }

  const deTitles = [...new Set(Object.values(matched).map(m => m.dewiki).filter(Boolean))];
  const deDescOf = new Map();
  for (const batch of chunks(deTitles, BATCH_SIZE)) {
    let data;
    try {
      data = await apiGet('de.wikipedia.org', { action: 'query', redirects: '1', prop: 'description', titles: batch.join('|') });
    } catch (err) {
      console.error(`dewiki batch failed, leaving de descriptions null: ${err.message}`);
      continue;
    }
    for (const [t, p] of resolveTitles(data.query ?? {}, batch)) {
      if (p && !p.missing && p.description) deDescOf.set(t, p.description);
    }
  }
  for (const m of Object.values(matched)) m.de = m.dewiki ? deDescOf.get(m.dewiki) ?? null : null;

  fs.writeFileSync(outPath, JSON.stringify(sortKeys(matched), null, 1) + '\n');

  const entries = Object.values(matched);
  const overridesUsed = authors.filter(a => Object.hasOwn(OVERRIDES, a));
  console.error(`authors=${authors.length} matched=${entries.length} withImage=${entries.filter(m => m.image).length} ` +
    `withDe=${entries.filter(m => m.de).length} overridesUsed=${overridesUsed.length}`);
  for (const [author, reason] of rejected) console.error(`  unmatched: ${author} (${reason})`);
  const unusedOverrides = Object.keys(OVERRIDES).filter(a => !authors.includes(a));
  if (unusedOverrides.length) console.error(`  overrides without seed author: ${unusedOverrides.join(', ')}`);
}

main().catch(err => { console.error(err); process.exit(1); });
