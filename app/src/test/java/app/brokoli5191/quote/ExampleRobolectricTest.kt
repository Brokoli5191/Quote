package app.brokoli5191.quote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Quote", appName)
  }

  @Test
  fun `verify quotes seed json can be read and parsed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inputStream = context.resources.openRawResource(R.raw.quotes_seed)
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    
    assertTrue(jsonString.isNotEmpty())
    val jsonArray = JSONArray(jsonString)
    assertTrue(jsonArray.length() > 0)
    
    val firstItem = jsonArray.getJSONObject(0)
    assertTrue(firstItem.has("quote"))
    assertTrue(firstItem.has("author"))
    assertTrue(firstItem.has("tags"))
  }

  @Test
  fun `audit category quote counts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inputStream = context.resources.openRawResource(R.raw.quotes_seed)
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    val jsonArray = JSONArray(jsonString)
    
    val userCategories = listOf(
        "Inspirational", "Life", "Humor", "Love", "Books", "Truth", "Reading", "Wisdom",
        "Happiness", "Writing", "Inspiration", "Philosophy", "Death", "Poetry", "Optimism"
    )
    
    val categoryCounts = userCategories.associateWith { 0 }.toMutableMap()
    
    for (i in 0 until jsonArray.length()) {
        try {
            val item = jsonArray.getJSONObject(i)
            val text = item.optString("quote", "")
            val author = item.optString("author", "")
            val tagsArray = item.optJSONArray("tags")
            val tags = if (tagsArray != null) {
                (0 until tagsArray.length()).map { tagsArray.optString(it, "") }
            } else {
                emptyList()
            }
            
            val category = mapTagsToCategoryForTest(text, author, tags)
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
        } catch (e: Exception) {
            val valStr = try { jsonArray.get(i).toString() } catch(ex: Exception) { "cannot get" }
            val errorMsg = "Error parsing index $i: Value: $valStr. Msg: ${e.message}"
            java.io.File("debug_output.txt").writeText(errorMsg)
            throw RuntimeException(errorMsg, e)
        }
    }
    
    val report = StringBuilder()
    report.append("--- CATEGORY AUDIT REPORT ---\n")
    var total = 0
    for ((cat, count) in categoryCounts) {
        report.append("Category '$cat': $count quotes\n")
        total += count
    }
    report.append("Total mapped: $total / ${jsonArray.length()}\n")
    java.io.File("debug_output.txt").writeText(report.toString())
    
    // Check if any category has 0 quotes
    val emptyCategories = categoryCounts.filter { it.value == 0 }.keys
    assertTrue("The following categories have 0 quotes: $emptyCategories", emptyCategories.isEmpty())
  }

  private fun mapTagsToCategoryForTest(quoteText: String, author: String, tags: List<String>): String {
    val allLowerTags = tags.map { it.lowercase().trim() }
    val textLower = quoteText.lowercase()
    
    val userCategories = listOf(
        "Inspirational", "Life", "Humor", "Love", "Books", "Truth", "Reading", "Wisdom",
        "Happiness", "Writing", "Inspiration", "Philosophy", "Death", "Poetry", "Optimism"
    )
    
    for (category in userCategories) {
        val categoryLower = category.lowercase()
        if (allLowerTags.contains(categoryLower)) {
            return category
        }
    }
    
    for (category in userCategories) {
        val categoryLower = category.lowercase()
        if (allLowerTags.any { it.contains(categoryLower) }) {
            return category
        }
    }
    
    for (category in userCategories) {
        val categoryLower = category.lowercase()
        if (textLower.contains(categoryLower)) {
            return category
        }
    }
    
    return "Inspirational"
  }
}
