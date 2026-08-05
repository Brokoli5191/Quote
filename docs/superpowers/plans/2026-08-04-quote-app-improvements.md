# Quote App Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship 7 user-requested improvements to the Quote Android app: larger tagged quote DB with a real category system, a true zero-animation low-performance mode, startup + runtime jank fixes, a decluttered Daily screen, combinable AMOLED + Material You theming, subtitle-free Library category cards, and a new open-book adaptive icon.

**Architecture:** Single-Activity Jetpack Compose app, Room persistence, `QuoteViewModel` (AndroidViewModel) holding all UI state as StateFlows, SharedPreferences for settings. Changes are additive and per-workstream; each task builds and is committed on its own.

**Tech Stack:** Kotlin 2.3, Jetpack Compose (Material3), Room + KSP, kotlinx-coroutines, JUnit + Robolectric for unit tests, Gradle 9.4 (`./gradlew`).

## Global Constraints

- Package root: `app.brokoli5191.quote`.
- minSdk 24, targetSdk 36, JDK 21, `sourceCompatibility`/`targetCompatibility` = 11.
- Build gate for every task: `./gradlew assembleDebug` must pass. Logic tasks also run `./gradlew testDebugUnitTest`.
- No new network dependency in the shipped app. No online quote fetching at runtime. Data fetching happens only in an offline build-time script under `tools/`.
- No fabricated quotes or author biographies.
- Persisted pref keys are versioned; migrations must not lose user favorites or user-added quotes.
- Commit after each task with a descriptive message ending in the `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer.
- Seed dataset source (already validated reachable): `Abirate/english_quotes` full set (2508 tagged quotes), clean ~2500 target — no untagged merge.

---

### Task 1: Remove Library category-card subtitles (Workstream F)

**Files:**
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/LibraryScreen.kt`

**Interfaces:**
- Produces: `CategoryBentoCard(name, icon, tintColor, height, onClick)` — the `description` parameter is removed; `CategoryTileData.description` field removed.
- Consumes: nothing new.

- [ ] **Step 1: Remove the description Text from `CategoryBentoCard`**

In `CategoryBentoCard`, delete the `Spacer` + description `Text` in the bottom `Column` (the block rendering `description` with `maxLines = 2`). Keep the `name` Text. Remove the `description: String` parameter from the function signature.

- [ ] **Step 2: Remove `description` from `CategoryTileData` and all call sites**

Delete `val description: String` from the `CategoryTileData` data class. Remove every `description = "..."` line from the `bentoCategories` list (all 15 entries). Remove `description = ...` from both `CategoryBentoCard(...)` invocations (the Inspirational large card and the `midCategories` loop).

- [ ] **Step 3: Rebalance card layout**

Since the card now shows only icon + name, center the name vertically-bottom as before but reduce card heights slightly for a tighter grid: change the large Inspirational card `height = 150.dp` → `120.dp` and `midCategories` cards `height = 140.dp` → `110.dp`. Leave icons unchanged.

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, no unresolved-reference errors for `description`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/brokoli5191/quote/ui/screens/LibraryScreen.kt
git commit -m "F: remove category-card subtitles in Library"
```

---

### Task 2: Declutter Daily screen + honest author block (Workstream D)

**Files:**
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/DailyScreen.kt`

**Interfaces:**
- Consumes: `QuoteViewModel.dailyQuote` (unchanged), `QuoteEntity.{author, tags}`.
- Produces: no new public symbols.

- [ ] **Step 1: Remove the fake "About the Sage" description text**

In the "Daily Insight Card", delete the `Text` that renders `if (quote.aboutAuthor.isBlank()) "A wisdom practitioner with deep teachings of truth and insight." else quote.aboutAuthor`. Also remove the now-meaningless "DAILY INSIGHT" ✦ label row and the "About the Sage" heading Text.

- [ ] **Step 2: Replace with an honest author block**

