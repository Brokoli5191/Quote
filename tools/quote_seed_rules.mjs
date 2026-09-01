const WINDOWS_1252 = new Map([
  ['€', 0x80], ['‚', 0x82], ['ƒ', 0x83], ['„', 0x84], ['…', 0x85],
  ['†', 0x86], ['‡', 0x87], ['ˆ', 0x88], ['‰', 0x89], ['Š', 0x8a],
  ['‹', 0x8b], ['Œ', 0x8c], ['Ž', 0x8e], ['‘', 0x91], ['’', 0x92],
  ['“', 0x93], ['”', 0x94], ['•', 0x95], ['–', 0x96], ['—', 0x97],
  ['˜', 0x98], ['™', 0x99], ['š', 0x9a], ['›', 0x9b], ['œ', 0x9c],
  ['ž', 0x9e], ['Ÿ', 0x9f],
]);

const ENGLISH_MARKERS = new Set(`
  a about above after again against all am an and any are as at away be because been
  before being below between both but by can cannot could did do does doing down during
  each even ever every few for from further get give go had has have having he her here
  hers herself him himself his how i if in into is it its itself just know let life like
  made make many may me more most must my myself never no nor not now of off on once only
  or other our ours ourselves out over own people same see she should so some such than
  that the their theirs them themselves then there these they thing think this those through
  time to too under until up upon us very want was we were what when where which while who
  whom why will with without work world would you your yours yourself yourselves
`.trim().split(/\s+/));

const ENGLISH_CONTENT_WORDS = new Set(`
  act adventure baby believe blubber brave change courage create curiosity dream dreams
  education failure faith fear foolish freedom future happy happiness hope hungry impossible
  knowledge later laters learn learning love nitwit obey oddment optimism progress resist
  science stars stay strength success truth tweak wisdom
`.trim().split(/\s+/));

const FOREIGN_MARKERS = new Set(`
  aber als auch auf aus avec bei cela cette dans das dass dein deine der des die du el
  ella en eres es esta et für haben ich il ist la las le les los mais mit nicht nous oder
  para pas por que qui sein sind sobre son su sur una uno un und une vous yo
`.trim().split(/\s+/));

const NON_LATIN_LETTER = /[\p{Script=Arabic}\p{Script=Armenian}\p{Script=Bengali}\p{Script=Cyrillic}\p{Script=Devanagari}\p{Script=Georgian}\p{Script=Greek}\p{Script=Han}\p{Script=Hangul}\p{Script=Hebrew}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Thai}]/u;

export const MAX_TAGS = 5;
export const MAX_QUOTE_LENGTH = 90;

export function repairMojibake(value) {
  let normalized = String(value || '')
    .replace(/Ã¢â‚¬â„¢/g, '’').replace(/Ã¢â‚¬Ëœ/g, '‘')
    .replace(/Ã¢â‚¬Å“/g, '“').replace(/Ã¢â‚¬ï¿½/g, '”')
    .replace(/Ã¢â‚¬â€œ/g, '–').replace(/Ã¢â‚¬â€/g, '—')
    .replace(/Ã¢â‚¬Â¦/g, '…').replace(/Ã¢â‚¬Â²/g, '′')
    .replace(/Ã¢â‚¬/g, '—');

  if (!/[ÃƒÃ‚Ã¢Ã˜Ã™]/.test(normalized)) return normalized;
  const bytes = [];
  for (const char of normalized) {
    const code = char.codePointAt(0);
    const byte = WINDOWS_1252.get(char) ?? (code <= 0xff ? code : null);
    if (byte === null) return normalized;
    bytes.push(byte);
  }
  const repaired = Buffer.from(bytes).toString('utf8');
  return repaired.includes('\uFFFD') ? normalized : repaired;
}

export function cleanText(value) {
  return repairMojibake(value)
    .replace(/\uFFFD/g, '')
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/([.!?,”])(?=“)/g, '$1 ')
    .trim()
    .replace(/^[“”„"]|[“”„"]$/g, '')
    .trim();
}

export function cleanAuthor(value) {
  return cleanText(value).replace(/,+$/, '').trim();
}

export function quoteKey(value) {
  return cleanText(value)
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[\p{M}\p{P}\p{S}]+/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function isProbablyEnglish(value) {
  const text = cleanText(value);
  if (!text || text.includes('\uFFFD') || NON_LATIN_LETTER.test(text)) return false;

  const words = (text.toLowerCase().match(/[a-z]+(?:['’][a-z]+)?/g) || [])
    .map(word => word.replace('’', "'"));
  if (words.length === 0) return false;

  const englishHits = words.filter(word =>
    ENGLISH_MARKERS.has(word) ||
    ENGLISH_CONTENT_WORDS.has(word) ||
    /(?:n't|'re|'ve|'ll|'d|'m|'s)$/.test(word)
  ).length;
  const foreignHits = words.filter(word => FOREIGN_MARKERS.has(word)).length;

  if (foreignHits >= 2 && foreignHits > englishHits) return false;
  if (englishHits > 0) return true;
  return words.length <= 3 && words.some(word => ENGLISH_CONTENT_WORDS.has(word));
}

export function cleanTags(rawTags, author) {
  const authorTokens = new Set(
    author.toLowerCase().split(/[^a-z0-9]+/).filter(token => token.length > 1)
  );
  const seen = new Set();
  const tags = [];
  for (const rawTag of Array.isArray(rawTags) ? rawTags : []) {
    const tag = String(rawTag).toLowerCase().trim();
    if (
      !tag ||
      seen.has(tag) ||
      tag.startsWith('misattributed') ||
      tag === 'attributed-no-source' ||
      authorTokens.has(tag)
    ) continue;
    seen.add(tag);
    tags.push(tag);
    if (tags.length === MAX_TAGS) break;
  }
  return tags;
}

export function buildSeed(records) {
  const seenQuotes = new Set();
  const quotes = [];

  for (const record of records) {
    const quote = cleanText(record.quote);
    const author = cleanAuthor(record.author);
    if (!quote || !author || quote.length > MAX_QUOTE_LENGTH || !isProbablyEnglish(quote)) continue;

    const key = quoteKey(quote);
    if (!key || seenQuotes.has(key)) continue;
    seenQuotes.add(key);
    quotes.push({ quote, author, tags: cleanTags(record.tags, author) });
  }
  return quotes;
}
