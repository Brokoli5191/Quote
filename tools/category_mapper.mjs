// JS port of app/src/main/java/app/brokoli5191/quote/data/CategoryMapper.kt.
// Keep in sync: same category order, same synonym order, same fallback chain.

// Ordered; earlier categories win ties. "Uncategorized" is the honest fallback.
export const CATEGORIES = [
  'Inspirational', 'Life', 'Love', 'Wisdom', 'Happiness', 'Optimism',
  'Humor', 'Philosophy', 'Truth', 'Death', 'Poetry', 'Writing',
  'Books', 'Reading', 'Knowledge', 'Success', 'Courage', 'Friendship',
  'Nature', 'Faith', 'Freedom', 'Uncategorized',
];

// Source tag (lowercased) -> category. Insertion order matters for steps 3 and 4.
const SYNONYMS = new Map();
const group = (category, tags) => tags.forEach((tag) => SYNONYMS.set(tag, category));
group('Inspirational', ['inspirational', 'inspiration', 'motivational', 'motivation']);
group('Life', ['life', 'living', 'existence']);
group('Love', ['love', 'romance', 'romantic', 'relationships']);
group('Wisdom', ['wisdom', 'wise', 'insight']);
group('Happiness', ['happiness', 'happy', 'joy', 'contentment']);
group('Optimism', ['hope', 'optimism', 'optimistic', 'positivity', 'positive']);
group('Humor', ['humor', 'humour', 'funny', 'comedy', 'wit']);
group('Philosophy', ['philosophy', 'philosophical', 'stoicism', 'existentialism']);
group('Truth', ['truth', 'honesty', 'reality']);
group('Death', ['death', 'mortality', 'grief', 'loss', 'dying']);
group('Poetry', ['poetry', 'poem', 'poems', 'verse']);
group('Writing', ['writing', 'writers', 'writer']);
group('Books', ['books', 'book', 'literature']);
group('Reading', ['reading', 'readers', 'reader']);
group('Knowledge', ['knowledge', 'science', 'learning', 'education', 'intelligence']);
group('Success', ['success', 'achievement', 'ambition', 'goals', 'work']);
group('Courage', ['courage', 'bravery', 'fear', 'strength', 'resilience']);
group('Friendship', ['friendship', 'friends', 'friend']);
group('Nature', ['nature', 'earth', 'environment']);
group('Faith', ['faith', 'god', 'religion', 'spirituality', 'belief']);
group('Freedom', ['freedom', 'liberty', 'independence']);

export function mapCategory(tags, text) {
  const lowerTags = (tags ?? []).map((tag) => String(tag).toLowerCase().trim());
  // 1. exact category name in tags
  for (const category of CATEGORIES) if (lowerTags.includes(category.toLowerCase())) return category;
  // 2. synonym map (exact tag)
  for (const tag of lowerTags) if (SYNONYMS.has(tag)) return SYNONYMS.get(tag);
  // 3. synonym substring (e.g. "self-love" contains "love")
  for (const tag of lowerTags) for (const [key, category] of SYNONYMS) if (tag.includes(key)) return category;
  // 4. keyword in text
  const lowerText = String(text ?? '').toLowerCase();
  for (const [key, category] of SYNONYMS) if (lowerText.includes(key)) return category;
  // 5. honest fallback
  return 'Uncategorized';
}
