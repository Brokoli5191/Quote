package app.brokoli5191.quote

import app.brokoli5191.quote.data.CategoryMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMapperTest {
    @Test fun exactTagWins() =
        assertEquals("Love", CategoryMapper.map(listOf("love", "romance"), "irrelevant"))

    @Test fun synonymMapsToCategory() =
        assertEquals("Optimism", CategoryMapper.map(listOf("hope"), "irrelevant"))

    @Test fun deathCluster() =
        assertEquals("Death", CategoryMapper.map(listOf("mortality"), "x"))

    @Test fun unmatchedIsUncategorizedNotInspirational() =
        assertEquals("Uncategorized", CategoryMapper.map(listOf("zzz-nonsense"), "abcd efgh ijkl"))

    @Test fun textKeywordFallback() =
        assertEquals("Wisdom", CategoryMapper.map(emptyList(), "a piece of true wisdom"))

    @Test fun uncategorizedIsARealCategory() =
        assertTrue(CategoryMapper.categories.contains("Uncategorized"))
}
