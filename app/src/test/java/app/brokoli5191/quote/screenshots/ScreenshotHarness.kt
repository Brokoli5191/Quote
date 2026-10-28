package app.brokoli5191.quote.screenshots

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import app.brokoli5191.quote.BottomNavigationBar
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.DailySelectionEntity
import app.brokoli5191.quote.data.QuoteEntity
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteOrigin
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.data.QuoteSubmissionStatus
import app.brokoli5191.quote.data.dailyDateKey
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.QuoteViewModelFactory
import app.brokoli5191.quote.ui.screens.DailyScreen
import app.brokoli5191.quote.ui.screens.LibraryScreen
import app.brokoli5191.quote.ui.screens.SavedScreen
import app.brokoli5191.quote.ui.screens.WidgetSettingsScreen
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Device used for every screenshot (in addition to the per-run locale, night mode and font scale). */
const val SCREENSHOT_DEVICE = "w360dp-h780dp-xxhdpi"

/** Recorded images land here (relative to the app module) when Roborazzi runs in record mode. */
private const val OUTPUT_DIR = "build/outputs/roborazzi"

/**
 * One rendering configuration: app language, app theme and font scale. Passed to the
 * parameterized runner as a plain id string ("en_dark", "de_amoled", "de_dark_fs130").
 */
data class ShotConfig(val language: String, val theme: String, val fontScale: Float) {
    val id: String =
        "${language}_${theme.lowercase()}" + if (fontScale != 1f) "_fs${(fontScale * 100).toInt()}" else ""

    companion object {
        fun parse(id: String): ShotConfig {
            val parts = id.split('_')
            val scale = parts.getOrNull(2)?.removePrefix("fs")?.toInt()?.div(100f) ?: 1f
            return ShotConfig(parts[0], parts[1].uppercase(), scale)
        }

        /** English and German in every theme, plus one large-font German run. */
        val all: List<String> = listOf(
            "en_dark", "en_light", "en_amoled",
            "de_dark", "de_light", "de_amoled",
            "de_dark_fs130"
        )

        fun parameters(): List<Array<Any>> = all.map { arrayOf<Any>(it) }
    }
}

/**
 * Applies locale, night mode and font scale before the compose rule launches its activity, and
 * keeps the app offline: every HTTP(S) request fails fast through a dead proxy.
 */
class ScreenshotEnvironmentRule(private val config: ShotConfig) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val locale = if (config.language == QuoteLanguage.GERMAN) "de-rDE" else "en-rUS"
            val night = if (config.theme == "LIGHT") "notnight" else "night"
            RuntimeEnvironment.setQualifiers("+$locale-$night")
            if (config.fontScale != 1f) RuntimeEnvironment.setFontScale(config.fontScale)
            val previousSelector = ProxySelector.getDefault()
            ProxySelector.setDefault(OfflineProxySelector)
            try {
                base.evaluate()
            } finally {
                ProxySelector.setDefault(previousSelector)
            }
        }
    }
}

// Loopback stays reachable (other tests in the same JVM use local servers); everything else
// goes to a closed local port, so requests fail immediately instead of reaching the network.
private object OfflineProxySelector : ProxySelector() {
    private val deadProxy = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))

    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host.orEmpty()
        return if (host == "localhost" || host == "127.0.0.1" || host == "::1") listOf(Proxy.NO_PROXY) else deadProxy
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