In the same `Column` (weight 1f, next to the initial-avatar Box), render only: the author's real name as `titleMedium`/`headlineSmall` bold, and nothing fabricated. Keep the generated initial-avatar Box (it is derived from the real name, not fake). Result: `[initial avatar]  Author Name`. The tags `FlowRow` below stays (real tags), and the action row (favorite/copy/Learn More) stays.

- [ ] **Step 3: Re-apply the in-flight ContentTransform tweak**

Ensure the quote `AnimatedContent` `transitionSpec` uses:
```kotlin
ContentTransform(
    targetContentEnter = fadeIn(animationSpec = tween(300)),
    initialContentExit = fadeOut(animationSpec = tween(300)),
    sizeTransform = null
)
```
(matches the uncommitted tweak on the user's main copy).

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. No references to the removed strings remain.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/brokoli5191/quote/ui/screens/DailyScreen.kt
git commit -m "D: declutter Daily screen, drop fake About-the-Sage filler"
```

---

### Task 3: Decouple AMOLED from Material You (Workstream E)

**Files:**
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/theme/Theme.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/QuoteViewModel.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/MainActivity.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/WidgetSettingsScreen.kt`

**Interfaces:**
- Produces:
  - `QuoteViewModel.amoledBlack: StateFlow<Boolean>` and `fun setAmoledBlack(enabled: Boolean)`.
  - `MyApplicationTheme(themeMode: String, themeAccent: String, amoledBlack: Boolean, content)` — new `amoledBlack` param (default `false`).
  - `themeMode` values reduced to `"LIGHT" | "DARK" | "DYNAMIC"` (no `"AMOLED"`).
- Consumes: existing `themeMode`, `themeAccent` flows.

- [ ] **Step 1: ViewModel — add amoledBlack state + migration**

In `QuoteViewModel`, add:
```kotlin
private val _amoledBlack = MutableStateFlow(false)
val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

fun setAmoledBlack(enabled: Boolean) {
    _amoledBlack.value = enabled
    prefs.edit().putBoolean("amoled_black", enabled).apply()
}
```
In `loadThemeSettings()`, replace the `theme_mode` load with a migration:
```kotlin
val storedMode = prefs.getString("theme_mode", "DARK") ?: "DARK"
if (storedMode == "AMOLED") {
    _themeMode.value = "DARK"
    _amoledBlack.value = true
    prefs.edit().putString("theme_mode", "DARK").putBoolean("amoled_black", true).apply()
} else {
    _themeMode.value = storedMode
    _amoledBlack.value = prefs.getBoolean("amoled_black", false)
}
```
Also change the `_themeMode` initial value default from `"AMOLED"` to `"DARK"`.

- [ ] **Step 2: Theme.kt — accept amoledBlack and apply true black**

Add `amoledBlack: Boolean = false` param to `MyApplicationTheme`. In the DYNAMIC branch, after computing the dynamic scheme, if `amoledBlack && isSystemInDarkTheme()` override background/surface/surfaceVariant to `Color(0xFF000000)` / near-black via `.copy()` on the dynamic scheme:
```kotlin
themeMode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
    val base = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (amoledBlack && isSystemInDarkTheme())
        base.copy(background = Color(0xFF000000), surface = Color(0xFF0B0B0C), surfaceVariant = Color(0xFF1C1C1E))
    else base
}
```
In the `useDark` (non-dynamic dark) branch, replace `val isAmoled = themeMode == "AMOLED"` with `val isAmoled = amoledBlack`. Keep the rest of that branch (it already switches bg/surf/surfContainer/surfHighest on `isAmoled`).

- [ ] **Step 3: MainActivity — pass amoledBlack through**

Collect and forward:
```kotlin
val amoledBlack by viewModel.amoledBlack.collectAsState()
MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent, amoledBlack = amoledBlack) { ... }
```

- [ ] **Step 4: Settings UI — 3-mode row + AMOLED toggle**

In `WidgetSettingsScreen.kt`: change `val modes = listOf("AMOLED", "DARK", "LIGHT", "DYNAMIC")` to `listOf("LIGHT", "DARK", "DYNAMIC")`, labels: `LIGHT`→"Light", `DARK`→"Dark", `DYNAMIC`→"System". Below the mode Row, add an AMOLED toggle:
```kotlin
val amoledBlack by viewModel.amoledBlack.collectAsState()
Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Column(Modifier.weight(1f)) {
        Text("AMOLED pure black", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("True-black background in any dark theme (incl. System)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
    Switch(checked = amoledBlack, onCheckedChange = { viewModel.setAmoledBlack(it) })
}
```
Leave the accent palette section as-is (still disabled under DYNAMIC).

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. Grep confirms no remaining `"AMOLED"` mode string is compared anywhere except the migration.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/brokoli5191/quote/ui/theme/Theme.kt app/src/main/java/app/brokoli5191/quote/ui/QuoteViewModel.kt app/src/main/java/app/brokoli5191/quote/MainActivity.kt app/src/main/java/app/brokoli5191/quote/ui/screens/WidgetSettingsScreen.kt
git commit -m "E: decouple AMOLED pure-black from theme mode so it combines with Material You"
```

---

### Task 4: Extract testable CategoryMapper with expanded categories (Workstream A, part 1)

**Files:**
- Create: `app/src/main/java/app/brokoli5191/quote/data/CategoryMapper.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/data/QuoteRepository.kt`
- Create: `app/src/test/java/app/brokoli5191/quote/CategoryMapperTest.kt`

**Interfaces:**
- Produces: `object CategoryMapper { val categories: List<String>; fun map(tags: List<String>, text: String): String }`. Returns a category from `categories`, final fallback `"Uncategorized"`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test**

`CategoryMapperTest.kt`:
```kotlin
package app.brokoli5191.quote

import app.brokoli5191.quote.data.CategoryMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMapperTest {
    @Test fun exactTagWins() =
        assertEquals("Love", CategoryMapper.map(listOf("love", "romance"), "irrelevant"))

    @Test fun synonymMapsToCategory() =
        assertEquals("Optimism", CategoryMapper.map(listOf("hope"), "irrelevant"))

    @Test fun deathCluster() =
        assertEquals("Death", CategoryMapper.map(listOf("mortality"), "x"))

    @Test fun unmatchedIsUncategorizedNotInspirational() =
        assertEquals("Uncategorized", CategoryMapper.map(listOf("zzz-nonsense"), "no keyword here"))

    @Test fun textKeywordFallback() =
        assertEquals("Wisdom", CategoryMapper.map(emptyList(), "a piece of true wisdom"))

    @Test fun everyCategoryIsInList() =
        assertEquals(true, CategoryMapper.categories.contains("Uncategorized"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "app.brokoli5191.quote.CategoryMapperTest"`
Expected: FAIL (unresolved reference `CategoryMapper`).

- [ ] **Step 3: Implement CategoryMapper**

`CategoryMapper.kt`:
```kotlin
package app.brokoli5191.quote.data

object CategoryMapper {
    // Ordered; earlier categories win ties. "Uncategorized" is the honest fallback.
    val categories: List<String> = listOf(
        "Inspirational", "Life", "Love", "Wisdom", "Happiness", "Optimism",
        "Humor", "Philosophy", "Truth", "Death", "Poetry", "Writing",
        "Books", "Reading", "Knowledge", "Success", "Courage", "Friendship",
        "Nature", "Faith", "Freedom", "Uncategorized"
    )

    // Source tag (lowercased) -> category. Many tags collapse to one category.
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
        // 2. synonym map
        for (t in lowerTags) synonyms[t]?.let { return it }
        // 3. synonym substring (e.g. "self-love")
        for (t in lowerTags) for ((k, v) in synonyms) if (t.contains(k)) return v
        // 4. keyword in text
        val lowerText = text.lowercase()
        for ((k, v) in synonyms) if (lowerText.contains(k)) return v
        // 5. honest fallback
        return "Uncategorized"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "app.brokoli5191.quote.CategoryMapperTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Use CategoryMapper in the repository**

In `QuoteRepository.kt`, delete the private `mapTagsToCategory(...)` function and replace its call site in `preseedDatabase` with:
```kotlin
val category = CategoryMapper.map(tagsList, quoteText)
```
Import `app.brokoli5191.quote.data.CategoryMapper` (same package — no import needed).

- [ ] **Step 6: Build + commit**

```bash
./gradlew assembleDebug
git add app/src/main/java/app/brokoli5191/quote/data/CategoryMapper.kt app/src/main/java/app/brokoli5191/quote/data/QuoteRepository.kt app/src/test/java/app/brokoli5191/quote/CategoryMapperTest.kt
git commit -m "A1: extract testable CategoryMapper with expanded categories + Uncategorized fallback"
```

---

### Task 5: Single source of truth for category UI list (Workstream A, part 2)

**Files:**
- Create: `app/src/main/java/app/brokoli5191/quote/ui/screens/CategoryCatalog.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/LibraryScreen.kt`

**Interfaces:**
- Produces: `val categoryCatalog: List<CategoryTileData>` (name + icon + tintColor) and `val filterCategoryNames: List<String>` — both derived so the bento grid and the filter sheet share one list.
- Consumes: `CategoryMapper.categories` (name list authority), `CategoryTileData`.

- [ ] **Step 1: Create the catalog**

`CategoryCatalog.kt` defines a `categoryCatalog: List<CategoryTileData>` covering every entry in `CategoryMapper.categories` except `"Uncategorized"` plus a final `"Uncategorized"` tile (icon `Icons.Default.Category`, neutral tint). Each entry maps name→icon→tintColor (reuse the existing icons/tints; pick sensible icons for the new categories: Knowledge→`School`, Success→`EmojiEvents`, Courage→`Bolt`, Friendship→`Diversity3`, Nature→`Park`, Faith→`Spa`, Freedom→`Flight`). Move the `CategoryTileData` data class here.

- [ ] **Step 2: Consume it in LibraryScreen**

Replace the inline `bentoCategories` list with `categoryCatalog`. Render the first tile full-width, the rest in rows of 2 (keep existing chunk logic but drive off `categoryCatalog.drop(1)`). Replace the filter-sheet `categoriesList` with `filterCategoryNames` (= `categoryCatalog.map { it.name }`).

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL; Library shows the expanded category set with no subtitles.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/brokoli5191/quote/ui/screens/CategoryCatalog.kt app/src/main/java/app/brokoli5191/quote/ui/screens/LibraryScreen.kt
git commit -m "A2: single source of truth for Library category list"
```

---

### Task 6: Regenerate seed dataset + favorite-preserving re-seed (Workstream A, part 3)

**Files:**
- Create: `tools/build_quotes_seed.sh` (offline build-time script, not shipped)
- Modify: `app/src/main/res/raw/quotes_seed.json` (regenerated)
- Modify: `app/src/main/java/app/brokoli5191/quote/data/QuoteRepository.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/QuoteViewModel.kt`

**Interfaces:**
- Produces: `QuoteRepository.reseedPreservingFavorites(context)` — clears non-user rows, re-inserts seed, restores favorites matched by `(text, author)`.
- Consumes: `CategoryMapper` (via `preseedDatabase`), `quotes_seed.json`.

- [ ] **Step 1: Write the seed build script**

`tools/build_quotes_seed.sh` downloads `https://huggingface.co/datasets/Abirate/english_quotes/resolve/main/quotes.jsonl`, then (using `jq`) strips smart quotes/whitespace, drops empty text/author, dedups on lowercased `(text|author)`, removes noise tags (`misattributed-*`, `attributed-no-source`, and any tag equal to a lowercased token of the author name), and emits the existing `[{"quote","author","tags":[...]}]` array to `app/src/main/res/raw/quotes_seed.json`. Include a fallback note: if `jq` is unavailable, the same transform is done inline in the execution step.

- [ ] **Step 2: Run the script to regenerate the seed**

Run: `bash tools/build_quotes_seed.sh`
Expected: `quotes_seed.json` now contains ~2500 objects. Verify: `grep -c '"author"' app/src/main/res/raw/quotes_seed.json` is ~2400-2508 and the first object still parses.

- [ ] **Step 3: Add favorite-preserving re-seed to the repository**

In `QuoteRepository.kt`:
```kotlin
suspend fun reseedPreservingFavorites(context: Context) {
    val oldFavKeys = quoteDao.getAllQuotesSync()
        .filter { it.isFavorite && !it.isUserAdded }
        .map { it.text.trim() to it.author.trim() }
        .toSet()
    quoteDao.deleteNonUserQuotes()
    preseedDatabase(context)
    if (oldFavKeys.isNotEmpty()) {
        val now = System.currentTimeMillis().toString()
        quoteDao.getAllQuotesSync().forEach { q ->
            if （!q.isUserAdded && (q.text.trim() to q.author.trim()) in oldFavKeys）
                quoteDao.updateFavorite(q.id, true, now)
        }
    }
}
```
Add DAO query in `QuoteDao.kt`: `@Query("DELETE FROM quotes WHERE isUserAdded = 0") suspend fun deleteNonUserQuotes()`. (Note: use ASCII parentheses in real code — the guillemets above are placeholders to avoid copy artifacts.)

- [ ] **Step 4: Wire re-seed into version-bumped seeding**

In `QuoteViewModel.checkAndSeedDatabase()`, change the pref key `"database_json_seeded_v5_2"` → `"database_json_seeded_v6"`, and on a not-seeded-or-count-low condition call `repository.reseedPreservingFavorites(app)` instead of `clearAllQuotes()` + `preseedDatabase(app)`. Keep the `loadDailyQuote()` and deferred update-check calls.

- [ ] **Step 5: Build + unit tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL; CategoryMapper tests still pass.

- [ ] **Step 6: Commit**

```bash
git add tools/build_quotes_seed.sh app/src/main/res/raw/quotes_seed.json app/src/main/java/app/brokoli5191/quote/data/QuoteRepository.kt app/src/main/java/app/brokoli5191/quote/data/QuoteDao.kt app/src/main/java/app/brokoli5191/quote/ui/QuoteViewModel.kt
git commit -m "A3: regenerate ~2500-quote seed, favorite-preserving re-seed, v6 seed key"
```

---

### Task 7: True zero-animation low-performance mode (Workstream B)

**Files:**
- Modify: `app/src/main/java/app/brokoli5191/quote/MainActivity.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/DailyScreen.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/screens/LibraryScreen.kt`

**Interfaces:**
- Consumes: `QuoteViewModel.lowPerformanceMode: StateFlow<Boolean>`.
- Produces: no new symbols; behavior change only.

- [ ] **Step 1: MainActivity tab transition → instant when low-perf**

In the `AnimatedContent` `transitionSpec`, add a first branch: `if (lowPerformanceMode) ContentTransform(EnterTransition.None, ExitTransition.None)` before the existing forward/back logic. Also, when `lowPerformanceMode`, skip the predictive-back peek/translate graphicsLayer (guard the `backPreviewTab` render and the current-content `translationX` with `!lowPerformanceMode`); the back gesture still pops, just without the slide.

- [ ] **Step 2: DailyScreen quote change → no fade when low-perf**

Collect `val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()`. In the quote `AnimatedContent` transitionSpec, `if (lowPerformanceMode) ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null) else <existing fade>`.

- [ ] **Step 3: DailyScreen — static heart + no elastic pull visual when low-perf**

Guard `heartScale` animate: when low-perf, use constant `1f`. In `ElasticPullDownContainer`, when low-perf pass-through, keep the drag→refresh trigger but set `dragOffset` visual to `0f` (no rubber-band). Thread a `lowPerformanceMode` param into `ElasticPullDownContainer`.

- [ ] **Step 4: LibraryScreen — static entry + empty-state when low-perf**

`QuoteBrowseItemCard`: when low-perf, skip the offset/alpha entry animation (render at final state). The empty-state infinite animations already have low-perf guards — verify they resolve to static. The `AnimatedContent` for browse/bento already has a low-perf fade branch; change it to `ContentTransform(EnterTransition.None, ExitTransition.None)` for truly zero motion. Pass `lowPerformanceMode` into `QuoteBrowseItemCard`.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "B: low-performance mode now disables all animations"
```

---

### Task 8: Splash screen + startup polish (Workstream C, part 1)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/app/brokoli5191/quote/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml` (create if absent)

**Interfaces:**
- Produces: `installSplashScreen()` call in `onCreate` before `super.onCreate`.

- [ ] **Step 1: Add the dependency**

In `libs.versions.toml` add `androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version = "1.0.1" }`. In `app/build.gradle.kts` add `implementation(libs.androidx.core.splashscreen)`.

- [ ] **Step 2: Install splash in MainActivity**

At the top of `onCreate`, before `super.onCreate(savedInstanceState)`, call `installSplashScreen()` (import `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`). Confirm no main-thread I/O remains in `onCreate` besides the existing synchronous DB build + `loadThemeSettings` (already off-critical-path; leave as-is).

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL; app cold-starts with a themed splash, no blank window.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/app/brokoli5191/quote/MainActivity.kt app/src/main/res/values/themes.xml
git commit -m "C1: add splash screen for smoother cold start"
```

---

### Task 9: Runtime jank fixes (Workstream C, part 2)

**Files:**
- Modify: `app/src/main/java/app/brokoli5191/quote/MainActivity.kt`
- Modify: `app/src/main/java/app/brokoli5191/quote/ui/QuoteViewModel.kt`

**Interfaces:**
- Consumes: existing flows.
- Produces: `filteredQuotes` computed off the main thread.

- [ ] **Step 1: Move filtering off the main thread**

In `QuoteViewModel`, the `filteredQuotes` `combine {}` runs its filter on the collector's context. Add `.flowOn(Dispatchers.Default)` before `.stateIn(...)` so filtering ~2500 rows on search keystrokes doesn't jank the main thread.

- [ ] **Step 2: Lighten the predictive-back double composition**

In `MainActivity`, the left "peek" renders a full second `DailyScreen(viewModel)` each gesture frame. Replace the peek content with a lightweight static placeholder (a `Box` filled with `MaterialTheme.colorScheme.background` and the "Daily Quote" header only) instead of the full screen, OR gate the full render behind `backProgress > 0.01f` and `!lowPerformanceMode`. Choose the static-placeholder approach to eliminate the cost.

- [ ] **Step 3: Soften tab-switch spring**

In the normal (non-low-perf) tab `AnimatedContent` branch, reduce simultaneous animated properties: keep `fadeIn/fadeOut` + `slideInHorizontally/slideOutHorizontally` but drop the `scaleIn/scaleOut` (scale on a full-screen subtree is the most expensive). Keep the spring specs.

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "C2: off-main filtering, lighter back-peek, cheaper tab transition"
```

---

### Task 10: Baseline Profile (Workstream C, part 3 — device-gated)

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `baselineprofile/` module (if generating on device)

**Interfaces:** none (build/runtime perf only).

> This task requires a connected device/emulator to *generate* the profile. If none is available during execution, STOP after Step 1, commit the plumbing, and leave a note; the concrete Compose fixes in Tasks 7 & 9 already deliver the guaranteed jank wins.

- [ ] **Step 1: Add the baseline profile Gradle plugin plumbing**

Add `androidx.baselineprofile` plugin + a `:baselineprofile` macrobenchmark module per the AndroidX Baseline Profiles Generator template (a `BaselineProfileGenerator` test that launches the app and scrolls Daily/Library). Wire `baselineProfile { }` in `app/build.gradle.kts`.

- [ ] **Step 2: Generate (device required)**

Run: `./gradlew :app:generateBaselineProfile`
Expected: `app/src/main/baseline-prof.txt` produced. If no device: skip, note in commit.

- [ ] **Step 3: Build + commit**

```bash
./gradlew assembleDebug
git add -A
git commit -m "C3: baseline profile plumbing (profile generation device-gated)"
```

---

### Task 11: New open-book adaptive app icon (Workstream G)

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Verify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`

**Interfaces:** none.

- [ ] **Step 1: Redraw the foreground as a minimalist open book**

Replace `ic_launcher_foreground.xml` with a 108x108 vector whose content sits inside the central 66dp safe zone: a simple two-page open book glyph (single accent fill `#FFD0BCFF` on transparent), symmetric spine, clean paths — no fine detail that disappears at small sizes.

- [ ] **Step 2: Simplify the background**

Set `ic_launcher_background.xml` to a solid brand color `#FF37265E` (violet dark) or a subtle vertical gradient `#FF37265E → #FF241640`. Full-bleed 108x108 rect.

- [ ] **Step 3: Verify adaptive wiring + themed icon**

Confirm `mipmap-anydpi-v26/ic_launcher.xml` references `@drawable/ic_launcher_background` + `@drawable/ic_launcher_foreground` (and a `<monochrome>` layer pointing at the foreground for Android 13 themed icons — add if missing).

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL; launcher shows the open-book adaptive icon. Legacy density `.webp` mipmaps are left as-is (regenerated in Android Studio's Image Asset tool if pixel-perfect legacy icons are needed; the adaptive vector covers API 26+).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/mipmap-anydpi-v26/
git commit -m "G: new open-book adaptive app icon"
```

---

## Self-Review

**Spec coverage:**
- Item 1 (startup perf) → Task 8. ✔
- Item 2 (more quotes + DB + tags/categories) → Tasks 4, 5, 6. ✔
- Item 3 (low-perf = no animation) → Task 7. ✔
- Item 4 (Daily declutter/personalize) → Task 2. ✔
- Item 5a (AMOLED + Material You together) → Task 3. ✔
- Item 5b (Library subtitles) → Task 1. ✔
- Item 6 (open-book adaptive icon) → Task 11. ✔
- Item 7 (general jank on premium devices) → Tasks 9, 10. ✔

**Type consistency:** `CategoryMapper.map(tags, text)` / `CategoryMapper.categories` used consistently in Tasks 4-6. `reseedPreservingFavorites(context)` + `deleteNonUserQuotes()` defined in Task 6 and used there. `amoledBlack` StateFlow + `setAmoledBlack` + `MyApplicationTheme(..., amoledBlack)` consistent across Task 3 steps. `CategoryTileData` moves to `CategoryCatalog.kt` in Task 5 after losing `description` in Task 1 — order matters (Task 1 before Task 5). ✔

**Placeholder scan:** No TBD/TODO. The Task 6 Step 3 code intentionally flags the guillemet placeholders with an inline instruction to use ASCII parens. Baseline Profile (Task 10) is explicitly device-gated with a stop condition, not a vague deferral. ✔

**Ordering constraint:** Tasks are independent except: Task 5 depends on Task 1 (CategoryTileData shape) and Task 4 (CategoryMapper.categories); Task 6 depends on Task 4 (CategoryMapper). Execute in numeric order.
