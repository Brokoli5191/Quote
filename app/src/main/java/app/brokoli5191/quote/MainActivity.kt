package app.brokoli5191.quote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.data.InstallationSeed
import app.brokoli5191.quote.ui.QuoteDraft
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.QuoteViewModelFactory
import app.brokoli5191.quote.ui.LocalAppLanguage
import app.brokoli5191.quote.ui.uiText
import app.brokoli5191.quote.ui.components.BottomNavigationBarHeight
import app.brokoli5191.quote.ui.components.QuoteEventsHost
import app.brokoli5191.quote.ui.screens.DailyScreen
import app.brokoli5191.quote.ui.screens.DeveloperScreen
import app.brokoli5191.quote.ui.screens.LibraryScreen
import app.brokoli5191.quote.ui.screens.NewQuoteScreen
import app.brokoli5191.quote.ui.screens.OnboardingScreen
import app.brokoli5191.quote.ui.screens.SavedScreen
import app.brokoli5191.quote.ui.screens.WidgetSettingsScreen
import app.brokoli5191.quote.ui.theme.MyApplicationTheme
import app.brokoli5191.quote.utils.AppLinks
import app.brokoli5191.quote.utils.QuoteSharing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: QuoteViewModel

    // What the widget, a notification, a shortcut or another app asked to open. It waits while
    // onboarding is shown and is kept across recreation until it has been opened.
    private var pendingAppLink by mutableStateOf<AppLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = QuoteRepository(database.quoteDao(), InstallationSeed.get(applicationContext))
        val factory = QuoteViewModelFactory(application, repository)

        // Create ViewModel before setContent so theme loads synchronously — prevents flash
        viewModel = ViewModelProvider(this, factory)[QuoteViewModel::class.java]

        // A recreated activity gets its launch intent again; that request was handled already.
        pendingAppLink = if (savedInstanceState == null) appLinkRequest(intent) else savedInstanceState.readAppLinkRequest()
        // singleTop: taps on the widget or a notification reach the running activity here.
        addOnNewIntentListener { newIntent -> appLinkRequest(newIntent)?.let { pendingAppLink = it } }

        setContent {
            LaunchedEffect(Unit) {
                viewModel.checkAndSeedDatabase()
            }

            // Once per resume: picks up a new day and (throttled) syncs community quotes and
            // submission states. Replaces the old 60-second polling loop.
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.onAppResumed()
                    awaitCancellation()
                }
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val themeAccent by viewModel.themeAccent.collectAsStateWithLifecycle()
            val amoledBlack by viewModel.amoledBlack.collectAsStateWithLifecycle()
            val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsStateWithLifecycle()
            val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
            val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()

            val appLink = pendingAppLink
            LaunchedEffect(appLink, showOnboarding) {
                if (appLink != null && !showOnboarding) {
                    pendingAppLink = null
                    openAppLink(appLink)
                }
            }

            MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent, amoledBlack = amoledBlack) {
              // System bar icons follow the app theme, not the system one (e.g. Light theme on a
              // dark system), so they stay readable in every theme.
              val appDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
              LaunchedEffect(appDark) {
                  enableEdgeToEdge(
                      statusBarStyle = SystemBarStyle.auto(
                          android.graphics.Color.TRANSPARENT,
                          android.graphics.Color.TRANSPARENT
                      ) { appDark },
                      navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { appDark }
                  )
              }
              CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                Crossfade(
                    targetState = showOnboarding,
                    animationSpec = if (lowPerformanceMode) snap() else tween(durationMillis = 360),
                    label = "OnboardingTransition"
                ) { onboarding ->
                    if (onboarding) {
                        OnboardingScreen(viewModel = viewModel)
                    } else {
                        MainContent(
                            viewModel = viewModel,
                            lowPerformanceMode = lowPerformanceMode,
                            blurNavigationSurfaces = blurNavigationSurfaces
                        )
                    }
                }
              }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingAppLink?.let(outState::putAppLinkRequest)
    }

    // openDestination keeps a New Quote form that holds a draft open, so no entry point drops it.
    // setPendingDraft opens New Quote itself.
    private fun openAppLink(link: AppLinkRequest) {
        val draft = link.draft
        if (draft != null) {
            viewModel.setPendingDraft(draft.text, draft.author)
        } else {
            viewModel.openDestination(link.destination)
        }
    }
}