/** A seeded app instance: in-memory Room database, prefs for the config and a real QuoteViewModel. */
class ScreenshotApp(
    val config: ShotConfig,
    val language: String = config.language,
    prefs: Map<String, Any> = emptyMap(),
    quotes: List<QuoteEntity> = FakeQuotes.all,
    dailyQuoteText: String? = null,
    showAnonymous: Boolean = false,
) {
    val context: Application = ApplicationProvider.getApplicationContext()
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val store = ViewModelStore()
    private val repository = QuoteRepository(database.quoteDao(), 42L)
    val viewModel: QuoteViewModel

    init {
        installDatabase(database)
        initWorkManager(context)
        Coil.setImageLoader(fakeImageLoader(context))

        val appPrefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().clear().apply {
            putBoolean("onboarding_completed", true)
            putBoolean("database_json_seeded_v12", true)
            putBoolean("stored_quote_text_repaired_v2", true)
            putString("theme_mode", config.theme)
            putBoolean("amoled_black", config.theme == "AMOLED")
            putString("theme_accent", "Violet")
            putString("app_language", language)
            putBoolean("show_anonymous_quotes", showAnonymous)
            putBoolean("auto_update_enabled", false)
            putBoolean("author_images_enabled", true)
            putBoolean("daily_reminder_enabled", true)
            putInt("daily_reminder_hour", 7)
            putInt("daily_reminder_minute", 30)
            prefs.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
        }.commit()

        val dao = database.quoteDao()
        runBlocking {
            dao.insertQuotes(quotes)
            if (dailyQuoteText != null) {
                val id = dao.getAllQuotesSync().first { it.text == dailyQuoteText }.id
                val key = "${dailyDateKey()}_${language}_${if (showAnonymous) 1 else 0}_${QuoteSourceMode.ALL}"
                dao.insertDailySelection(DailySelectionEntity(key, id))
            }
        }

        viewModel = ViewModelProvider(store, QuoteViewModelFactory(context, repository))[QuoteViewModel::class.java]
    }

    /** Sets the ViewModel's daily quote from the repository (same call as QuoteViewModel.loadDailyQuote). */
    fun publishDailyQuote() {
        val quote = runBlocking {
            repository.getDailyQuote(dailyDateKey(), viewModel.quoteSourceMode.value, language, viewModel.showAnonymousQuotes.value)
        }
        val field = QuoteViewModel::class.java.getDeclaredField("_dailyQuote")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<QuoteEntity?>).value = quote
    }

    fun close() {
        store.clear()
        database.close()
        installDatabase(null)
    }

    private fun installDatabase(db: AppDatabase?) {
        // The ViewModel's author lookups use AppDatabase.getInstance(); point it at the in-memory database.
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, db)
    }
}

// WorkManager is not auto-initialized under Robolectric; the ViewModel schedules the sync worker.
private val workExecutor: Executor = Executors.newSingleThreadExecutor()

private fun initWorkManager(context: Context) {
    WorkManagerImpl.setDelegate(null)
    WorkManager.initialize(
        context,
        Configuration.Builder()
            .setExecutor(workExecutor)
            .setTaskExecutor(workExecutor)
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()
    )
}

// Every image request gets the same generated portrait, from "memory cache" so Coil skips the crossfade.
private fun fakeImageLoader(context: Context): ImageLoader {
    val portrait = BitmapDrawable(context.resources, fakePortrait())
    return ImageLoader.Builder(context)
        .dispatcher(Dispatchers.Main.immediate)
        .components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                    SuccessResult(drawable = portrait, request = chain.request, dataSource = DataSource.MEMORY_CACHE)
            })
        }
        .build()
}

private fun fakePortrait(): Bitmap {
    val size = 330
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFFD9C7A7.toInt(), 0xFF8C7656.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null
    paint.color = 0xFF4A3B2A.toInt()
    canvas.drawOval(RectF(105f, 55f, 225f, 195f), paint)
    val shoulders = Path().apply {
        moveTo(40f, size.toFloat())
        cubicTo(50f, 230f, 120f, 205f, 165f, 205f)
        cubicTo(210f, 205f, 280f, 230f, 290f, size.toFloat())
        close()
    }
    canvas.drawPath(shoulders, paint)
    return bitmap
}

/** Theme and app language as MainActivity sets them up. */
@Composable
fun AppFrame(viewModel: QuoteViewModel, content: @Composable () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
    val amoledBlack by viewModel.amoledBlack.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent, amoledBlack = amoledBlack) {
        CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
            content()
        }
    }
}

/** A main tab with the app's bottom navigation bar; [overlay] is drawn above the tab (e.g. New Quote). */
@Composable
fun TabFrame(viewModel: QuoteViewModel, tab: String, overlay: (@Composable () -> Unit)? = null) {
    AppFrame(viewModel) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomNavigationBar(
                    activeTab = tab,
                    lowPerformanceMode = false,
                    blurEnabled = false,
                    hazeState = rememberHazeState(blurEnabled = false),
                    onTabSelected = {}
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    "Daily" -> DailyScreen(viewModel)
                    "Library" -> LibraryScreen(viewModel)
                    "Saved" -> SavedScreen(viewModel)
                    "Settings" -> WidgetSettingsScreen(viewModel)
                }
                overlay?.invoke()
            }
        }
    }
}

