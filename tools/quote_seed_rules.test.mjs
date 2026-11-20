import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  applyQuoteTags, authorKey, buildSeed, cleanAuthor, cleanText, compactQuoteKey, isProbablyEnglish, MAX_QUOTE_LENGTH, MAX_TAGS,
  OVERRIDE_LENGTH_SLACK, pairQuoteMarks, TEXT_OVERRIDES, textDefects,
} from './quote_seed_rules.mjs';

const readRaw = name => JSON.parse(readFileSync(new URL(`../app/src/main/res/raw/${name}`, import.meta.url), 'utf8'));

test('language check accepts short English quotes and rejects common non-English examples', () => {
  assert.equal(isProbablyEnglish('Stay hungry. Stay foolish.'), true);
  assert.equal(isProbablyEnglish('Resist much, obey little.'), true);
  assert.equal(isProbablyEnglish('Das Leben ist schön.'), false);
  assert.equal(isProbablyEnglish('La vie est belle.'), false);
  assert.equal(isProbablyEnglish('La vida es bella.'), false);
  assert.equal(isProbablyEnglish('أجمل حب هو الذي نعثر عليه أثناء بحثنا عن شيء آخر'), false);
});

test('seed builder caps tags and deduplicates quote text across authors', () => {
  const seed = buildSeed([
    { quote: 'The only thing we have to fear is fear itself.', author: 'First', tags: ['one', 'two', 'three', 'four', 'five', 'six'] },
    { quote: 'The only thing we have to fear is fear itself!', author: 'Second', tags: ['duplicate'] },
  ]);

  assert.equal(seed.length, 1);
  assert.equal(seed[0].tags.length, MAX_TAGS);
  assert.equal(seed[0].author, 'First');
});

test('cleanText strips only a quotation-mark pair that wraps the entire text', () => {
  assert.equal(cleanText('“Be yourself; everyone else is already taken.”'), 'Be yourself; everyone else is already taken.');
  assert.equal(cleanText('"Stay hungry. Stay foolish."'), 'Stay hungry. Stay foolish.');
  assert.equal(cleanText('„Mehr Licht!“'), 'Mehr Licht!');
  assert.equal(cleanText('He said “hello” to me.'), 'He said “hello” to me.');
  assert.equal(cleanText('“Hello,” she said, “goodbye.”'), '“Hello,” she said, “goodbye.”');
  assert.equal(
    cleanText('“Do you hate people?” “I don\'t hate them.”'),
    '“Do you hate people?” “I don\'t hate them.”',
  );
});

test('cleanText keeps a closing mark that belongs to an inner quote', () => {
  assert.equal(
    cleanText('“Stay gold, Ponyboy. Stay gold . . .” The pillow seemed to sink a little.”'),
    '“Stay gold, Ponyboy. Stay gold . . .” The pillow seemed to sink a little.',
  );
  assert.equal(cleanText('She said “yes”'), 'She said “yes”');
  assert.equal(cleanText('She said "yes"'), 'She said "yes"');
});

test('cleanText balances unmatched quotation marks', () => {
  assert.equal(
    cleanText('“Of all the words of mice and men, the saddest are, "It might have been.”'),
    'Of all the words of mice and men, the saddest are, "It might have been."',
  );
  assert.equal(
    cleanText('“You love me. Real or not real?"I tell him, "Real.”'),
    '"You love me. Real or not real?" I tell him, "Real."',
  );
  assert.equal(cleanText('“What do you want?""Just coffee.”'), '"What do you want?" "Just coffee."');
  assert.equal(cleanText('The sign read “Keep out'), 'The sign read “Keep out”');
  assert.equal(cleanText('“Quote without an end'), 'Quote without an end');
  assert.equal(cleanText('Bought a 12" record.'), 'Bought a 12" record.');
});

test('cleanText spaces only opening marks, so German closing marks stay next to the text', () => {
  assert.equal(cleanText('“Why?”“Because.”'), '“Why?” “Because.”');
  assert.equal(cleanText('Ich sage ihm: „Real.“'), 'Ich sage ihm: „Real.“');
  assert.equal(cleanText('„Warum?“ „Darum.“'), '„Warum?“ „Darum.“');
  assert.equal(cleanText('Er sagte: „Komm!“ und ging.'), 'Er sagte: „Komm!“ und ging.');
  assert.equal(cleanText('„Ja,“ sagte er.'), '„Ja,“ sagte er.');
  // Repairs the space that earlier versions put before the closing mark.
  assert.equal(cleanText('Ich sage ihm: „Real. “'), 'Ich sage ihm: „Real.“');
});

test('cleanText keeps the German translations as they are, apart from a pair wrapping the whole text', () => {
  const translations = [...readRaw('quotes_de.json'), ...readRaw('anonymous_quotes.json')].map(item => item.quote_de);
  const changed = translations.filter(text => {
    const cleaned = cleanText(text);
    return cleaned !== text && cleaned !== text.slice(1, -1).trim();
  });
  assert.deepEqual(changed, []);
});

test('cleaned quote marks are always balanced', () => {
  for (const raw of [
    '“How do you spell \'love\'?" - Piglet"You don\'t spell it...you feel it." - Pooh”',
    '“Hang on, Angel?" I asked."If I did?""I don\'t like it."He grinned. "It stays, Angel.”',
    '“You can\'t be happy unless you\'re unhappy sometimes".”',
  ]) {
    const { unmatchedOpen, unmatchedClose } = pairQuoteMarks(cleanText(raw));
    assert.deepEqual([unmatchedOpen, unmatchedClose], [[], []], raw);
  }
});