/** A destination from [AppLinks], plus the draft for text that another app shared or selected. */
internal data class AppLinkRequest(val destination: String, val draft: QuoteDraft? = null)

private val AppLinkDestinations = setOf(
    AppLinks.DEST_DAILY,
    AppLinks.DEST_FAVORITES,
    AppLinks.DEST_MY_QUOTES,
    AppLinks.DEST_NEW_QUOTE
)

/**
 * What [intent] asks MainActivity to open, or null for a plain launch. Shared text (ACTION_SEND,
 * ACTION_PROCESS_TEXT) opens New Quote with a draft; otherwise AppLinks.EXTRA_DEST names the screen.
 * A task reopened from Recents gets its old intent again, which is not a new request.
 */
internal fun appLinkRequest(intent: Intent?): AppLinkRequest? {
    if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
    // The activity is exported: extras from other apps may be malformed and must not crash the start.
    return try {
        val sharedText = when (intent.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }
        if (sharedText != null) {
            QuoteSharing.draftFromSharedText(sharedText.toString())?.let { AppLinkRequest(AppLinks.DEST_NEW_QUOTE, it) }
        } else {
            intent.getStringExtra(AppLinks.EXTRA_DEST)?.takeIf { it in AppLinkDestinations }?.let { AppLinkRequest(it) }
        }
    } catch (e: RuntimeException) {
        Log.w("MainActivity", "Ignoring an intent with unreadable extras", e)
        null
    }
}

private const val STATE_LINK_DESTINATION = "pending_app_link_destination"
private const val STATE_LINK_TEXT = "pending_app_link_text"
private const val STATE_LINK_AUTHOR = "pending_app_link_author"

private fun Bundle.putAppLinkRequest(link: AppLinkRequest) {
    putString(STATE_LINK_DESTINATION, link.destination)
    link.draft?.let {
        putString(STATE_LINK_TEXT, it.text)
        putString(STATE_LINK_AUTHOR, it.author)
    }
}

private fun Bundle.readAppLinkRequest(): AppLinkRequest? {
    val destination = getString(STATE_LINK_DESTINATION) ?: return null
    val draft = getString(STATE_LINK_TEXT)?.let { QuoteDraft(it, getString(STATE_LINK_AUTHOR).orEmpty()) }
    return AppLinkRequest(destination, draft)
}