/** Height of the app's bottom bar above the system navigation inset (see MainActivity). */
val BottomBarHeight = 81.dp

/** Lets Room/Default-dispatcher work finish and the main looper drain. */
fun ComposeContentTestRule.settle(rounds: Int = 8) {
    repeat(rounds) {
        waitForIdle()
        Thread.sleep(40)
    }
    waitForIdle()
}

fun ComposeContentTestRule.awaitCondition(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    waitUntil(timeoutMillis) { condition() }
    settle()
}

/** Like [awaitCondition] but returns false instead of failing on timeout. */
fun ComposeContentTestRule.tryAwait(timeoutMillis: Long, condition: () -> Boolean): Boolean = try {
    waitUntil(timeoutMillis) { condition() }
    true
} catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
    false
}

/** Records `<screen>_<state>_<config>.png` into build/outputs/roborazzi. */
fun ComposeContentTestRule.capture(screen: String, state: String, config: ShotConfig) {
    settle()
    captureScreenRoboImage(filePath = "$OUTPUT_DIR/${screen}_${state}_${config.id}.png")
}

/** Realistic offline content: bundled, community and personal quotes in both languages. */
object FakeQuotes {
    private val now = System.currentTimeMillis()
    private const val DAY = 86_400_000L

    const val MARCUS = "The happiness of your life depends upon the quality of your thoughts."
    const val SENECA_LONG =
        "It is not that we have a short time to live, but that we waste a lot of it. Life is long enough, " +
            "and a sufficiently generous amount has been given to us for the highest achievements if it were all well invested."
    const val ANONYMOUS = "Some days the bravest thing you can do is simply begin again."
    const val GOETHE_DE = "Es ist nicht genug zu wissen, man muss auch anwenden. Es ist nicht genug zu wollen, man muss auch tun."

    private fun bundled(
        text: String,
        de: String,
        author: String,
        category: String,
        tags: String,
        favoriteDaysAgo: Int? = null,
        age: Int = 0,
    ) = QuoteEntity(
        text = text,
        textDe = de,
        author = author,
        category = category,
        tags = tags,
        origin = QuoteOrigin.BUNDLED,
        isFavorite = favoriteDaysAgo != null,
        savedDate = favoriteDaysAgo?.let { (now - it * DAY - 3_600_000L).toString() },
        timestamp = now - age * 60_000L
    )

    val bundledQuotes = listOf(
        bundled(MARCUS, "Das Glück deines Lebens hängt von der Beschaffenheit deiner Gedanken ab.",
            "Marcus Aurelius", "Philosophy", "happiness,stoicism,mind", favoriteDaysAgo = 0, age = 1),
        bundled(SENECA_LONG,
            "Es ist nicht so, dass wir wenig Zeit haben, sondern dass wir viel davon vergeuden. Das Leben ist lang genug " +
                "und reichlich bemessen, um selbst das Größte zu vollbringen, wenn es nur gut angelegt wird.",
            "Seneca", "Life", "life,time,stoicism", age = 2),
        bundled("Life is like riding a bicycle. To keep your balance you must keep moving.",
            "Das Leben ist wie Fahrradfahren. Um die Balance zu halten, musst du in Bewegung bleiben.",
            "Albert Einstein", "Life", "life,balance", favoriteDaysAgo = 3, age = 3),
        bundled("My mission in life is not merely to survive, but to thrive.",
            "Meine Mission im Leben ist es nicht, bloß zu überleben, sondern aufzublühen.",
            "Maya Angelou", "Inspirational", "life,inspiration", favoriteDaysAgo = 1, age = 4),
        bundled("To live is the rarest thing in the world. Most people exist, that is all.",
            "Zu leben ist das Seltenste auf der Welt. Die meisten Menschen existieren nur.",
            "Oscar Wilde", "Life", "life,humor", age = 5),
        bundled("The journey of a thousand miles begins with one step.",
            "Eine Reise von tausend Meilen beginnt mit einem einzigen Schritt.",
            "Lao Tzu", "Inspirational", "journey,beginning", favoriteDaysAgo = 12, age = 6),
        bundled("What you seek is seeking you.",
            "Was du suchst, sucht dich.",
            "Rumi", "Wisdom", "wisdom,love", favoriteDaysAgo = 40, age = 7),
        bundled("He who has a why to live can bear almost any how.",
            "Wer ein Warum zum Leben hat, erträgt fast jedes Wie.",
            "Friedrich Nietzsche", "Philosophy", "life,meaning", age = 8),
    )

