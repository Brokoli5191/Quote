package app.brokoli5191.quote.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.quote.R
import app.brokoli5191.quote.data.QuoteLanguage
import app.brokoli5191.quote.data.QuoteSourceMode
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.components.AuthorAvatar
import app.brokoli5191.quote.ui.components.ExpressiveButton
import app.brokoli5191.quote.ui.components.ExpressiveTextButton
import app.brokoli5191.quote.ui.components.FloatingChromeBackdrop
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import app.brokoli5191.quote.ui.components.QuoteTimePickerDialog
import app.brokoli5191.quote.ui.theme.SerifFontFamily
import app.brokoli5191.quote.ui.uiText
import kotlinx.coroutines.launch
import java.util.Calendar

// Space the floating top and bottom chrome covers; pages pad their scrolling content by it.
private val LocalOnboardingChromePadding = compositionLocalOf { PaddingValues(0.dp) }
private val TopChromeFallbackHeight = 64.dp
private val BottomChromeFallbackHeight = 112.dp
private val ChromeFadeGap = 16.dp

private enum class OnboardingPage { WELCOME, LANGUAGE, APPEARANCE, SOURCES, REMINDER, AUTHORS, WIDGET, DONE }

// Author used for the preview cards; has a bundled portrait and a translated sample quote.
private const val PREVIEW_AUTHOR = "Marcus Aurelius"
private const val PREVIEW_QUOTE = "The happiness of your life depends upon the quality of your thoughts."

/**
 * First-run setup shown before the main UI. Language, appearance, sources and author images apply
 * live; the daily reminder is scheduled when onboarding finishes (or is skipped).
 */
