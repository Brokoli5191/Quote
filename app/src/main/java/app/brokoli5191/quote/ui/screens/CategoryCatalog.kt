package app.brokoli5191.quote.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for the Library category tiles. Kept in sync with
 * [app.brokoli5191.quote.data.CategoryMapper.categories] — the same names the
 * seeder assigns. Both the bento grid and the filter sheet read from here, so
 * adding a category is one edit.
 */
data class CategoryTileData(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color,
    val isFullWidth: Boolean = false
)

private val Cream = Color(0xFFFFF7EB)
private val Green = Color(0xFFA0D2AD)
private val Amber = Color(0xFFFFDB9C)
private val Rose = Color(0xFFFFB2C5)
private val Blue = Color(0xFFADC6FF)
private val Neutral = Color(0xFFB6B2BE)

val categoryCatalog: List<CategoryTileData> = listOf(
    CategoryTileData("Inspirational", Icons.Default.EmojiObjects, Cream, isFullWidth = true),
    CategoryTileData("Community", Icons.Default.Public, Green),
    CategoryTileData("Local", Icons.Default.PhoneAndroid, Neutral),
    CategoryTileData("Life", Icons.Default.Spa, Green),
    CategoryTileData("Love", Icons.Default.Favorite, Rose),
    CategoryTileData("Wisdom", Icons.Default.SelfImprovement, Amber),
    CategoryTileData("Happiness", Icons.Default.SentimentVerySatisfied, Amber),
    CategoryTileData("Optimism", Icons.Default.WbSunny, Amber),
    CategoryTileData("Humor", Icons.Default.TheaterComedy, Amber),
    CategoryTileData("Philosophy", Icons.Default.HistoryEdu, Blue),
    CategoryTileData("Truth", Icons.Default.Balance, Blue),
    CategoryTileData("Death", Icons.Default.HourglassEmpty, Blue),
    CategoryTileData("Poetry", Icons.Default.Create, Rose),
    CategoryTileData("Writing", Icons.Default.DriveFileRenameOutline, Blue),
    CategoryTileData("Books", Icons.Default.LibraryBooks, Amber),
    CategoryTileData("Reading", Icons.Default.AutoStories, Amber),
    CategoryTileData("Knowledge", Icons.Default.School, Blue),
    CategoryTileData("Success", Icons.Default.EmojiEvents, Amber),
    CategoryTileData("Courage", Icons.Default.Bolt, Cream),
    CategoryTileData("Friendship", Icons.Default.Diversity3, Rose),
    CategoryTileData("Nature", Icons.Default.Park, Green),
    CategoryTileData("Faith", Icons.Default.Star, Cream),
    CategoryTileData("Freedom", Icons.Default.Flight, Blue),
    CategoryTileData("Uncategorized", Icons.Default.Category, Neutral)
)

/** Category names for the filter sheet (same order as the grid). */
val filterCategoryNames: List<String> = categoryCatalog.map { it.name }