    val anonymous = QuoteEntity(
        text = ANONYMOUS,
        textDe = "An manchen Tagen ist das Mutigste, was du tun kannst, einfach neu anzufangen.",
        author = "Unknown",
        category = "Reflections",
        origin = QuoteOrigin.BUNDLED,
        isAnonymous = true,
        timestamp = now - 9 * 60_000L
    )

    val community = listOf(
        QuoteEntity(
            text = GOETHE_DE,
            textEn = "Knowing is not enough; we must apply. Willing is not enough; we must do.",
            author = "Johann Wolfgang von Goethe",
            category = "Wisdom",
            tags = "action,wisdom",
            language = QuoteLanguage.GERMAN,
            origin = QuoteOrigin.COMMUNITY,
            serverId = "c-101",
            serverRevision = 7,
            timestamp = now - 10 * 60_000L
        ),
        QuoteEntity(
            text = "Small steps every day still move you forward.",
            textDe = "Kleine Schritte jeden Tag bringen dich trotzdem voran.",
            author = "Jonas Weber",
            category = "Inspirational",
            tags = "life,progress",
            origin = QuoteOrigin.COMMUNITY,
            serverId = "c-102",
            serverRevision = 8,
            isFavorite = true,
            savedDate = (now - 5 * DAY).toString(),
            timestamp = now - 11 * 60_000L
        ),
        // Published copy of the approved personal quote below (hidden in favorites/My Quotes views).
        QuoteEntity(
            text = "Kindness costs nothing and still changes everything.",
            author = "Tom Becker",
            category = "Love",
            origin = QuoteOrigin.COMMUNITY,
            serverId = "c-103",
            submissionId = "sub-2",
            serverRevision = 9,
            timestamp = now - 12 * 60_000L
        ),
    )

    private fun personal(
        text: String,
        author: String,
        status: String,
        minutesAgo: Long,
        language: String = QuoteLanguage.ENGLISH,
        submissionId: String? = null,
        reviewNote: String? = null,
        linkedServerId: String? = null,
        category: String = "Custom",
    ) = QuoteEntity(
        text = text,
        author = author,
        category = category,
        isUserAdded = true,
        origin = QuoteOrigin.PERSONAL,
        language = language,
        submissionStatus = status,
        submissionId = submissionId,
        submittedAt = submissionId?.let { now - minutesAgo * 60_000L },
        reviewNote = reviewNote,
        linkedServerId = linkedServerId,
        timestamp = now - minutesAgo * 60_000L
    )

    val personal = listOf(
        personal("Mut ist eine leise Stimme, die sagt: Morgen versuche ich es noch einmal.", "Lena Hoffmann",
            QuoteSubmissionStatus.PENDING, minutesAgo = 30, language = QuoteLanguage.GERMAN, submissionId = "sub-1",
            category = "Inspirational"),
        personal("Kindness costs nothing and still changes everything.", "Tom Becker",
            QuoteSubmissionStatus.APPROVED, minutesAgo = 60 * 26, submissionId = "sub-2", linkedServerId = "c-103",
            category = "Love"),
        personal("Coffee first, philosophy later.", "Sam Rivera",
            QuoteSubmissionStatus.REJECTED, minutesAgo = 60 * 50, submissionId = "sub-3",
            reviewNote = "Thanks! This reads more like a personal motto than a quote. Please add context or a source.",
            category = "Humor"),
        personal("Every sunrise is an invitation to start over.", "",
            QuoteSubmissionStatus.REMOVED, minutesAgo = 60 * 24 * 9, submissionId = "sub-4", category = "Optimism"),
        personal("Write it down, so your future self remembers why it mattered.", "Sam Rivera",
            QuoteSubmissionStatus.NOT_SUBMITTED, minutesAgo = 60 * 24 * 15, category = "Wisdom"),
    )

    val all: List<QuoteEntity> = bundledQuotes + anonymous + community + personal
}