test('cleanText repairs mojibake next to genuine quotation marks', () => {
  assert.equal(
    cleanText('“Libraries were full of ideasâ€”perhaps the most dangerous of all weapons.”'),
    'Libraries were full of ideas—perhaps the most dangerous of all weapons.',
  );
  assert.equal(
    cleanText('“What day is it?â€\uFFFD asked Pooh. â€œItâ€™s today,â€\uFFFD squeaked Piglet.”'),
    '“What day is it?” asked Pooh. “It’s today,” squeaked Piglet.',
  );
  assert.equal(cleanText('Café, naïve façade'), 'Café, naïve façade');
});

test('cleanAuthor maps misspelled and variant author names to one display name', () => {
  for (const [raw, expected] of [
    ['Confucious', 'Confucius'],
    ['William Shakespear', 'William Shakespeare'],
    ['Lewis Caroll', 'Lewis Carroll'],
    ['Edgar Allen Poe', 'Edgar Allan Poe'],
    ['Mother Theresa', 'Mother Teresa'],
    ['Hemingway, Ernest', 'Ernest Hemingway'],
    ['Tolstoy Leo', 'Leo Tolstoy'],
    ['Oscar Wilde (attributed to)', 'Oscar Wilde'],
    ['Herman Hesse', 'Hermann Hesse'],
    ['Haruki Marukami', 'Haruki Murakami'],
    ['haruki murakami', 'Haruki Murakami'],
    ['Henry Ward Beecherr', 'Henry Ward Beecher'],
    ['Cathy Guiswite', 'Cathy Guisewite'],
    ['Winston S. Churchill', 'Winston Churchill'],
    ['J.K. rowling', 'J.K. Rowling'],
    ['J. K. Rowling', 'J.K. Rowling'],
    ['J.K.Rowling', 'J.K. Rowling'],
    ['JK Rowling', 'J.K. Rowling'],
    ['e.e. cummings', 'E.E. Cummings'],
    ['A. P. J. Abdul Kalam', 'A.P.J. Abdul Kalam'],
    ['Martin Luther King, Jr.', 'Martin Luther King Jr.'],
    ['Gabriel Garcia Marquez', 'Gabriel García Márquez'],
  ]) assert.equal(cleanAuthor(raw), expected, raw);

  for (const name of ['W. Somerset Maugham', 'Ursula K. Le Guin', 'C. JoyBell C.', 'Walter M. Miller Jr.', 'Alexandre Dumas']) {
    assert.equal(cleanAuthor(name), name);
  }
  assert.equal(authorKey('Corrie Ten Boom'), authorKey('Corrie ten Boom'));
  assert.equal(authorKey('Anais Nin'), authorKey('Anaïs Nin'));
});

test('seed builder stores the canonical author and drops tags that repeat it', () => {
  const seed = buildSeed([
    { quote: 'All the world’s a stage.', author: 'William Shakespear', tags: ['shakespeare', 'theatre'] },
  ]);
  assert.deepEqual(seed, [{ quote: 'All the world’s a stage.', author: 'William Shakespeare', tags: ['theatre'] }]);
});

test('curated tags replace source tags without changing the source-tag category', () => {
  const seed = applyQuoteTags([
    { quote: 'So many books, so little time.', author: 'Frank Zappa', tags: ['books', 'humor'] },
    { quote: 'An untagged quote.', author: 'Someone', tags: ['romance'] },
  ], { 'So many books, so little time.': ['books', 'reading', 'time'] });

  assert.deepEqual(seed[0], { quote: 'So many books, so little time.', author: 'Frank Zappa', tags: ['books', 'reading', 'time'], category: 'Humor' });
  assert.deepEqual(seed[1].tags, ['romance']);
  assert.equal(seed[1].category, 'Love');
});

test('text corrections change only spacing, punctuation and case, and are stable under cleanText', () => {
  for (const [source, corrected] of TEXT_OVERRIDES) {
    assert.equal(cleanText(source), source, `override key is not a cleaned source text: ${source}`);
    assert.equal(cleanText(corrected), corrected, `correction changes under cleanText: ${corrected}`);
    assert.equal(compactQuoteKey(corrected), compactQuoteKey(source), `correction changes letters: ${corrected}`);
    assert.deepEqual(textDefects(corrected), [], corrected);
    assert.ok(corrected.length <= MAX_QUOTE_LENGTH + OVERRIDE_LENGTH_SLACK, corrected);
  }
});

test('seed builder applies text corrections and drops blocked authors', () => {
  const seed = buildSeed([
    { quote: 'You only live twice:Once when you are bornAnd once when you look death in the face', author: 'Ian Fleming', tags: [] },
    { quote: 'If you win, you need not have to explain...If you lose, you should not be there to explain!', author: 'Adolf Hitler', tags: [] },
  ]);
  assert.deepEqual(seed.map(item => item.quote), [
    'You only live twice: once when you are born, and once when you look death in the face.',
  ]);
});

test('defect check finds lost line breaks and missing end punctuation', () => {
  assert.ok(textDefects('You only live twice:Once when you are bornAnd once').length > 0);
  assert.ok(textDefects('Sometimes people are beautiful.Not in looks.').length > 0);
  assert.ok(textDefects('I said."Good thing I do."').length > 0);
  assert.ok(textDefects('Scarce, sir...mighty scarce.').length > 0);
  assert.ok(textDefects('Silence is so freaking loud').length > 0);
  assert.deepEqual(textDefects('It\'s the friends you can call up at 4 a.m. that matter.'), []);
  assert.deepEqual(textDefects('“Do you hate people?” “I don\'t hate them.”'), []);
});

test('bundled seed, translations and anonymous quotes have no text defects', () => {
  const texts = [
    ...readRaw('quotes_seed.json').map(item => item.quote),
    ...readRaw('anonymous_quotes.json').map(item => item.quote),
  ];
  for (const text of texts) assert.deepEqual(textDefects(text), [], text);
});