@Composable
fun OnboardingScreen(viewModel: QuoteViewModel, modifier: Modifier = Modifier) {
    val pages = OnboardingPage.entries
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()

    // Reminder choices are kept here and only scheduled on finish.
    var reminderEnabled by rememberSaveable { mutableStateOf(viewModel.dailyReminderEnabled.value) }
    var reminderHour by rememberSaveable { mutableIntStateOf(viewModel.dailyReminderHour.value) }
    var reminderMinute by rememberSaveable { mutableIntStateOf(viewModel.dailyReminderMinute.value) }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        reminderEnabled = granted
        notificationsDenied = !granted
    }

    fun goToPage(page: Int) {
        val target = page.coerceIn(0, pages.lastIndex)
        scope.launch {
            if (lowPerformanceMode) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
        }
    }

    fun finish() {
        viewModel.updateDailyReminderTime(reminderHour, reminderMinute)
        if (reminderEnabled != viewModel.dailyReminderEnabled.value) {
            viewModel.setDailyReminderEnabled(reminderEnabled)
        }
        viewModel.completeOnboarding()
    }

    fun onReminderToggle(enabled: Boolean) {
        if (!enabled) {
            reminderEnabled = false
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            reminderEnabled = true
            notificationsDenied = false
        }
    }

    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pages.lastIndex

    // The Skip row and the page indicator + buttons float over the pages, which scroll under them
    // and fade out instead of being cut off. Both are measured so page content starts and centres
    // between them for every language and font scale.
    val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
    val onboardingHazeState = rememberHazeState(blurEnabled = blurNavigationSurfaces && !lowPerformanceMode)
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val safeBottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    var topChromePx by remember { mutableIntStateOf(0) }
    var bottomChromePx by remember { mutableIntStateOf(0) }
    val topChrome = if (topChromePx > 0) with(density) { topChromePx.toDp() } else safeTop + TopChromeFallbackHeight
    val bottomChrome = if (bottomChromePx > 0) with(density) { bottomChromePx.toDp() } else safeBottom + BottomChromeFallbackHeight

    // The back gesture steps through the pages; on the first page it leaves the app as usual.
    BackHandler(enabled = currentPage > 0) { goToPage(currentPage - 1) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            CompositionLocalProvider(
                LocalOnboardingChromePadding provides PaddingValues(top = topChrome, bottom = bottomChrome)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(onboardingHazeState),
                    verticalAlignment = Alignment.Top,
                    key = { pages[it].name }
                ) { index ->
                    when (pages[index]) {
                        OnboardingPage.WELCOME -> WelcomePage()
                        OnboardingPage.LANGUAGE -> LanguagePage(viewModel)
                        OnboardingPage.APPEARANCE -> AppearancePage(viewModel)
                        OnboardingPage.SOURCES -> SourcesPage(viewModel)
                        OnboardingPage.REMINDER -> ReminderPage(
                            enabled = reminderEnabled,
                            hour = reminderHour,
                            minute = reminderMinute,
                            notificationsDenied = notificationsDenied,
                            onToggle = { onReminderToggle(it) },
                            onPickTime = { showTimePicker = true }
                        )
                        OnboardingPage.AUTHORS -> AuthorsPage(viewModel)
                        OnboardingPage.WIDGET -> WidgetPage()
                        OnboardingPage.DONE -> DonePage()
                    }
                }
            }

            FloatingChromeBackdrop(
                hazeState = onboardingHazeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topChrome + ChromeFadeGap)
            )
            FloatingChromeBackdrop(
                hazeState = onboardingHazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomChrome + ChromeFadeGap),
                fadeDown = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { topChromePx = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastPage) {
                    ExpressiveTextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            finish()
                        }
                    ) {
                        Text(uiText("Skip"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomChromePx = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            ) {
                PageIndicator(
                    pageCount = pages.size,
                    currentPage = currentPage,
                    lowPerformanceMode = lowPerformanceMode,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (currentPage > 0) {
                            ExpressiveTextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    goToPage(currentPage - 1)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(uiText("Back"), fontWeight = FontWeight.SemiBold, maxLines = 2)
                            }
                        }
                    }
                    ExpressiveButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isLastPage) finish() else goToPage(currentPage + 1)
                        },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(min = 132.dp)
                            .heightIn(min = 52.dp),
                        restingCorner = 18.dp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = uiText(if (isLastPage) "Get started" else "Next"),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        QuoteTimePickerDialog(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            onConfirm = { hour, minute ->
                reminderHour = hour
                reminderMinute = minute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    lowPerformanceMode: Boolean,
    modifier: Modifier = Modifier
) {
    val description = uiText("Step %1\$d of %2\$d").format(currentPage + 1, pageCount)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = if (lowPerformanceMode) snap() else tween(durationMillis = 260),
                label = "OnboardingDotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = if (lowPerformanceMode) snap() else tween(durationMillis = 260),
                label = "OnboardingDotColor"
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Scrollable page body, vertically centred when it fits. [compact] tightens the gaps around the
 * header for pages with a lot of content.
 */
@Composable
private fun OnboardingPageLayout(
    icon: ImageVector?,
    title: String,
    body: String,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val chrome = LocalOnboardingChromePadding.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(chrome)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 44.dp else 56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun WelcomePage() {
    val chrome = LocalOnboardingChromePadding.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(chrome)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon()
            Spacer(Modifier.height(32.dp))
            Text(
                text = uiText("Welcome to Quote"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiText("A quote for every day, a library full of wisdom and room for your own thoughts."),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiText("Ready in a minute."),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The launcher icon, drawn from its adaptive layers and cropped to the visible safe zone. */
@Composable
private fun AppIcon() {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Adaptive icon layers are 108dp with a 72dp visible zone, hence the 1.5x size.
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.requiredSize(156.dp)
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(156.dp)
        )
    }
}

@Composable
private fun LanguagePage(viewModel: QuoteViewModel) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    OnboardingPageLayout(
        icon = Icons.Outlined.Translate,
        title = uiText("Choose your language"),
        body = uiText("Applies right away. Change it later in Settings.")
    ) {
        // Each language is named in itself so it can be found from either one.
        listOf(
            QuoteLanguage.ENGLISH to "English",
            QuoteLanguage.GERMAN to "Deutsch"
        ).forEach { (language, name) ->
            OptionRow(
                selected = appLanguage == language,
                title = name,
                description = null,
                badge = language.uppercase(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setAppLanguage(language)
                }
            )
        }
    }
}

@Composable
private fun AppearancePage(viewModel: QuoteViewModel) {
    val haptic = LocalHapticFeedback.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val themeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
    val isDynamic = themeMode == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    OnboardingPageLayout(
        icon = Icons.Outlined.Palette,
        title = uiText("Make it yours"),
        body = uiText("Pick a theme and an accent color."),
        compact = true
    ) {
        // Tighter spacing than the other pages so the whole page fits without scrolling, also in
        // German at a large font scale.
        Column {
            ThemePreviewCard()

            Spacer(Modifier.height(12.dp))
            Text(
                text = uiText("App Theme"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LIGHT", "DARK", "AMOLED", "DYNAMIC").chunked(2).forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowModes.forEach { mode ->
                            val label = when (mode) {
                                "DYNAMIC" -> "System"
                                "AMOLED" -> "AMOLED"
                                else -> mode.lowercase().replaceFirstChar { it.uppercase() }
                            }
                            ChoiceButton(
                                selected = themeMode == mode,
                                label = uiText(label),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setThemeMode(mode)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = uiText("Accent Palette"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            // System mode takes its accent from the wallpaper, so the hint replaces the swatches
            // (which would all be disabled) instead of adding another line to the page.
            if (isDynamic) {
                Text(
                    text = uiText("Accent colors come from your system in System mode."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Violet", "Amber", "Green", "Blue", "Rose").forEach { accent ->
                        val selected = themeAccent == accent
                        val label = uiText(accent)
                        val swatch = accentSwatch(accent)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) swatch else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .semantics { contentDescription = label }
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setThemeAccent(accent)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (selected) 20.dp else 14.dp)
                                    .background(swatch, CircleShape)
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun accentSwatch(accent: String): Color = when (accent) {
    "Violet" -> Color(0xFFD0BCFF)
    "Amber" -> Color(0xFFFFDB9C)
    "Green" -> Color(0xFFA0D2AD)
    "Blue" -> Color(0xFFADC6FF)
    else -> Color(0xFFFFB2C5) // Rose
}

/** Small, compact version of the daily quote card; follows the live theme. */
@Composable
private fun ThemePreviewCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        )
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = uiText("Preview").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "“${uiText(PREVIEW_QUOTE)}”",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = SerifFontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(name = PREVIEW_AUTHOR, imageUrl = null, unknown = false, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = PREVIEW_AUTHOR,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesPage(viewModel: QuoteViewModel) {
    val haptic = LocalHapticFeedback.current
    val sourceMode by viewModel.quoteSourceMode.collectAsStateWithLifecycle()
    val showAnonymous by viewModel.showAnonymousQuotes.collectAsStateWithLifecycle()

    OnboardingPageLayout(
        icon = Icons.Outlined.AutoStories,
        title = uiText("Choose your sources"),
        body = uiText("Pick what shows up daily and in the library."),
        compact = true
    ) {
        listOf(
            Triple(QuoteSourceMode.ALL, "All", "Curated and community quotes."),
            Triple(QuoteSourceMode.CURATED, "Curated", "Hand-picked from well-known authors."),
            Triple(QuoteSourceMode.COMMUNITY, "Community", "Shared by users, reviewed first.")
        ).forEach { (mode, title, description) ->
            OptionRow(
                selected = sourceMode == mode,
                title = uiText(title),
                description = uiText(description),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setQuoteSourceMode(mode)
                }
            )
        }
        SwitchRow(
            title = uiText("Anonymous reflections"),
            description = uiText("Poetic quotes without an author."),
            checked = showAnonymous,
            onCheckedChange = viewModel::setShowAnonymousQuotes
        )
    }
}

@Composable
private fun ReminderPage(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    notificationsDenied: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit
) {
    val context = LocalContext.current
    val timeText = remember(hour, minute, context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
    }

    OnboardingPageLayout(
        icon = Icons.Outlined.NotificationsActive,
        title = uiText("A daily nudge"),
        body = uiText("One quote a day as a notification.")
    ) {
        SwitchRow(
            title = uiText("Daily quote reminder"),
            description = null,
            checked = enabled,
            onCheckedChange = onToggle
        )
        OptionSurface(selected = false, onClick = onPickTime, role = Role.Button) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = uiText("Reminder time"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiText("Tap to change"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (notificationsDenied) {
            Text(
                text = uiText("Notifications are blocked. Allow them in the system settings."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AuthorsPage(viewModel: QuoteViewModel) {
    val imagesEnabled by viewModel.authorImagesEnabled.collectAsStateWithLifecycle()
    val authorInfoFlow = remember(viewModel) { viewModel.authorInfo(PREVIEW_AUTHOR) }
    val authorInfo by authorInfoFlow.collectAsStateWithLifecycle()

    OnboardingPageLayout(
        icon = Icons.Outlined.AccountCircle,
        title = uiText("Author portraits"),
        body = uiText("Portraits and short bios from Wikipedia.")
    ) {
        OptionSurface(selected = false) {
            AuthorAvatar(
                name = PREVIEW_AUTHOR,
                imageUrl = authorInfo?.imageUrl,
                unknown = false,
                size = 52.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = PREVIEW_AUTHOR,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                authorInfo?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SwitchRow(
            title = uiText("Show author images"),
            description = uiText("Loaded from Wikimedia. Turn off to skip these requests."),
            checked = imagesEnabled,
            onCheckedChange = viewModel::setAuthorImagesEnabled
        )
    }
}

@Composable
private fun WidgetPage() {
    OnboardingPageLayout(
        icon = Icons.Outlined.Widgets,
        title = uiText("Home screen widget"),
        body = uiText("Today’s quote at a glance.")
    ) {
        listOf(
            "Long-press an empty spot on your home screen.",
            "Tap Widgets.",
            "Pick Quote and place it."
        ).forEachIndexed { index, step ->
            OptionSurface(selected = false) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = uiText(step),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DonePage() {
    OnboardingPageLayout(
        icon = Icons.Outlined.TaskAlt,
        title = uiText("You’re all set"),
        body = uiText("You can change everything later in Settings.")
    )
}

/** Rounded, tinted container shared by the option, switch and info rows. */
@Composable
private fun OptionSurface(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    role: Role = Role.RadioButton,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = when {
        onClick == null -> Modifier
        role == Role.RadioButton -> Modifier.selectable(selected = selected, role = role, onClick = onClick)
        else -> Modifier.selectable(selected = false, role = role, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                // The dark themes' primaryContainer is a light pastel that turns into a muddy
                // mid-tone at this alpha, so they get a translucent primary tint instead.
                if (selected) {
                    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    }
                } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                ),
                shape
            )
            .then(interaction)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    description: String?,
    onClick: () -> Unit,
    badge: String? = null
) {
    OptionSurface(selected = selected, onClick = onClick) {
        if (badge != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .toggleable(value = checked, role = Role.Switch) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            }
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Theme mode button, same look as in Settings; the label may wrap to two lines. */
@Composable
private fun ChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        restingCorner = 12.dp,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
