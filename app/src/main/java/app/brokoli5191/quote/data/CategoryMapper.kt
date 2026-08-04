package app.brokoli5191.quote.data

/**
 * Maps a quote's source tags (and, as a last resort, its text) to a single
 * browsable category. Pure Kotlin so it can be unit-tested without Android.
 *
 * Replaces the old "everything unmatched falls into Inspirational" behaviour:
 * the honest final fallback here is "Uncategorized", a real category, so the
 * unmatched pile is visible instead of inflating Inspirational.
 */
object CategoryMapper {
    // Ordered; earlier categories win ties. "Uncategorized" is the honest fallback.
    val categories: List<String> = listOf(
        "Inspirational", "Life", "Love", "Wisdom", "Happiness", "Optimism",
        "Humor", "Philosophy", "Truth", "Death", "Poetry", "Writing",
        "Books", "Reading", "Knowledge", "Success", "Courage", "Friendship",
        "Nature", "Faith", "Freedom", "Uncategorized"
    )

    // Source tag (lowercased) -> category. Many source tags collapse to one category.
    private val synonyms: Map<String, String> = buildMap {
        listOf("inspirational", "inspiration", "motivational", "motivation").forEach { put(it, "Inspirational") }
        listOf("life", "living", "existence").forEach { put(it, "Life") }
        listOf("love", "romance", "romantic", "relationships").forEach { put(it, "Love") }
        listOf("wisdom", "wise", "insight").forEach { put(it, "Wisdom") }
        listOf("happiness", "happy", "joy", "contentment").forEach { put(it, "Happiness") }
        listOf("hope", "optimism", "optimistic", "positivity", "positive").forEach { put(it, "Optimism") }
        listOf("humor", "humour", "funny", "comedy", "wit").forEach { put(it, "Humor") }
        listOf("philosophy", "philosophical", "stoicism", "existentialism").forEach { put(it, "Philosophy") }
        listOf("truth", "honesty", "reality").forEach { put(it, "Truth") }
        listOf("death", "mortality", "grief", "loss", "dying").forEach { put(it, "Death") }
        listOf("poetry", "poem", "poems", "verse").forEach { put(it, "Poetry") }
        listOf("writing", "writers", "writer").forEach { put(it, "Writing") }
        listOf("books", "book", "literature").forEach { put(it, "Books") }
        listOf("reading", "readers", "reader").forEach { put(it, "Reading") }
        listOf("knowledge", "science", "learning", "education", "intelligence").forEach { put(it, "Knowledge") }
        listOf("success", "achievement", "ambition", "goals", "work").forEach { put(it, "Success") }
        listOf("courage", "bravery", "fear", "strength", "resilience").forEach { put(it, "Courage") }
        listOf("friendship", "friends", "friend").forEach { put(it, "Friendship") }
        listOf("nature", "earth", "environment").forEach { put(it, "Nature") }
        listOf("faith", "god", "religion", "spirituality", "belief").forEach { put(it, "Faith") }
        listOf("freedom", "liberty", "independence").forEach { put(it, "Freedom") }
    }

    fun map(tags: List<String>, text: String): String {
        val lowerTags = tags.map { it.lowercase().trim() }
        // 1. exact category name in tags
        for (c in categories) if (lowerTags.contains(c.lowercase())) return c
        // 2. synonym map (exact tag)
        for (t in lowerTags) synonyms[t]?.let { return it }
        // 3. synonym substring (e.g. "self-love" contains "love")
        for (t in lowerTags) for ((k, v) in synonyms) if (t.contains(k)) return v
        // 4. keyword in text
        val lowerText = text.lowercase()
        for ((k, v) in synonyms) if (lowerText.contains(k)) return v
        // 5. honest fallback
        return "Uncategorized"
    }
}