// Same scrims enableEdgeToEdge() uses by default (only visible on API < 29 with button navigation).
private val LightNavigationScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * Tabs, overlays and predictive back. Search text, filters and gesture progress are read lazily
 * (at gesture time, in graphicsLayer blocks or through derivedStateOf) so typing and back-gesture
 * frames don't recompose this whole tree.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MainContent(
    viewModel: QuoteViewModel,
    lowPerformanceMode: Boolean,
    blurNavigationSurfaces: Boolean
) {
    val showDevScreen by viewModel.showDevScreen.collectAsStateWithLifecycle()
    val showNewQuoteScreen by viewModel.showNewQuoteScreen.collectAsStateWithLifecycle()
    val activeTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val blurActive = blurNavigationSurfaces && !lowPerformanceMode
    val navigationHazeState = rememberHazeState(blurEnabled = blurActive)

    val hasBackStack by viewModel.hasBackStack.collectAsStateWithLifecycle()

    val backProgressAnim = remember { Animatable(0f) }
    val newQuoteProgressAnim = remember { Animatable(1f) }
    // Only flips when a back gesture starts or ends, not on every progress frame.
    val backGestureActive by remember { derivedStateOf { backProgressAnim.value > 0f } }
    val animationScope = rememberCoroutineScope()

    var isCompletingPredictiveBack by remember { mutableStateOf(false) }
    var isCompletingDevBack by remember { mutableStateOf(false) }
    var completionPreviewTab by remember { mutableStateOf<String?>(null) }
    var newQuoteBackInProgress by remember { mutableStateOf(false) }
    // Whether the running back gesture only clears the Library search/filter.
    var backGestureIsFilterClear by remember { mutableStateOf(false) }

    // Tab the user tapped while New Quote held a draft; set while the discard dialog asks.
    var discardDraftForTab by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(showNewQuoteScreen) {
        if (!showNewQuoteScreen) {
            discardDraftForTab = null
            // Closed without its exit animation (an entry point opened another screen), so the
            // next open slides in again.
            if (!newQuoteBackInProgress) newQuoteProgressAnim.snapTo(1f)
        } else if (!newQuoteBackInProgress) {
            newQuoteProgressAnim.animateTo(
                0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    PredictiveBackHandler(enabled = showNewQuoteScreen) { progress ->
        newQuoteBackInProgress = true
        var completed = false
        try {
            newQuoteProgressAnim.stop()
            progress.collect { event ->
                newQuoteProgressAnim.snapTo(event.progress)
            }
            newQuoteProgressAnim.animateTo(
                1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
            )
            completed = true
            viewModel.closeNewQuoteScreen()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (completed) {
                    kotlinx.coroutines.delay(32L)
                } else {
                    newQuoteProgressAnim.animateTo(
                        0f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                    )
                }
                newQuoteBackInProgress = false
            }
        }
    }

    PredictiveBackHandler(
        enabled = hasBackStack && !showNewQuoteScreen && !newQuoteBackInProgress
    ) { progress ->
        // Read when the back starts instead of collecting search/filter state in composition. A
        // plain back (three-button navigation, a key press, Android 13 and older) starts this with
        // a flow that ends without any event, so none of it may wait for one.
        val devScreen = viewModel.showDevScreen.value
        val newQuoteScreen = viewModel.showNewQuoteScreen.value
        val tab = viewModel.selectedTab.value
        val isFilterClear = !devScreen && !newQuoteScreen && tab == "Library"
            && (viewModel.selectedCategories.value.isNotEmpty() || viewModel.searchQuery.value.isNotBlank())
        val previewTab = if (!isFilterClear && !devScreen && !newQuoteScreen && tab != "Daily") "Daily" else null
        backGestureIsFilterClear = isFilterClear
        var gestureSeen = false
        try {
            progress.collect { event ->
                gestureSeen = true
                backProgressAnim.snapTo(event.progress)
            }
            if (isFilterClear || (!gestureSeen && !devScreen)) {
                // No peek to finish: pop at once, so a tab change runs its normal transition.
                backProgressAnim.snapTo(0f)
                viewModel.popBackStack()
                return@PredictiveBackHandler
            }
            // The Developer screen has no exit transition of its own; a plain back slides it out here.
            completionPreviewTab = previewTab
            isCompletingPredictiveBack = true
            if (devScreen) isCompletingDevBack = true
            backProgressAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f))
            viewModel.popBackStack()
            kotlinx.coroutines.delay(50L)
        } finally {
            isCompletingDevBack = false
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                kotlinx.coroutines.delay(32L)
                backProgressAnim.snapTo(0f)
                // Cleared in the same frame as the progress reset: the peek only leaves once the
                // real tab has slid back in, so no empty frame shows in between.
                isCompletingPredictiveBack = false
                completionPreviewTab = null
                backGestureIsFilterClear = false
            }
        }
    }

    // Which tab to preview on the left during back gesture / completion
    val isFilterClearBack = !showDevScreen && !showNewQuoteScreen && !isCompletingPredictiveBack
        && backGestureActive && backGestureIsFilterClear

    val backPreviewTab: String? = when {
        isCompletingPredictiveBack -> completionPreviewTab
        showDevScreen || showNewQuoteScreen -> null
        isFilterClearBack -> null
        backGestureActive && activeTab != "Daily" -> "Daily"
        else -> null
    }

    val tabOrder = listOf("Daily", "Library", "Saved", "Settings")
    var swipeDeltaX by remember { mutableFloatStateOf(0f) }

    fun closeNewQuoteScreen(afterClose: () -> Unit = {}) {
        if (newQuoteBackInProgress) return
        animationScope.launch {
            newQuoteBackInProgress = true
            newQuoteProgressAnim.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            )
            viewModel.closeNewQuoteScreen()
            afterClose()
            newQuoteBackInProgress = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(activeTab, showDevScreen, showNewQuoteScreen) {
                if (showDevScreen || showNewQuoteScreen) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val idx = tabOrder.indexOf(activeTab)
                        val savedSubTab = viewModel.savedSubTab.value
                        when {
                            swipeDeltaX < -80f && activeTab == "Saved" && savedSubTab == "Favorites" ->
                                viewModel.selectSavedSubTab("My Quotes")
                            swipeDeltaX > 80f && activeTab == "Saved" && savedSubTab == "My Quotes" ->
                                viewModel.selectSavedSubTab("Favorites")
                            swipeDeltaX < -80f && idx < tabOrder.lastIndex ->
                                viewModel.selectTab(tabOrder[idx + 1])
                            swipeDeltaX > 80f && idx > 0 ->
                                viewModel.selectTab(tabOrder[idx - 1])
                        }
                        swipeDeltaX = 0f
                    },
                    onDragCancel = { swipeDeltaX = 0f },
                    onHorizontalDrag = { _, delta -> swipeDeltaX += delta }
                )
            }
    ) {
        // Nav bar stays fixed — only the content area animates
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Read here so keyboard visibility changes only recompose the bar slot.
                if (!showDevScreen && !WindowInsets.isImeVisible) {
                    BottomNavigationBar(
                        activeTab = activeTab,
                        lowPerformanceMode = lowPerformanceMode,
                        blurEnabled = blurActive,
                        hazeState = navigationHazeState,
                        onTabSelected = { tab ->
                            when {
                                !showNewQuoteScreen -> viewModel.selectTab(tab)
                                // Same question as Back: leaving New Quote must not drop a draft silently.
                                viewModel.newQuoteHasDraft.value -> discardDraftForTab = tab
                                else -> closeNewQuoteScreen { viewModel.selectTab(tab) }
                            }
                        }
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(navigationHazeState, zIndex = 0f)
            ) {
                // Previous page peeks in from the left during back gesture
                if (backPreviewTab != null && !lowPerformanceMode) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val backProgress = backProgressAnim.value
                                translationX = -(size.width * (1f - backProgress) * 0.25f)
                                scaleX = 0.93f + backProgress * 0.07f
                                scaleY = 0.93f + backProgress * 0.07f
                            }
                    ) {
                        // Composed once when the gesture starts; progress is only read in
                        // graphicsLayer above, so gesture frames don't recompose it.
                        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                            DailyScreen(viewModel)
                        }
                    }
                }

                // Current content slides right (not for filter-clear back)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val backProgress = backProgressAnim.value
                            // A completing back without a peek (Developer screen) keeps the tab in place.
                            val completingWithoutPeek = isCompletingPredictiveBack && completionPreviewTab == null
                            if (!showDevScreen && !showNewQuoteScreen && backProgress > 0f && !isFilterClearBack && !lowPerformanceMode && !completingWithoutPeek) {
                                translationX = size.width * backProgress
                            }
                        }
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            if (isCompletingPredictiveBack || initialState == targetState) {
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None
                                )
                            } else {
                                val isForward = tabIndex(targetState) > tabIndex(initialState)
                                if (lowPerformanceMode) {
                                    // Low-performance mode: no motion at all.
                                    ContentTransform(
                                        targetContentEnter = EnterTransition.None,
                                        initialContentExit = ExitTransition.None
                                    )
                                } else {
                                    val floatSpring = spring<Float>(dampingRatio = 0.76f, stiffness = 180f)
                                    val offsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.76f, stiffness = 180f)
                                    // Scale dropped: slide + fade only — scaling a
                                    // full-screen subtree was the costliest property.
                                    if (isForward) {
                                        (slideInHorizontally(offsetSpring) { (it * 0.15f).toInt() } +
                                         fadeIn(floatSpring))
                                        .togetherWith(
                                         slideOutHorizontally(offsetSpring) { -(it * 0.15f).toInt() } +
                                         fadeOut(floatSpring))
                                    } else {
                                        (slideInHorizontally(offsetSpring) { -(it * 0.15f).toInt() } +
                                         fadeIn(floatSpring))
                                        .togetherWith(
                                         slideOutHorizontally(offsetSpring) { (it * 0.15f).toInt() } +
                                         fadeOut(floatSpring))
                                    }
                                }
                            }
                        },
                        // The Scaffold passes no insets; the tabs handle top and bottom themselves,
                        // and this keeps their content clear of side navigation bars and cutouts.
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        contentAlignment = Alignment.Center,
                        label = "MainTabsTransition"
                    ) { tab ->
                        when (tab) {
                            "Daily" -> DailyScreen(viewModel)
                            "Library" -> LibraryScreen(viewModel)
                            "Saved" -> SavedScreen(viewModel)
                            "Settings" -> WidgetSettingsScreen(viewModel)
                        }
                    }
                }

                if (showNewQuoteScreen || newQuoteBackInProgress) {
                    NewQuoteScreen(
                        viewModel = viewModel,
                        onBack = { closeNewQuoteScreen() },
                        // Keeps the form above the bottom bar (and the system navigation
                        // under it); while typing the bar hides and the keyboard takes over.
                        bottomInsets = WindowInsets.ime.union(
                            WindowInsets.navigationBars.add(WindowInsets(bottom = BottomNavigationBarHeight))
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val newQuoteProgress = newQuoteProgressAnim.value
                                translationX = size.width * newQuoteProgress
                                alpha = 1f - newQuoteProgress * 0.15f
                            }
                    )
                }

                QuoteEventsHost(viewModel)
            }
        }

        val tabAfterDiscard = discardDraftForTab
        if (tabAfterDiscard != null && showNewQuoteScreen) {
            DiscardDraftDialog(
                onKeepEditing = { discardDraftForTab = null },
                onDiscard = {
                    discardDraftForTab = null
                    closeNewQuoteScreen { viewModel.selectTab(tabAfterDiscard) }
                }
            )
        }

        // Developer screen — exit is instant so predictive back graphicsLayer is authoritative
        AnimatedVisibility(
            visible = showDevScreen || isCompletingDevBack,
            enter = slideInVertically { it } + fadeIn(),
            exit = ExitTransition.None
        ) {
            DeveloperScreen(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val backProgress = backProgressAnim.value
                        if (backProgress > 0f) {
                            translationX = size.width * backProgress
                            alpha = 1f - backProgress * 0.15f
                        }
                    }
            )
        }

    }
}

