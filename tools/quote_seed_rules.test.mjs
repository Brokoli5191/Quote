import assert from 'node:assert/strict';
import test from 'node:test';
import { buildSeed, cleanText, isProbablyEnglish, MAX_TAGS, pairQuoteMarks } from './quote_seed_rules.mjs';

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
