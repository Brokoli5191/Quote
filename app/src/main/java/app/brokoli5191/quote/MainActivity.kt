package app.brokoli5191.quote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brokoli5191.quote.data.AppDatabase
import app.brokoli5191.quote.data.QuoteRepository
import app.brokoli5191.quote.ui.AuraViewModel
import app.brokoli5191.quote.ui.AuraViewModelFactory
import app.brokoli5191.quote.ui.screens.DailyScreen
import app.brokoli5191.quote.ui.screens.LibraryScreen
import app.brokoli5191.quote.ui.screens.SavedScreen
import app.brokoli5191.quote.ui.screens.WidgetSettingsScreen
import app.brokoli5191.quote.ui.theme.MyApplicationTheme

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Room Database & Repository
        val database = AppDatabase.getInstance(applicationContext)
        val repository = QuoteRepository(database.quoteDao())
        val factory = AuraViewModelFactory(repository)

        setContent {
            val viewModel: AuraViewModel by viewModels { factory }
            
            // Load persistent settings on first composition
            LaunchedEffect(Unit) {
                viewModel.loadThemeSettings(applicationContext)
                viewModel.checkAndSeedDatabase(applicationContext)
            }

            val themeMode by viewModel.themeMode.collectAsState()
            val themeAccent by viewModel.themeAccent.collectAsState()
            val lowPerformanceMode by viewModel.lowPerformanceMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode, themeAccent = themeAccent) {
                val activeTab by viewModel.selectedTab.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            activeTab = activeTab,
                            lowPerformanceMode = lowPerformanceMode,
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
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                if (initialState == targetState) {
                                    ContentTransform(
                                        targetContentEnter = EnterTransition.None,
                                        initialContentExit = ExitTransition.None
                                    )
                                } else if (lowPerformanceMode) {
                                    val currentIdx = when (initialState) {
                                        "Daily" -> 0
                                        "Library" -> 1
                                        "Saved" -> 2
                                        "Settings" -> 3
                                        else -> 0
                                    }
                                    val targetIdx = when (targetState) {
                                        "Daily" -> 0
                                        "Library" -> 1
                                        "Saved" -> 2
                                        "Settings" -> 3
                                        else -> 0
                                    }
                                    val isForward = targetIdx > currentIdx
                                    
                                    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    val fadeSpec = tween<Float>(durationMillis = 180)
                                    
                                    if (isForward) {
                                        (slideInHorizontally(animationSpec = slideSpec) { width -> width } + fadeIn(animationSpec = fadeSpec))
                                            .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> -width } + fadeOut(animationSpec = fadeSpec))
                                    } else {
                                        (slideInHorizontally(animationSpec = slideSpec) { width -> -width } + fadeIn(animationSpec = fadeSpec))
                                            .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> width } + fadeOut(animationSpec = fadeSpec))
                                    }
                                } else {
                                    val currentIdx = when (initialState) {
                                        "Daily" -> 0
                                        "Library" -> 1
                                        "Saved" -> 2
                                        "Settings" -> 3
                                        else -> 0
                                    }
                                    val targetIdx = when (targetState) {
                                        "Daily" -> 0
                                        "Library" -> 1
                                        "Saved" -> 2
                                        "Settings" -> 3
                                        else -> 0
                                    }
                                    
                                    val isForward = targetIdx > currentIdx
                                    
                                    val exprSpring = spring<Float>(dampingRatio = 0.76f, stiffness = 180f)
                                    val exprOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.76f, stiffness = 180f)
                                    
                                    if (isForward) {
                                        (slideInHorizontally(animationSpec = exprOffsetSpring) { width -> (width * 0.15f).toInt() } +
                                         fadeIn(animationSpec = exprSpring) +
                                         scaleIn(initialScale = 0.92f, animationSpec = exprSpring))
                                        .togetherWith(
                                         slideOutHorizontally(animationSpec = exprOffsetSpring) { width -> -(width * 0.15f).toInt() } +
                                         fadeOut(animationSpec = exprSpring) +
                                         scaleOut(targetScale = 0.92f, animationSpec = exprSpring))
                                    } else {
                                        (slideInHorizontally(animationSpec = exprOffsetSpring) { width -> -(width * 0.15f).toInt() } +
                                         fadeIn(animationSpec = exprSpring) +
                                         scaleIn(initialScale = 0.92f, animationSpec = exprSpring))
                                        .togetherWith(
                                         slideOutHorizontally(animationSpec = exprOffsetSpring) { width -> (width * 0.15f).toInt() } +
                                         fadeOut(animationSpec = exprSpring) +
                                         scaleOut(targetScale = 0.92f, animationSpec = exprSpring))
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
        }
    }
}

@Composable
fun BottomNavigationBar(activeTab: String, lowPerformanceMode: Boolean, onTabSelected: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
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
                Triple("Daily", Pair(Icons.Outlined.FormatQuote, Icons.Default.FormatQuote), "Daily"),
                Triple("Library", Pair(Icons.Outlined.AutoStories, Icons.Default.AutoStories), "Library"),
                Triple("Saved", Pair(Icons.Default.FavoriteBorder, Icons.Default.Favorite), "Saved"),
                Triple("Settings", Pair(Icons.Outlined.Settings, Icons.Default.Settings), "Settings")
            )

            items.forEach { (tab, iconPair, label) ->
                val isSelected = activeTab == tab
                
                // Elastic bounce scale animation on click
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = if (lowPerformanceMode) {
                        tween(durationMillis = 150)
                    } else {
                        spring(
                            dampingRatio = 0.48f,
                            stiffness = 300f
                        )
                    },
                    label = "NavIconScale"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // Cleaner, distraction-free select
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTabSelected(tab)
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val targetIcon = if (isSelected && !lowPerformanceMode) iconPair.second else iconPair.first
                        Icon(
                            imageVector = targetIcon,
                            contentDescription = label,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp)) // Move the labels slightly down as requested

                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            }
                        )
                    )
                }
            }
        }
    }
}