// Same dialog as New Quote's own Back handling.
@Composable
private fun DiscardDraftDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = { Text(uiText("Discard draft?")) },
        text = { Text(uiText("Your unsaved quote will be lost.")) },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(uiText("Keep editing"))
            }
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(uiText("Discard"), color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

private fun tabIndex(tab: String) = when (tab) {
    "Daily" -> 0
    "Library" -> 1
    "Saved" -> 2
    "Settings" -> 3
    else -> 0
}

/**
 * Nav-bar icon: shows the outline glyph when inactive; when it becomes active the
 * filled glyph is revealed with a diagonal wipe from top-left to bottom-right over
 * the identically-sized, perfectly-overlaid outline. The pill fades in with it.
 */
@Composable
private fun NavBarIcon(
    outline: ImageVector,
    filled: ImageVector,
    selected: Boolean,
    lowPerformanceMode: Boolean
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (lowPerformanceMode) snap() else tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "navFill"
    )
    val activeTint = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = progress)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(24.dp)) {
            // Base outline, always the same size and position as the filled layer.
            Icon(
                imageVector = outline,
                contentDescription = null,
                tint = lerp(inactiveTint, activeTint, progress),
                modifier = Modifier.size(24.dp)
            )
            // Filled layer, revealed by a diagonal wipe (x + y <= progress * (w + h)).
            Icon(
                imageVector = filled,
                contentDescription = null,
                tint = activeTint,
                modifier = Modifier
                    .size(24.dp)
                    .drawWithContent {
                        if (progress <= 0f) return@drawWithContent
                        val extent = (size.width + size.height) * progress
                        clipPath(
                            Path().apply {
                                moveTo(0f, 0f)
                                lineTo(extent, 0f)
                                lineTo(0f, extent)
                                close()
                            }
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    activeTab: String,
    lowPerformanceMode: Boolean,
    blurEnabled: Boolean,
    hazeState: HazeState,
    onTabSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // A surface of its own with a clear top edge: content scrolling under the bar (the "Another quote"
    // button, list cards) must not look like part of it. With blur on, a little of it still shows.
    val barColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (blurEnabled) 0.94f else 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hazeEffect(hazeState)
                .background(barColor)
                .navigationBarsPadding()
                .height(BottomNavigationBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val items = listOf(
                Triple("Daily", Pair(Icons.Outlined.WbSunny, Icons.Filled.WbSunny), "Daily"),
                Triple("Library", Pair(Icons.Outlined.Spa, Icons.Filled.Spa), "Library"),
                Triple("Saved", Pair(Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite), "Saved"),
                Triple("Settings", Pair(Icons.Outlined.Settings, Icons.Filled.Settings), "Settings")
            )

            items.forEach { (tab, iconPair, label) ->
                val isSelected = activeTab == tab

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab)
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NavBarIcon(
                        outline = iconPair.first,
                        filled = iconPair.second,
                        selected = isSelected,
                        lowPerformanceMode = lowPerformanceMode
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // One line only: long German labels at large font scales shrink instead of wrapping.
                    Text(
                        text = uiText(label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 8.sp,
                            maxFontSize = 11.sp,
                            stepSize = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}
