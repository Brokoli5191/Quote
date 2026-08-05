package app.brokoli5191.quote

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.ui.QuoteViewModel
import app.brokoli5191.quote.ui.QuoteViewModelFactory
import app.brokoli5191.quote.ui.screens.DailyScreen
import app.brokoli5191.quote.ui.screens.DeveloperScreen
import app.brokoli5191.quote.ui.screens.LibraryScreen
import app.brokoli5191.quote.ui.screens.SavedScreen
import app.brokoli5191.quote.ui.screens.WidgetSettingsScreen
import app.brokoli5191.quote.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = QuoteRepository(database.quoteDao())
        val factory = QuoteViewModelFactory(application, repository)

        // Create ViewModel before setContent so theme loads synchronously — prevents flash
        val viewModel = ViewModelProvider(this, factory)[QuoteViewModel::class.java]

        setContent {
            LaunchedEffect(Unit) {
                viewModel.checkAndSeedDatabase()
            }

            val themeMode by viewModel.themeMode.collectAsState()
            val themeAccent by viewModel.themeAccent.collectAsState()
            val amoledBlack by viewModel.amoledBlack.collectAsState()
            val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()
            val blurNavigationSurfaces by viewModel.blurNavigationSurfaces.collectAsState()
            val showDevScreen by viewModel.showDevScreen.collectAsState()

            MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent, amoledBlack = amoledBlack) {
                val activeTab by viewModel.selectedTab.collectAsState()

                val hasBackStack by viewModel.hasBackStack.collectAsState()
                val selectedCategories by viewModel.selectedCategories.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()

                val backProgressAnim = remember { Animatable(0f) }
                val backProgress = backProgressAnim.value

                var isCompletingPredictiveBack by remember { mutableStateOf(false) }
                var isCompletingDevBack by remember { mutableStateOf(false) }
                var completionPreviewTab by remember { mutableStateOf<String?>(null) }

                PredictiveBackHandler(enabled = hasBackStack) { progress ->
                    var capturedIsFilterClear = false
                    var capturedIsDevBack = false
                    var capturedPreviewTab: String? = null
                    try {
                        progress.collect { event ->
                            backProgressAnim.snapTo(event.progress)
                            capturedIsDevBack = showDevScreen
                            capturedIsFilterClear = !showDevScreen && activeTab == "Library"
                                && (selectedCategories.isNotEmpty() || searchQuery.isNotBlank())
                            capturedPreviewTab = if (!capturedIsFilterClear && !showDevScreen && activeTab != "Daily") "Daily" else null
                        }
                        if (capturedIsFilterClear) {
                            backProgressAnim.snapTo(0f)
                            viewModel.popBackStack()
                            return@PredictiveBackHandler
                        }
                        completionPreviewTab = capturedPreviewTab
                        isCompletingPredictiveBack = true
                        if (capturedIsDevBack) isCompletingDevBack = true
                        backProgressAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f))
                        viewModel.popBackStack()
                        kotlinx.coroutines.delay(50L)
                    } finally {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            backProgressAnim.snapTo(0f)
                        }
                        isCompletingPredictiveBack = false
                        isCompletingDevBack = false
                        completionPreviewTab = null
                    }
                }

                // Which tab to preview on the left during back gesture / completion
                val isFilterClearBack = !showDevScreen && !isCompletingPredictiveBack
                    && backProgress > 0f && activeTab == "Library"
                    && (selectedCategories.isNotEmpty() || searchQuery.isNotBlank())

                val backPreviewTab: String? = when {
                    isCompletingPredictiveBack -> completionPreviewTab
                    showDevScreen -> null
                    isFilterClearBack -> null
                    backProgress > 0f && activeTab != "Daily" -> "Daily"
                    else -> null
                }

                val tabOrder = listOf("Daily", "Library", "Saved", "Settings")
                var swipeDeltaX by remember { mutableFloatStateOf(0f) }

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(activeTab, showDevScreen) {
                            if (showDevScreen) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val idx = tabOrder.indexOf(activeTab)
                                    when {
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
                            BottomNavigationBar(
                                activeTab = activeTab,
                                lowPerformanceMode = lowPerformanceMode,
                                glassEffectEnabled = blurNavigationSurfaces && !lowPerformanceMode,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
                        },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            // Previous page peeks in from the left during back gesture
                            if (backPreviewTab != null && !lowPerformanceMode) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = -(size.width * (1f - backProgress) * 0.25f)
                                            scaleX = 0.93f + backProgress * 0.07f
                                            scaleY = 0.93f + backProgress * 0.07f
                                        }
                                ) {
                                    // Lightweight static peek — avoids composing the full
                                    // DailyScreen on every predictive-back gesture frame.
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                        Text(
                                            text = "Daily Quote",
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .statusBarsPadding()
                                                .padding(start = 20.dp, top = 16.dp)
                                        )
                                    }
                                }
                            }

                            // Current content slides right (not for filter-clear back)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (!showDevScreen && backProgress > 0f && !isFilterClearBack && !lowPerformanceMode) {
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
                                    modifier = Modifier.fillMaxSize(),
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
                        }
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
                                    if (backProgress > 0f) {
                                        translationX = size.width * backProgress
                                        alpha = 1f - backProgress * 0.15f
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
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
    label: String,
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
                contentDescription = label,
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
    glassEffectEnabled: Boolean,
    onTabSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.background.copy(alpha = if (glassEffectEnabled) 0.82f else 1f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp),
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        label = label,
                        lowPerformanceMode = lowPerformanceMode
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    )
                }
            }
        }
    }
}
